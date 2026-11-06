---
name: spring-repository-as-adapter
description: Use when adding or changing anything in features/guesser/infra/h2 (GameRow, RoundRow, GameRowRepository, GameRepositoryJpa, GuesserH2Config); when a Spring Data interface or JPA entity is needed; when a DataAccessException must become InfraFailure; when a domain record gets an @Entity annotation; when writing a @DataJpaTest; when adding any new infra adapter module.
---

# Spring repository as adapter

## Overview

A repository is a **port**: an interface in `domain/ports` (`GameRepository`). Spring Data is a tool for the **adapter** that implements the port. So the Spring Data interface and the JPA entity live only in `infra/h2`.

```
domain/ports/GameRepository          (port, plain Java, throws ConcurrentPress, InfraFailure)
        ^ implements
infra/h2/GameRepositoryJpa           (adapter: maps and converts exceptions)
        uses
infra/h2/GameRowRepository           (Spring Data interface, used only by the adapter)
infra/h2/GameRow, RoundRow           (JPA entity and embeddable)
```

## Rules

1. The domain `Game` record has no annotations. `GameRow` is the JPA entity. The adapter maps both ways.
2. `GameRowRepository extends JpaRepository<GameRow, UUID>` is used only by `GameRepositoryJpa`. No other module names it.
3. The adapter class is named `<Port><Technology>`: `GameRepositoryJpa`. In-memory: `GameRepositoryInMemory`.
4. The adapter catches `org.springframework.dao.DataAccessException` and throws `InfraFailure` with the cause. `OptimisticLockingFailureException` (a subclass) is caught first and becomes the domain error `ConcurrentPress`. No Spring exception leaves the adapter. `GameRepository.save` declares `throws ConcurrentPress, InfraFailure`.
5. The adapter does **not** open transactions. The workflow does, through `TransactionRunner`. Do not put `@Transactional` on the adapter. Reads and writes join the transaction that `TransactionRunnerSpring` opened.
6. Ids: the adapter mints them (`GameRepository.create(WindowSize)` returns the new `Game`). The domain only carries and parses ids.
7. `@Version private Long version` on `GameRow` guards against two writes at the same moment. The version is not part of the domain `Game`; it stays in the row. Use the boxed `Long`, not `long` (see Snags).
8. Each infra module has one config class: `GuesserH2Config` with `@Configuration`, `@EnableJpaRepositories(basePackageClasses = GameRowRepository.class)`, `@EntityScan(basePackageClasses = GameRow.class)` (Spring Boot 4 package: `org.springframework.boot.persistence.autoconfigure.EntityScan`) and a `@Bean GameRepository gameRepositoryJpa(GameRowRepository rows)`. The product imports this class. Do not use component scan across modules.
9. Row types stay inside `infra/h2`. No other module imports `GameRow`, `RoundRow` or `GameRowRepository`; only the product depends on `guess-guesser-infra-h2`, and it imports `GuesserH2Config` only. Today these classes are `public`; package-private would be stricter.
10. Pure code inside an adapter stays in the adapter when it works on the adapter's own types. `LongestMatch.predictNext` is pure, but it is the longest-match adapter's own algorithm, so it stays in `infra/longest-match`.
11. Every port gets a second adapter for tests in `infra/memory`: a real class, not Mockito. See [composition-root](../composition-root/SKILL.md).
12. Test the JPA adapter with `@DataJpaTest` plus `@Import(GuesserH2Config.class)`. Round-trip a game that has both round kinds (`Guessed` and `Unguessed`). See Snags for the setup a library module needs.

## Example

`features/guesser/infra/h2/src/main/java/s4y/guess/guesser/infra/h2/GameRepositoryJpa.java`

```java
public final class GameRepositoryJpa implements GameRepository {
  private final GameRowRepository rows;

  public GameRepositoryJpa(GameRowRepository rows) { this.rows = rows; }

  @Override
  public Optional<Game> find(GameId id) throws InfraFailure {
    Optional<GameRow> row;
    try {
      row = rows.findById(id.value());
    } catch (DataAccessException e) {
      throw new InfraFailure("Could not read game " + id.value(), e);
    }
    return row.isEmpty() ? Optional.empty() : Optional.of(toGame(row.get()));
  }

  @Override
  public void save(Game game) throws ConcurrentPress, InfraFailure {
    try {
      // Same managed row as the earlier find() in this transaction: it keeps the version.
      GameRow row = rows.findById(game.id().value())
          .orElseThrow(() -> new InfraFailure("game not found on save"));
      row.replaceContent(game.windowSize().value(), toRoundRows(game.rounds()));
      rows.saveAndFlush(row);   // the version check runs here, inside the work
    } catch (OptimisticLockingFailureException e) {
      throw new ConcurrentPress(game.id());
    } catch (DataAccessException e) {
      throw new InfraFailure("Could not save game " + game.id().value(), e);
    }
  }
  // create(...), toGame(...) and the row mapping omitted
}
```

