---
name: usecase-command
description: Use when adding or changing a *UseCase class or its nested Command record (StartGameUseCase, PressBitUseCase and others); when a controller starts to hold business logic; when someone wants one class with many use-case methods; when a use case opens a transaction, returns a DTO, or gets a Spring annotation.
---

# Use case and command

## Overview

A use case is the entry point from presentation into the domain. It is thin. It turns a `Command` into domain values, calls **one** workflow, and returns a domain type.

It has no Spring annotation. The product creates it with `new` (see [composition-root](../composition-root/SKILL.md)).

## Rules

1. One class per intent: `XUseCase`, `public final`. Package `s4y.guess.guesser.app.usecases`, module `features/guesser/app`.
2. The `Command` is a **nested** `public record Command(...)` inside the use case. No separate file, no `commands` package.
3. One public method: `execute(Command)`. It lists the real checked exceptions in `throws`.
4. The constructor takes ports (and `TransactionRunner` if the workflow needs it). Store them in `private final` fields.
5. `execute` calls **one** workflow and passes the ports down. No other logic, except turning raw command values into domain values (`WindowSize.clamped(int)`). The workflow builds the result record (`GameSnapshot`, `PressOutcome`); the use case returns it as it is.
6. It returns a domain type (`GameSnapshot`, `PressOutcome`, both in `domain/workflows`). Never a DTO, never an HTTP type.
7. **No transaction.** The workflow owns it (see [transaction-port](../transaction-port/SKILL.md)).
8. **No use-case groups.** Do not write `GameUseCases` with five methods. A group hides which ports each use case needs.
9. The app module depends on `guesser-domain` only. No Spring, no infra, no presentation.
10. Commands carry input only. No ports, no auth token, no transaction.
11. Keep fields flat in the `Command`. Do not create a "query object" that is passed unchanged through every layer.

The use cases and their ports:

| Use case | Command | Returns | Ports |
|---|---|---|---|
| `StartGameUseCase` | `Command(Optional<Integer> windowSize)` | `GameSnapshot` | GameRepository, TransactionRunner |
| `GetGameUseCase` | `Command(GameId id)` | `GameSnapshot` | GameRepository |
| `PressBitUseCase` | `Command(GameId id, Bit bit)` | `PressOutcome` | GameRepository, GuessGateway, TransactionRunner |
| `ChangeWindowSizeUseCase` | `Command(GameId id, int requested)` | `GameSnapshot` | GameRepository, TransactionRunner |
| `ResetRoundsUseCase` | `Command(GameId id)` | `GameSnapshot` | GameRepository, TransactionRunner |

## Example

`features/guesser/app/src/main/java/s4y/guess/guesser/app/usecases/PressBitUseCase.java`

```java
public final class PressBitUseCase {

  /** The game and the bit that was pressed. */
  public record Command(GameId id, Bit bit) {}

  private final GameRepository games;
  private final GuessGateway gateway;
  private final TransactionRunner tx;

  public PressBitUseCase(GameRepository games, GuessGateway gateway, TransactionRunner tx) {
    this.games = games;
    this.gateway = gateway;
    this.tx = tx;
  }

  public PressOutcome execute(Command command) throws GuesserError, InfraFailure {
    return GameWorkflows.press(games, gateway, tx, command.id(), command.bit());
  }
}
```

`features/guesser/app/src/main/java/s4y/guess/guesser/app/usecases/StartGameUseCase.java` (the only logic is the int to `WindowSize` step)

```java
public GameSnapshot execute(Command command) throws InfraFailure {
  WindowSize size =
      command.windowSize().map(WindowSize::clamped).orElseGet(WindowSize::defaultSize);
  return GameWorkflows.start(games, tx, size);
}
```

Test: `features/guesser/app/src/test/java/s4y/guess/guesser/app/usecases/UseCasesTest.java` builds all five use cases on `GameRepositoryInMemory`, `GuessGatewayScripted` (`then(...)`, `thenFail(...)`, `inputs()`) and `TransactionRunnerInMemory`. No Mockito. Add one test per new behaviour: happy path, each domain error, each clamp.

## Common mistakes

- `@Service` or `@Component` on a use case. The domain and app have no Spring.
- A use case that calls two workflows and merges the results. Make one workflow that does both.
- A use case that calls a repository directly because "it is only one call". Add a small workflow.
- A use case that opens a transaction.
- Returning `GameResponse` (a contract DTO) from a use case. Mapping is presentation's job.
- A `Command` file in a `commands/` package.
- A controller that builds a `Game` or checks window limits. That is domain.

## Differs from

- meridian: use cases are `@Service` beans, and the use case owns the transaction. Here use cases are plain classes created by the product, and the workflow owns the transaction. The nested `Command` record and the single `execute` method are the same as meridian.
- vocabla-mill: a shared `UseCase<C>` interface with a command that carries its response type. Here there is no shared interface; each use case has its own `execute` signature with its own `throws`.
