import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small in-memory REST service for managing todos.
 *
 * Usage:
 *   javac TodoServer.java
 *   java TodoServer [port]
 */
public class TodoServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final Map<Integer, Todo> TODOS = new ConcurrentSkipListMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(4);

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        seedTodos();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/todos", TodoServer::handleTodos);
        server.setExecutor(Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors())));
        server.start();
        System.out.println("Todo REST service listening at http://localhost:" + port);
    }

    private static int parsePort(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Expected zero or one port number.");
        }
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Port must be a number.");
        }
    }

    private static void seedTodos() {
        TODOS.put(1, new Todo(1, "Buy groceries", false));
        TODOS.put(2, new Todo(2, "Finish homework", false));
        TODOS.put(3, new Todo(3, "Review the REST API", true));
    }

    private static void handleTodos(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (!path.equals("/todos") && !path.matches("/todos/[0-9]+")) {
            sendError(exchange, 404, "Todo not found.");
            return;
        }

        if (path.equals("/todos")) {
            if ("GET".equals(method)) {
                sendJson(exchange, 200, todosJson());
            } else if ("POST".equals(method)) {
                createTodo(exchange);
            } else {
                methodNotAllowed(exchange, "GET, POST");
            }
            return;
        }

        int id;
        try {
            id = Integer.parseInt(path.substring("/todos/".length()));
        } catch (NumberFormatException exception) {
            sendError(exchange, 404, "Todo not found.");
            return;
        }

        if ("GET".equals(method)) {
            Todo todo = TODOS.get(id);
            if (todo == null) {
                sendError(exchange, 404, "Todo not found.");
            } else {
                sendJson(exchange, 200, todo.toJson());
            }
        } else if ("PUT".equals(method)) {
            updateTodo(exchange, id);
        } else if ("DELETE".equals(method)) {
            deleteTodo(exchange, id);
        } else {
            methodNotAllowed(exchange, "GET, PUT, DELETE");
        }
    }

    private static void createTodo(HttpExchange exchange) throws IOException {
        TodoInput input = readTodoInput(exchange);
        if (input == null) {
            return;
        }
        int id = NEXT_ID.getAndIncrement();
        Todo todo = new Todo(id, input.title, input.completed);
        TODOS.put(id, todo);
        exchange.getResponseHeaders().set("Location", "/todos/" + id);
        sendJson(exchange, 201, todo.toJson());
    }

    private static void updateTodo(HttpExchange exchange, int id) throws IOException {
        Todo existing = TODOS.get(id);
        if (existing == null) {
            sendError(exchange, 404, "Todo not found.");
            return;
        }
        TodoInput input = readTodoInput(exchange);
        if (input == null) {
            return;
        }
        Todo updated = new Todo(id, input.title, input.completed);
        if (!TODOS.replace(id, existing, updated)) {
            sendError(exchange, 404, "Todo not found.");
            return;
        }
        sendJson(exchange, 200, updated.toJson());
    }

    private static void deleteTodo(HttpExchange exchange, int id) throws IOException {
        if (TODOS.remove(id) == null) {
            sendError(exchange, 404, "Todo not found.");
            return;
        }
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static TodoInput readTodoInput(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            sendError(exchange, 415, "Content-Type must be application/json.");
            return null;
        }

        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            sendError(exchange, 413, "Request body is too large.");
            return null;
        }
        try {
            return TodoInput.parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
            return null;
        }
    }

    private static String todosJson() {
        ArrayList<Todo> todos = new ArrayList<>(TODOS.values());
        todos.sort(Comparator.comparingInt(todo -> todo.id));
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < todos.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(todos.get(index).toJson());
        }
        return json.append(']').toString();
    }

    private static void methodNotAllowed(HttpExchange exchange, String allowed)
            throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendError(exchange, 405, "Method not allowed.");
    }

    private static void sendError(HttpExchange exchange, int status, String message)
            throws IOException {
        sendJson(exchange, status, "{\"error\":\"" + escapeJson(message) + "\"}");
    }

    private static void sendJson(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }

    private static final class Todo {
        private final int id;
        private final String title;
        private final boolean completed;

        private Todo(int id, String title, boolean completed) {
            this.id = id;
            this.title = title;
            this.completed = completed;
        }

        private String toJson() {
            return "{\"id\":" + id + ",\"title\":\"" + escapeJson(title)
                    + "\",\"completed\":" + completed + "}";
        }
    }

    private static final class TodoInput {
        private final String title;
        private final boolean completed;

        private TodoInput(String title, boolean completed) {
            this.title = title;
            this.completed = completed;
        }

        private static TodoInput parse(String json) {
            JsonObjectReader reader = new JsonObjectReader(json);
            return reader.readTodoInput();
        }
    }

    private static final class JsonObjectReader {
        private final String json;
        private int position;

        private JsonObjectReader(String json) {
            this.json = json;
        }

        private TodoInput readTodoInput() {
            skipWhitespace();
            expect('{');
            String title = null;
            Boolean completed = null;
            skipWhitespace();
            if (!consume('}')) {
                while (true) {
                    String key = readString();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    if ("title".equals(key) && title == null) {
                        title = readString();
                    } else if ("completed".equals(key) && completed == null) {
                        completed = readBoolean();
                    } else {
                        throw invalidJson();
                    }
                    skipWhitespace();
                    if (consume('}')) {
                        break;
                    }
                    expect(',');
                    skipWhitespace();
                }
            }
            skipWhitespace();
            if (position != json.length() || title == null || completed == null) {
                throw invalidJson();
            }
            if (title.trim().isEmpty() || title.length() > 200) {
                throw new IllegalArgumentException(
                        "Title must contain 1 to 200 non-whitespace characters.");
            }
            return new TodoInput(title, completed);
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < json.length()) {
                char character = json.charAt(position++);
                if (character == '"') {
                    return value.toString();
                }
                if (character < 0x20) {
                    throw invalidJson();
                }
                if (character != '\\') {
                    value.append(character);
                    continue;
                }
                if (position >= json.length()) {
                    throw invalidJson();
                }
                char escape = json.charAt(position++);
                switch (escape) {
                    case '"':
                    case '\\':
                    case '/':
                        value.append(escape);
                        break;
                    case 'b':
                        value.append('\b');
                        break;
                    case 'f':
                        value.append('\f');
                        break;
                    case 'n':
                        value.append('\n');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'u':
                        value.append(readUnicodeEscape());
                        break;
                    default:
                        throw invalidJson();
                }
            }
            throw invalidJson();
        }

        private char readUnicodeEscape() {
            if (position + 4 > json.length()) {
                throw invalidJson();
            }
            try {
                char value = (char) Integer.parseInt(json.substring(position, position + 4), 16);
                position += 4;
                return value;
            } catch (NumberFormatException exception) {
                throw invalidJson();
            }
        }

        private boolean readBoolean() {
            if (json.startsWith("true", position)) {
                position += 4;
                return true;
            }
            if (json.startsWith("false", position)) {
                position += 5;
                return false;
            }
            throw invalidJson();
        }

        private void skipWhitespace() {
            while (position < json.length()
                    && (json.charAt(position) == ' '
                    || json.charAt(position) == '\n'
                    || json.charAt(position) == '\r'
                    || json.charAt(position) == '\t')) {
                position++;
            }
        }

        private boolean consume(char expected) {
            if (position < json.length() && json.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw invalidJson();
            }
        }

        private IllegalArgumentException invalidJson() {
            return new IllegalArgumentException(
                    "Expected a JSON object with string 'title' and boolean 'completed'.");
        }
    }
}
