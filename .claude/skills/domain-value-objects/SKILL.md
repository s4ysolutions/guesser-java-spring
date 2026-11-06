---
name: domain-value-objects
description: Use when creating or changing a record in domain/vo or domain/entities (Bit, Bits, WindowSize, Guess, Round, GameId, Game); when choosing between of(), parse() and clamped() factories; when a variant type needs a sealed interface (Round, GuessOutcome); when adding a domain exception (GuesserError, InvalidBit, GameNotFound); when an annotation (JPA, Jackson, Spring) appears on a domain type; when asked "VO or entity?".
---

# Domain value objects, entities and errors

## Overview

A value object (VO) is an immutable type that owns its rules. If you hold one, it is valid. Records are the default shape. An entity is a type you read and write by its own id (`Game`). Errors are typed, checked exceptions.

No framework touches these types: no JPA, no Jackson, no Spring.

## Rules

1. Use a `record` (or an `enum`). Immutable by construction.
2. Check the rules in the **compact constructor** or in a factory. Do not validate later in a service.
3. Copy collections: `List.copyOf(values)` in the compact constructor.
4. Name factories by what they do:
   - `of(...)`: checks the input, throws a typed checked error when the *caller* may send bad data (`Bit.of(int) throws InvalidBit`).
   - `parse(String)`: text from outside (`GameId.parse(...) throws InvalidGameId`).
   - `clamped(...)`: the domain rule is "fix it into range" (`WindowSize.clamped(int)`).
5. Two kinds of bad input:
   - Data from outside the program: a checked domain error (`InvalidBit`).
   - A bug in our code: an unchecked exception. The `WindowSize` constructor throws `IllegalArgumentException` outside [1, 256]. Callers that take user input go through `clamped`.
6. A type with variants is a `sealed interface` with record cases: `Round` (`Guessed`, `Unguessed`) and `GuessOutcome` (`Made`, `Failed`, `Skipped`). Use `switch` over it. Do not use an enum plus nullable fields.
7. Use an `enum` when the cases have no data (`Bit`).
8. Put behaviour on the type when it uses only that type's data: `Bits.lastN(int)`, `Score.ratio()`, `Round.Guessed.hit()`.
9. **Entity vs VO**: if a type is read by its own id and written by its own id, it is an entity (`Game` in `domain/entities`). Otherwise it is a VO (`domain/vo`).
10. **Errors are checked exceptions.** The feature base is `abstract class GuesserError extends Exception`. Cases: `InvalidBit`, `InvalidGameId`, `GameNotFound`, `ConcurrentPress`. `InfraFailure extends Exception` lives in `core/domain/errors`.
11. Only an adapter throws `InfraFailure`. It catches the raw exception (for example `DataAccessException`), and wraps it with the cause.
12. `GuessFailed` is an internal error with a `Reason`. It does **not** extend `GuesserError`, because it never leaves the domain. A workflow turns it into the value `GuessOutcome.Failed`.
13. Do not put an HTTP status in an error. The presentation layer maps errors to statuses (see [endpoint-contract-separation](../endpoint-contract-separation/SKILL.md)).

## Example

`features/guesser/domain/src/main/java/s4y/guess/guesser/domain/vo/WindowSize.java`

```java
public record WindowSize(int value) {
  public static final int MIN = 1;
  public static final int MAX = 256;
  public static final int DEFAULT_VALUE = 32;

  public WindowSize {
    if (value < MIN || value > MAX) {
      throw new IllegalArgumentException(
          "Window size must be between " + MIN + " and " + MAX + ": " + value);
    }
  }

  public static WindowSize clamped(int requested) {
    return new WindowSize(Math.max(MIN, Math.min(MAX, requested)));
  }

  public static WindowSize defaultSize() { return new WindowSize(DEFAULT_VALUE); }
}
```

`features/guesser/domain/src/main/java/s4y/guess/guesser/domain/vo/Round.java`

```java
public sealed interface Round permits Round.Guessed, Round.Unguessed {
  Bit bit();

  record Guessed(Bit bit, Bit guess) implements Round {
    public boolean hit() { return bit == guess; }
  }

  record Unguessed(Bit bit) implements Round {}
}
```

`features/guesser/domain/src/main/java/s4y/guess/guesser/domain/errors/GameNotFound.java`

```java
public final class GameNotFound extends GuesserError {
  private final GameId id;
  public GameNotFound(GameId id) { super("Game not found: " + id.value()); this.id = id; }
  public GameId id() { return id; }
}
```

`features/guesser/domain/src/main/java/s4y/guess/guesser/domain/vo/GuessOutcome.java`

```java
public sealed interface GuessOutcome {
  record Made(Guess value) implements GuessOutcome {}
  record Failed(GuessFailed.Reason reason, String message) implements GuessOutcome {}
  record Skipped() implements GuessOutcome {}

  default Optional<Guess> guess() {
    return this instanceof Made made ? Optional.of(made.value()) : Optional.empty();
  }
}
```

## Snags

- **Record component vs interface method.** `Made` has a component `value`, not `guess`. A record component `guess` would generate an accessor `guess()` returning `Guess`. The interface already has `Optional<Guess> guess()`. The two clash (same name, different return type), and the code does not compile. Name the component after what it is (`value`), and keep the interface method for the common question.
- **`Map.of` has no order.** The iteration order of `Map.of` changes between runs. If the order is visible (JSON sent to a service, a printed list, an assertion on a string), use `LinkedHashMap`. A Jev test was flaky for this reason: the answer options must go out as `0`, then `1` (`GuessGatewayJev`).
- **`Round.Guessed(Bit bit, Bit guess)`** has a component named `guess`. That is fine there, because `Round` has no `guess()` method on the interface. Check the interface before you name a component.

## Common mistakes

- `@Entity`, `@Column`, `@JsonProperty` on a domain record. Map in infra or presentation.
- A mutable list inside a record (`Rounds` must copy).
- `Bit` as an `int` or `boolean` in signatures. Use the type.
- A `null` for "no guess". Use `Optional<Guess>` or the `Unguessed` case.
- A `String` error code instead of an exception type.
- `throws Exception` on a port or workflow. List the real checked types.
- Wrapping `InfraFailure` in a new feature exception just to rename it.
- Letting `GuessFailed` reach a controller.
- A record component with the same name as a method on the sealed interface (`Made(Guess guess)`). It does not compile.
- `Map.of(...)` where the order of entries is visible. Use `LinkedHashMap`.

## Differs from

- meridian: domain errors are flat, each extends `Exception`, and "never a shared parent". Here the guesser has one abstract base, `GuesserError` (D16). Reason: the REST error handler and the workflows can say `throws GuesserError`, and the handler maps by class. `InfraFailure` is still separate and never a `GuesserError`.
- vocabla-mill: errors travel in the type of the result, as a sealed hierarchy. Java has no such slot, so checked exceptions do that job here.
