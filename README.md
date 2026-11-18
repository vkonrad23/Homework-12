# Java REST Web Service Example

This program uses Java's built-in `HttpClient` to call the public
[JSONPlaceholder](https://jsonplaceholder.typicode.com/) REST API and retrieve a
todo item.

## Requirements

- Java 11 or newer
- Internet access

## Run

From this directory:

```text
javac TodoClient.java
java TodoClient
```

The default request retrieves todo `1`. To retrieve another item, pass an ID
from `1` through `200`:

```text
java TodoClient <todo-id>
```

For example:

```text
java TodoClient 42
```

The program sends a `GET` request to:

```text
https://jsonplaceholder.typicode.com/todos/42
```

and prints the todo title and completion status. It also reports invalid input,
network failures, interrupted requests, and non-success HTTP responses.



cd "C:\Users\vladk\OneDrive\Desktop\Homework 11"
javac TodoClient.java
java TodoClient


java TodoClient 42