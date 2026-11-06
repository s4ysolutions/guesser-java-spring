---
name: endpoint-contract-separation
description: Use when adding or changing a DTO record or a path constant in features/guesser/contract; when writing GamesController, GameMapper or GuesserErrorHandler; when the CLI (GuessHttpClient) needs a URL or JSON shape; when a domain type is returned from a controller; when mapping GameNotFound, ConcurrentPress or InfraFailure to an HTTP status; when someone asks to share a client between server and CLI.
---

# Endpoint contract separation

## Overview

The wire format has three parts, each in its own place:

| Part | Module | Holds |
|---|---|---|
| Contract | `features/guesser/contract` | DTO records and path constants. No dependencies. |
| Server side | `features/guesser/presentation/rest` | Controller, mapper (domain -> DTO), error handler |
| Client side | `products/guess-cli` | `GuessHttpClient`, reads the DTOs |

**The server writes the DTOs. The CLI reads them.** The contract contains no HTTP code, no Spring, no Jackson annotations, and no domain types. Records need no annotations for Jackson 3 (`tools.jackson.databind`).

## Rules

1. DTOs are records with Java types only: `String`, `int`, `Integer`, `Boolean`, `double`, `List`. A nullable value uses a boxed type (`Integer guess`, `Boolean hit`).
2. Ids are `String` on the wire. The controller calls `GameId.parse(id)` and `Bit.of(int)`; a bad value throws a domain error.
3. All paths are constants in `GuesserPaths`: `GAMES`, `GAME`, `PRESSES`, `WINDOW_SIZE`, `ROUNDS`, and `expand(template, id)`. **No path literal anywhere else**, in server or CLI. A copied literal can drift and gives a 404 at run time, not a compile error.
4. The controller uses the constants in its mapping annotations: `@PostMapping(GuesserPaths.PRESSES)`. Name the path variable explicitly: `@PathVariable("id")`. An explicit name does not depend on compiler flags (`-parameters`).
5. The controller is thin: parse input, build a `Command`, call one use case, map the result with `GameMapper`. It has no `try`/`catch` for domain errors.
6. `GameMapper` (package-private, static methods) maps domain -> DTO. Mapping lives in presentation. Domain types never appear in a controller's return type. A `Round` maps with a `switch` over the sealed cases (`Guessed` gives `guess` and `hit`; `Unguessed` gives `null` for both). A guess failure is sent as one string: `"TIMEOUT: slow"`.
7. Errors: one `@RestControllerAdvice` (`GuesserErrorHandler`) maps exceptions to a status and an `ErrorResponse(code, message)`. `code` is the simple class name.

| Exception | Status |
|---|---|
| `GameNotFound` | 404 |
| `ConcurrentPress` | 409 |
| `InvalidBit`, `InvalidGameId` | 400 |
| `InfraFailure` | 503 |

8. The domain error does not know its status. The table lives only in the handler.
9. The CLI is a simple monolith in `products/guess-cli`: its own `GuessHttpClient` (`java.net.http` + Jackson), no ports, no hexagonal layers, no tests. Do not put an HTTP client in the contract, and do not make the CLI depend on the server or on domain modules.
10. The CLI turns a non-2xx answer into `ErrorResponse`, prints `code: message`, and exits with 1.
11. There is no client-server round-trip test. The server's MockMvc tests check the wire format (JSON shape, status codes). The shared constants keep the CLI in step.
12. `GuesserErrorHandler` also has a fallback for any other `GuesserError`: 400. A new error that you forget to map gives 400 with its class name as `code`, not a 500. Still add its own handler method and a test.

## Example

`features/guesser/contract/src/main/java/s4y/guess/guesser/contract/GuesserPaths.java`

```java
public final class GuesserPaths {
  public static final String GAMES = "/api/games";
  public static final String GAME = GAMES + "/{id}";
  public static final String PRESSES = GAME + "/presses";
  public static final String WINDOW_SIZE = GAME + "/window-size";
  public static final String ROUNDS = GAME + "/rounds";

  private GuesserPaths() {}

  public static String expand(String template, String id) { return template.replace("{id}", id); }
}
```

`features/guesser/presentation/rest/src/main/java/s4y/guess/guesser/presentation/rest/GamesController.java`

```java
@PostMapping(GuesserPaths.PRESSES)
public PressResponse press(@PathVariable("id") String id, @RequestBody PressRequest request)
    throws GuesserError, InfraFailure {
  var outcome =
      pressBit.execute(new PressBitUseCase.Command(GameId.parse(id), Bit.of(request.bit())));
  return GameMapper.toResponse(outcome);
}
```

`products/guess-cli/src/main/java/s4y/guess/cli/GuessHttpClient.java` builds its request with `GuesserPaths.expand(GuesserPaths.PRESSES, id)` and reads `PressResponse`.

## Known gap (D14)

Nothing tests that the CLI and the server agree on a **request body**. The MockMvc tests (`GuessServerProductionWiringTest`, `GuessServerTestWiringTest`) build the request from the same records, so a renamed field changes both sides at once and no test fails. What protects the contract today:

- the shared records (`PressRequest`, `GameResponse`, ...): both sides compile against them;
- `GuesserPaths`: both sides use the same path constants.

What it does not catch: the CLI sending a body the server reads wrongly, for example if one side adds a custom Jackson setting. If this matters later, add a test that starts the server on a random port and runs `GuessHttpClient` against it. We chose not to (D14).

## Common mistakes

- Returning `Game` or `PressOutcome` from a controller.
- A `"/api/games"` string in the CLI or in a controller annotation.
- `@JsonProperty` or `@Schema` on a contract record.
- A DTO with a domain type field (`Bit`, `GameId`).
- `try`/`catch` per controller method. Declare `throws`; the advice handles it.
- `@PathVariable String id` without a name. Write `@PathVariable("id")`.
- A status code on a domain exception class.
- Believing the MockMvc tests guard the CLI. They do not (see the known gap).
- Adding the HTTP client to `contract` "so both sides share it". The server does not need a client.
- Letting the CLI use a domain class "just for `Bit`". It only reads `int` from the DTO.

## Differs from

- meridian: each presentation module owns its DTOs, and a client does not share them. Here the contract is a separate module that both the server and the CLI use, because they are in one repo and the CLI is a plain client.
- vocabla-mill: describes an endpoint as a typed OpenAPI value, separate from the route that implements it, and tests contract round trips with a real server and client. We keep the split of path and shape (contract) from handler (controller), but have no OpenAPI value and no round-trip test (D14).
