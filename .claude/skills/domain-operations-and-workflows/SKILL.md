---
name: domain-operations-and-workflows
description: Use when writing or reviewing a class named *Operations or *Workflows; when deciding if domain logic is pure or needs a port; when a domain test needs a fake or stub; when a rule keeps state in a field; when asked "why are workflows static?" or "where does state live?"; when a workflow gets a new port and every caller must change.
---

# Domain operations and workflows

## Overview

Domain logic comes in two kinds. Do not call either one a "domain service".

- **Operation**: pure. Values in, value out. No I/O, no ports, no mutable state, no Spring.
- **Workflow**: uses side effects through ports (database, network, clock, transaction).

The name tells the kind: `*Operations` and `*Workflows`. `grep` finds each kind.

## Rules

1. **Operation** is a `final` class with a private constructor and static methods. It takes domain values and returns domain values. It does not know any port. Package: `domain/operations`.
2. **Workflow** is a `final` class with a private constructor and static methods. Package: `domain/workflows`.
3. A workflow gets its ports and the `TransactionRunner` as **method arguments**. It has no fields and no state.
4. State a workflow needs lives behind a port. Repositories are ports too, because a database is a side effect.
5. **Test litmus**: if testing a rule needs a fake or stub, it is a workflow. If you can test it with literal values, it is an operation. A "domain service" that needs a fake is a workflow with a wrong name.
6. A workflow calls operations for decisions and ports for I/O. Keep the decision in an operation so it is easy to test.
7. A workflow may call another workflow, and passes the needed ports down.
8. A workflow throws checked domain errors (see [domain-value-objects](../domain-value-objects/SKILL.md)) plus `InfraFailure`. It never declares `throws Exception`.
9. The workflow owns the transaction. See [transaction-port](../transaction-port/SKILL.md).
10. A workflow returns a domain record. When no existing type fits, the result record lives **in `domain/workflows`** next to the workflow: `GameSnapshot(Game, Score)` and `PressOutcome(...)`. Not in `vo`: a record that holds a `Game` in `vo` makes a package cycle with `entities`.
11. Small `private static` helpers in the workflow class (`snapshot(game)`, `failureOf(outcome)` in `GameWorkflows`) keep the transaction lambda short. A helper that is pure and has a rule in it belongs in an operation instead.

### Why static

The domain has no Spring and no bean wiring. So a workflow cannot get its ports injected. The app layer receives adapters from the composition root (see [composition-root](../composition-root/SKILL.md)) and passes them down on each call. A static method with ports as arguments shows every dependency in the signature.

Trade-off: a new port changes the signature, so every caller changes. This is fine while each workflow has one caller (the use case). If a workflow takes many ports, group them in a parameter record, for example `record GameDeps(GameRepository games, GuessGateway gateway, TransactionRunner tx)`. Put that record next to the workflows, because it holds port types.

## Example

Pure rule, tested with literals.
`features/guesser/domain/src/main/java/s4y/guess/guesser/domain/operations/GuessOperations.java`

```java
public final class GuessOperations {
  private GuessOperations() {}

  public static boolean needsGuess(Bits history) { return !history.isEmpty(); }

  public static Bits input(Game game) {
    return game.rounds().bits().lastN(game.windowSize().value());
  }
}
```

Side effect through a port. No history means the gateway is not called.
`features/guesser/domain/src/main/java/s4y/guess/guesser/domain/workflows/GuessWorkflows.java`

```java
public final class GuessWorkflows {
  private GuessWorkflows() {}

  public static GuessOutcome guessNext(GuessGateway gateway, Game game) {
    Bits input = GuessOperations.input(game);
    if (!GuessOperations.needsGuess(input)) return new GuessOutcome.Skipped();
    try {
      return new GuessOutcome.Made(gateway.guess(input));
    } catch (GuessFailed e) {
      return new GuessOutcome.Failed(e.reason(), e.getMessage());
    }
  }
}
```

`GuessFailed` never leaves the domain. The workflow turns it into a `GuessOutcome.Failed` value.

## Common mistakes

- An `*Operations` class with a repository field or a `Clock.now()` call. Pass the time in as a value, or make it a workflow.
- A workflow with a field such as `private final GameRepository games`. State goes behind a port; ports go in as arguments.
- A static mutable field (cache, counter) in a workflow or operation. Hidden state breaks tests. Put it behind a port.
- Decision logic buried in a workflow or an adapter. Pull the pure part out into an operation.
- A workflow that takes an infra type (`GameRowRepository`, `JdbcTemplate`) as argument. Only domain ports.
- `@Service` or `@Component` on a workflow.
- Testing an operation with a fake. If you need one, the class is a workflow.
- Putting `GameSnapshot` or `PressOutcome` in `vo` "because they are records". They are workflow results.
- Testing a workflow with Mockito. Domain tests use hand-written fakes in `src/test` (`GameRepositoryFake`, `GuessGatewayFake`) and `TransactionRunnerInMemory`. Mockito is not even on the classpath (see [maven-module-layout](../maven-module-layout/SKILL.md)).

## Differs from

- vocabla-mill: offers two shapes: a static function object, or a service class with injected dependencies (shape B) so new dependencies touch no caller. Here only the static shape exists (D6). If a workflow grows many ports, use a parameter record, not a service class.
- meridian: workflows are plain domain classes with the ports in the constructor (`new LabelWorkflow(labelRepository)`); a `@Bean` method in the feature's app-layer `@Configuration` creates them. The domain stays free of Spring there too — the cost is that the app layer carries Spring config. Here workflows are static, so nothing creates them and the app layer has no Spring at all.
