# Java Todo REST Service

A dependency-free todo REST API and command-line client using Java 11 or newer.
The service stores todos in memory and starts with three example items; data
resets when the service restarts.

## Run

Open a terminal in this directory and start the service:

```text
javac TodoServer.java TodoClient.java
java TodoServer
```

The service listens at `http://localhost:8080`. In another terminal, retrieve a
todo with the client:

```text
java TodoClient
java TodoClient 2
```

## API

Response bodies use JSON (the `204 No Content` response has no body). Create and
replace requests require the
`Content-Type: application/json` header and a JSON object with a string `title`
and boolean `completed`. Titles must contain 1 to 200 non-whitespace characters.

| Method | Path | Description | Success |
|---|---|---|---|
| `GET` | `/todos` | List all todos | `200 OK` |
| `GET` | `/todos/{id}` | Retrieve a todo | `200 OK` |
| `POST` | `/todos` | Create a todo | `201 Created` |
| `PUT` | `/todos/{id}` | Replace a todo | `200 OK` |
| `DELETE` | `/todos/{id}` | Delete a todo | `204 No Content` |

Example:

```text
curl -i http://localhost:8080/todos/1
curl -i -X POST http://localhost:8080/todos -H "Content-Type: application/json" -d "{\"title\":\"Read the docs\",\"completed\":false}"
```

Missing todos return `404`; invalid JSON returns `400`. Choose another listening
port by passing it to the server, for example `java TodoServer 9090`. The client
uses port 8080 by default.
