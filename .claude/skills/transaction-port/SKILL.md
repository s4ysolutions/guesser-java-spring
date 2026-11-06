---
name: transaction-port
description: Use when a workflow must read and then write in one transaction; when touching TransactionRunner, TransactionRunnerSpring or TransactionRunnerInMemory; when a checked exception must pass through a transaction lambda and roll back; when a slow call (gateway, Jev, HTTP) sits near a transaction; when two requests can press at the same time (ConcurrentPress); when someone asks if a use case should open the transaction.
---

# Transaction port

## Overview

A transaction is a port. The domain does not know Spring transactions. `TransactionRunner` is an interface in `core/domain`. The **workflow** calls it. A use case never opens a transaction.

Only the workflow knows how many atomic steps its logic needs: none, one, or several.

## The port

`core/domain/src/main/java/s4y/guess/core/domain/ports/TransactionRunner.java`

```java
public interface TransactionRunner {
  <R, X extends Exception> R inTransaction(String name, Work<R, X> work) throws X, InfraFailure;

  @FunctionalInterface
  interface Work<R, X extends Exception> {
    R run() throws X, InfraFailure;
  }
}
```

- `X` is the checked exception your work throws. It passes straight out to the caller. No wrapping, no `throws Exception`.
- `InfraFailure` covers begin, commit and rollback problems, and failures of ports used inside.
- `name` is for logs.

Adapters:

- `TransactionRunnerSpring(PlatformTransactionManager)` in `core/infra/spring-tx`. It uses `TransactionTemplate`. The callback cannot throw checked exceptions, so it catches them, wraps them in a private `Carrier extends RuntimeException` (this makes Spring roll back), and rethrows the original cause outside the template (`InfraFailure` as it is, any other checked cause cast to `X`). A Spring `TransactionException` becomes `InfraFailure`. So does a `DataAccessException` that escapes the work, for example one thrown at commit.
- `TransactionRunnerInMemory` in `core/infra/memory`. It runs the work directly. No rollback. For tests and demos.

## Rules

1. The workflow owns the transaction. The use case is a pass-through.
2. **Slow I/O runs before the transaction.** A gateway or Jev call can take seconds. Inside a transaction it holds a database connection for that time. Run it first.
3. Inside the transaction, **read again and check**. State may have changed while the slow call ran. Compare, and throw a domain error if it did (`ConcurrentPress`). This closes the gap between read and write.
4. Keep the transaction body short: fast port calls and pure operations only.
5. Throw domain errors from inside the lambda to roll back. Do not catch and swallow them.
6. A read-only workflow (`GameWorkflows.load`) needs no transaction.
7. One `inTransaction` call per atomic unit. Do not nest.

## Example

`features/guesser/domain/src/main/java/s4y/guess/guesser/domain/workflows/GameWorkflows.java`

```java
public static PressOutcome press(GameRepository games, GuessGateway gateway,
    TransactionRunner tx, GameId id, Bit bit) throws GuesserError, InfraFailure {
  // 1. read, no transaction
  Game before = games.find(id).orElseThrow(() -> new GameNotFound(id));
  // 2. slow call, no transaction. The gateway sees history only, never `bit`.
  GuessOutcome outcome = GuessWorkflows.guessNext(gateway, before);
  // 3. short transaction: read again, check, write
  return tx.inTransaction("press", () -> {
    Game current = games.find(id).orElseThrow(() -> new GameNotFound(id));
    if (current.rounds().size() != before.rounds().size()) {
      throw new ConcurrentPress(id);
    }
    Game next = GameOperations.press(current, bit, outcome.guess());
    games.save(next);
    Round round = next.rounds().last().orElseThrow();
    return new PressOutcome(next, round, failureOf(outcome), ScoreOperations.of(next.rounds()));
  });
}
```

Test it with `TransactionRunnerInMemory` and a hand-written repository that changes the game on its second `find`. The test must expect `ConcurrentPress`. See `GameRepositoryFake.secondFindReturns` and `GameWorkflowsTest` in `features/guesser/domain/src/test`.

## Two guards against a lost update

`press` has two checks. They catch different cases. Keep both.

| Guard | Where | `ConcurrentPress` comes from | Catches |
|---|---|---|---|
| Round-count re-read | inside the transaction, in `GameWorkflows.press` | the workflow | A press that finished **between the slow guess and the transaction**. The guess was made for an old history, so the press is refused. |
| `@Version` check at `saveAndFlush` | in `GameRepositoryJpa.save` | the adapter (`OptimisticLockingFailureException` becomes `ConcurrentPress`) | Two transactions that **both passed the re-read** and now write at the same moment. The second write fails. |

The re-read alone is not a lock: two transactions can both read the same count. The version alone does not know the guess is stale: a press that committed before our transaction began would just win. The workflow test uses a fake that fails on the second `find` (guard 1). The adapter test uses two real transactions (guard 2): `GameRepositoryJpaConcurrencyTest`. See [spring-repository-as-adapter](../spring-repository-as-adapter/SKILL.md).

## Java inference snag: `Work<R, X>`

`X` is inferred from what the lambda throws.

- The lambda throws only `InfraFailure` (for example `GameWorkflows.start`): `X` infers as `RuntimeException`. The call site then declares only `InfraFailure`.
- The lambda throws `GameNotFound` and `ConcurrentPress` (for example `press`): `X` infers as their common supertype `GuesserError`. The method must declare `throws GuesserError, InfraFailure`.

This is why the domain errors share a base class (`GuesserError`). Two unrelated checked types would infer as `Exception`, and the method would need `throws Exception`.

## Common mistakes

- Calling the gateway inside `inTransaction`. The connection stays open for the whole call.
- Reading before the transaction and writing inside it without a second read and check. Two presses can then overwrite each other.
- A use case that opens the transaction and passes work down. It cannot handle workflows that need two transactions or none.
- Catching `Exception` inside the lambda to "be safe". That hides the rollback.
- Using Spring `@Transactional` on a domain class. The domain has no Spring.
- Removing the re-read because "the version column covers it". It does not: see the two guards above.
- Testing rollback with `TransactionRunnerInMemory`. It does not roll back. Test rollback in `core/infra/spring-tx` with a counting `PlatformTransactionManager`.

## Differs from

- meridian: the use case owns the transaction (it calls `runInTransaction`), and use cases are `@Service` beans. We did not take that. A use case cannot know if its workflow needs zero, one or two transactions.
- vocabla-mill: the same choice as here (workflow owns the transaction), but its transaction type is a type parameter. Here the port is plain and has no type parameter for the transaction.
