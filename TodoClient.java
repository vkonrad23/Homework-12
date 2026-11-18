import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small REST client that retrieves a todo item from JSONPlaceholder.
 *
 * Usage:
 *   javac TodoClient.java
 *   java TodoClient 1
 */
public class TodoClient {
    private static final String API_URL = "https://jsonplaceholder.typicode.com/todos/";
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("\"title\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern COMPLETED_PATTERN =
            Pattern.compile("\"completed\"\\s*:\\s*(true|false)");

    public static void main(String[] args) {
        int todoId;
        try {
            todoId = parseTodoId(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("Error: " + exception.getMessage());
            System.err.println("Usage: java TodoClient [todo-id]");
            System.exit(1);
            return;
        }

        try {
            String json = getTodo(todoId);
            System.out.println("Todo #" + todoId);
            System.out.println("Title: " + extract(json, TITLE_PATTERN, "title"));
            System.out.println("Completed: " + extract(json, COMPLETED_PATTERN, "completed"));
        } catch (IOException exception) {
            System.err.println("Network error: " + exception.getMessage());
            System.exit(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Request interrupted.");
            System.exit(1);
        }
    }

    private static int parseTodoId(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Expected zero or one todo ID.");
        }
        if (args.length == 0) {
            return 1;
        }

        try {
            int id = Integer.parseInt(args[0]);
            if (id < 1 || id > 200) {
                throw new IllegalArgumentException("Todo ID must be between 1 and 200.");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Todo ID must be a number.");
        }
    }

    private static String getTodo(int todoId) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + todoId))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("The service returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String extract(String json, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Response did not contain a '" + fieldName + "' field.");
        }
        return matcher.group(1);
    }
}