(In the real file the `orElseThrow` is an `if (found.isEmpty())` block, because the supplier cannot throw a checked exception.)

`GuesserH2Config` is in the same package and builds the adapter:

```java
@Configuration
@EnableJpaRepositories(basePackageClasses = GameRowRepository.class)
@EntityScan(basePackageClasses = GameRow.class)
public class GuesserH2Config {
  @Bean
  public GameRepository gameRepositoryJpa(GameRowRepository rows) {
    return new GameRepositoryJpa(rows);
  }
}
```

## Snags

- **Why `save` calls `findById` first.** In the transaction that `TransactionRunnerSpring` opened, the workflow already called `find`. `findById` in `save` returns the **same managed row** (first-level cache). That row still has the version the transaction read. Then `saveAndFlush` writes with that version. If another transaction committed in between, the update matches zero rows and Spring throws `OptimisticLockingFailureException`. `saveAndFlush` (not `save`) makes the failure happen inside the work, not at commit.
- **`version` is `Long`, not `long`.** Spring Data decides "new or not" with the version field. A primitive `long` is never null, so an entity with an id we assigned (`UUID.randomUUID()`) looks "not new". Spring then calls `merge`, which adds an extra `select`. With `Long`, `null` means new, and `saveAndFlush` does a plain `persist`.
- **`rounds` is `FetchType.EAGER`** and `spring.jpa.open-in-view=false` (in `products/guess-server/src/main/resources/application.properties`). `find` also runs **outside** a transaction: `GameWorkflows.press` reads the game before the transaction, and `load` is a read. Without a session, a lazy collection would throw `LazyInitializationException` when the adapter maps it. EAGER loads it inside `findById`.
- **`@DataJpaTest` in a library module has no `@SpringBootApplication`.** The module needs a test-only `@SpringBootConfiguration`: `src/test/java/.../h2/H2TestApplication.java` (an empty class). Each test then adds `@Import(GuesserH2Config.class)`.
- **`@DataJpaTest` wraps every test in a transaction** and rolls it back. That hides what the workflow does. A test of a workflow-owned transaction, or of an optimistic lock, needs real transactions: put `@Transactional(propagation = Propagation.NOT_SUPPORTED)` on the test class (`GameWorkflowsOnJpaTest`, `GameRepositoryJpaConcurrencyTest`).
- **Deterministic optimistic-lock test.** Open transaction A with `TransactionRunnerSpring`, `find` the game. Inside A, run transaction B with a `TransactionTemplate` set to `PROPAGATION_REQUIRES_NEW`: it reads, saves and commits on its own connection. Back in A, `save` must now throw `ConcurrentPress`. No threads, no sleeps. See `GameRepositoryJpaConcurrencyTest`.
- **Boot 4 test packages:** `@DataJpaTest` is in `org.springframework.boot.data.jpa.test.autoconfigure`. See [maven-module-layout](../maven-module-layout/SKILL.md).

## Common mistakes

- `@Entity` on the domain `Game`, or a Spring Data interface in `domain` or `app`.
- Returning `GameRow` from the adapter, or taking one as an argument.
- Letting `DataAccessException` or `OptimisticLockingFailureException` escape as an unchecked exception. Map it.
- `@Transactional` on adapter methods. Two transaction owners will fight.
- A `@Repository` or `@Component` on the adapter plus component scan from the product. The product imports the config instead.
- Putting the H2 URL or `ddl-auto` in the infra module. Those are product settings (`application.properties` of `guess-server`).
- Adding a second adapter by copying `GameRepositoryJpa`, and making infra modules depend on each other.
- `@Version long` (primitive). Use `Long`.
- A lazy `@ElementCollection` read outside a transaction.
- Testing the optimistic lock inside the default `@DataJpaTest` transaction. Both "transactions" share one session and never conflict.
- `rows.save(...)` in the adapter instead of `saveAndFlush(...)`. The version failure then shows up at commit, where the workflow can not turn it into `ConcurrentPress`.

## Differs from

- meridian: adapters use JDBC with a core Postgres module, and use cases own the transaction. Here the adapter uses Spring Data JPA with H2, and the workflow owns the transaction.
- vocabla-mill: uses a transaction context type that repositories take as a type parameter. Here repositories have no transaction argument; the ambient Spring transaction is used, and only `TransactionRunnerSpring` knows about it.
