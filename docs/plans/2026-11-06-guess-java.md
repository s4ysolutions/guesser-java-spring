# guess-java — implementation plan

Date: 2026-11-06
Executor: Sonnet / Haiku sub-agents, one task at a time, in order.
Language for all docs, comments, skills: plain English, B1 level.

## 0. What we build and why

A Java demo of hexagonal architecture and DDD. It is the Java/backend twin of
`~/s4y/guess` (TypeScript + Effect). The game: a person sends bits (0 or 1). Before
each bit the server guesses it from the last N bits. The person "wins" by staying
unpredictable.

Artifacts:
1. **Skills** in `.claude/skills/` — the most valuable artifact. Java rules for
   layering, modules, domain operations and workflows, transactions, Spring as
   adapters.
2. **Backend REST server** — product `guess-server` (Spring Boot 4, H2).
3. **CLI client** — product `guess-cli` (plain Java, no Spring, not hexagonal).
4. **Contract** module shared by both products: DTO records and path constants only.

Reference projects (read only, never change them):
- `~/s4y/guess` — same game in TS. Domain rules: `packages/features/guesser/logic/domain/src`.
  Longest-match algorithm: `packages/features/guesser/logic/adapter-longest-match/src/predict.ts`.
  Jev adapter: `packages/features/guesser/logic/adapter-jev/src/gateway.ts`.
- `~/s4y/vocabla-mill/.claude/skills/` — Scala skills, the source of the ideas.
- `~/s4y/meridian/meridian/.claude/skills/` — an earlier Java adoption of the same ideas
  (`hexagonal-layers`, `hexagonal-domain`, `hexagonal-use-cases`, `hexagonal-infra`,
  `hexagonal-presentation`, `error-handling`, `no-mockito-use-fakes`).

## 1. Decisions (fixed — do not change them)

| # | Decision |
|---|---|
| D1 | Top level is `core/`, `features/`, `products/`. Only `products/*` have a `main`. Each product is a composition root. |
| D2 | One feature: `guesser`. `core` holds only what has no feature owner: `InfraFailure`, `TransactionRunner` port and its adapters. |
| D3 | Maven multi-module. Root pom parent = `org.springframework.boot:spring-boot-starter-parent:4.0.8`. Java 25 (`<java.version>25</java.version>`). |
| D4 | Domain and app modules have **no Spring** dependency at all. Spring appears only in `infra`, `presentation`, `products`. The Maven deps enforce it. |
| D5 | **Operation** = pure: no I/O, no ports, no mutable state, no Spring. Tested with literal values. Name suffix `Operations`. |
| D6 | **Workflow** = side effects through ports. A `final` class with a private constructor and **static methods**. Ports and `TransactionRunner` are method arguments. No fields, no state. Name suffix `Workflows`. A test that needs a fake means the code is a workflow. |
| D7 | Any state a workflow needs lives behind a port. Repositories are ports too (a DB is a side effect). |
| D8 | The **workflow owns the transaction**: it calls `TransactionRunner.inTransaction(...)`. Use cases never open a transaction. Long I/O (gateway / Jev call) runs **before** the transaction opens. Meridian does it the other way (use case owns it) — skills mention this as the alternative we did not take. |
| D9 | A port has at least two adapters: production and test. Test adapters are real modules (`infra/memory`), not Mockito. No Mockito anywhere. |
| D10 | Use case = plain class (no Spring annotation), nested `Command` record, one `execute(Command)` method. Constructor takes ports. The product's `@Configuration` creates it with `new`. |
| D11 | Persistence: H2 + Spring Data JPA. The Spring Data interface and the JPA entity live only in `infra/h2`. An adapter class implements the domain port and maps entity ↔ domain. |
| D12 | `contract` = DTO records + path constants. No HTTP code, no Spring, no domain types. The server writes these DTOs, the CLI reads them. |
| D13 | CLI = simple monolith in `products/guess-cli`: its own HTTP client class (java.net.http + Jackson), no ports, no tests. |
| D14 | No client–server round-trip test. Server MockMvc tests cover the wire format. |
| D15 | Jev adapter is wired in the server when `guess.gateway=jev`; default is `longest-match`. API key from env var `JEV_API_KEY`. Never write a key into any file. |
| D16 | Domain errors are **checked** exceptions. Feature base: `GuesserError extends Exception` (abstract). `InfraFailure extends Exception` lives in core. |

## 2. Module map

Group id `s4y.guess`. Java base package `s4y.guess`.

| Directory | artifactId | Java package | Main deps (compile) | Test deps |
|---|---|---|---|---|
| `core/domain` | `guess-core-domain` | `s4y.guess.core.domain.{errors,ports}` | none | junit |
| `core/infra/memory` | `guess-core-infra-memory` | `s4y.guess.core.infra.memory` | core-domain | junit |
| `core/infra/spring-tx` | `guess-core-infra-spring-tx` | `s4y.guess.core.infra.springtx` | core-domain, `spring-tx` | junit |
| `features/guesser/domain` | `guess-guesser-domain` | `s4y.guess.guesser.domain.{vo,entities,errors,operations,ports,workflows}` | core-domain | junit, assertj, core-infra-memory |
| `features/guesser/app` | `guess-guesser-app` | `s4y.guess.guesser.app.usecases` | guesser-domain | junit, assertj, core-infra-memory, guesser-infra-memory |
| `features/guesser/infra/memory` | `guess-guesser-infra-memory` | `s4y.guess.guesser.infra.memory` | guesser-domain | junit, assertj |
| `features/guesser/infra/longest-match` | `guess-guesser-infra-longest-match` | `s4y.guess.guesser.infra.longestmatch` | guesser-domain | junit, assertj |
| `features/guesser/infra/jev` | `guess-guesser-infra-jev` | `s4y.guess.guesser.infra.jev` | guesser-domain, `tools.jackson.core:jackson-databind` | junit, assertj (stub server = JDK `com.sun.net.httpserver.HttpServer`) |
| `features/guesser/infra/h2` | `guess-guesser-infra-h2` | `s4y.guess.guesser.infra.h2` | guesser-domain, `spring-boot-starter-data-jpa` | `spring-boot-starter-data-jpa-test`, `com.h2database:h2`, core-infra-spring-tx |
| `features/guesser/contract` | `guess-guesser-contract` | `s4y.guess.guesser.contract` | none | — |
| `features/guesser/presentation/rest` | `guess-guesser-presentation-rest` | `s4y.guess.guesser.presentation.rest` | guesser-app, guesser-domain, guesser-contract, `spring-boot-starter-webmvc` | — (tested in the product) |
| `products/guess-server` | `guess-server` | `s4y.guess.server` | core-domain, core-infra-spring-tx, guesser-domain, guesser-app, guesser-infra-h2, guesser-infra-longest-match, guesser-infra-jev, guesser-presentation-rest, guesser-contract; `com.h2database:h2` runtime | `spring-boot-starter-webmvc-test`, `spring-boot-starter-test`, core-infra-memory, guesser-infra-memory |
| `products/guess-cli` | `guess-cli` | `s4y.guess.cli` | guesser-contract, `tools.jackson.core:jackson-databind` | none |

Aggregator poms: root `pom.xml` (modules `core`, `features`, `products`), `core/pom.xml`,
`core/infra/pom.xml`, `features/pom.xml`, `features/guesser/pom.xml`,
`features/guesser/infra/pom.xml`, `features/guesser/presentation/pom.xml`,
`products/pom.xml`. All aggregators: `<packaging>pom</packaging>`, parent = the
aggregator above. Leaf modules: parent = the nearest aggregator, `relativePath` set.
Internal versions: `${project.version}` = `0.1.0-SNAPSHOT`; put all internal artifacts
in the root `<dependencyManagement>`.

Spring Boot 4.0.8 facts (checked in the local Maven cache):
- Web starter: `spring-boot-starter-webmvc`. Test: `spring-boot-starter-webmvc-test`.
- `@AutoConfigureMockMvc` is `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- `@EntityScan` is `org.springframework.boot.persistence.autoconfigure.EntityScan`.
- JPA test starter: `spring-boot-starter-data-jpa-test` (`@DataJpaTest` — find its package
  with `unzip -l ~/.m2/repository/org/springframework/boot/spring-boot-data-jpa-test/4.0.8/*.jar | grep DataJpaTest`).
- Jackson 3: package `tools.jackson.databind` (`tools.jackson.databind.json.JsonMapper`).
  Records work without annotations.
- If a class is not where you expect, find it with `unzip -l <jar> | grep <Name>` — do not guess.

## 3. Domain model (features/guesser/domain)

Port of the TS rules. Keep names exactly.

### vo/
- `Bit` — `enum Bit { ZERO, ONE }`; `static Bit of(int v) throws InvalidBit` (0/1 only);
  `int value()`; `Bit opposite()`.
- `Bits` — `record Bits(List<Bit> values)`; compact ctor copies with `List.copyOf`;
  `static Bits empty()`; `Bits lastN(int n)` (n ≤ 0 → empty; else the last n);
  `int size()`; `boolean isEmpty()`; `String asText()` → `"0110"`.
- `WindowSize` — `record WindowSize(int value)`; constants `MIN=1`, `MAX=256`, `DEFAULT_VALUE=32`;
  compact ctor throws `IllegalArgumentException` outside [MIN, MAX] (programming error);
  `static WindowSize clamped(int requested)` clamps into range (domain rule from TS);
  `static WindowSize defaultSize()`.
- `Guess` — `record Guess(Bit bit, OptionalDouble confidence)`; `static Guess of(Bit)`.
- `Round` — `sealed interface Round permits Round.Guessed, Round.Unguessed`;
  `Bit bit()`; `record Guessed(Bit bit, Bit guess) implements Round { boolean hit() }`;
  `record Unguessed(Bit bit) implements Round`.
- `Rounds` — `record Rounds(List<Round> values)`; `empty()`, `append(Round)` returns new,
  `Bits bits()`, `int size()`, `Optional<Round> last()`.
- `Score` — `record Score(int hits, int guessed, int total)`; `int unguessed()`; `double ratio()`
  (0 when guessed = 0).
- `GuessOutcome` — `sealed interface GuessOutcome`: `record Made(Guess value)`,
  `record Failed(GuessFailed.Reason reason, String message)`, `record Skipped()` (no history);
  `Optional<Guess> guess()`.
- `GameId` — `record GameId(UUID value)`; `static GameId parse(String) throws InvalidGameId`.

### entities/
- `Game` — `record Game(GameId id, WindowSize windowSize, Rounds rounds)`. It is an entity:
  read and written by its own id.

### errors/
- `GuesserError extends Exception` (abstract, base of feature errors).
- `InvalidBit`, `InvalidGameId`, `GameNotFound(GameId)`, `ConcurrentPress(GameId)` — all `extends GuesserError`.
- `GuessFailed extends Exception` with `enum Reason { UNAVAILABLE, BAD_ANSWER, TIMEOUT }`,
  `Reason reason()`. **Not** a `GuesserError`: it never leaves the domain, the workflow
  turns it into `GuessOutcome.Failed`.

### operations/ (pure, static, `final` class + private ctor)
- `RoundOperations`: `Round make(Bit bit, Optional<Guess> guess)`.
- `ScoreOperations`: `Score of(Rounds rounds)`.
- `GameOperations`: `Game press(Game game, Bit bit, Optional<Guess> guess)`;
  `Game reset(Game game)`; `Game withWindowSize(Game game, WindowSize size)`.
- `GuessOperations`: `boolean needsGuess(Bits history)` (false when empty);
  `Bits input(Game game)` → `game.rounds().bits().lastN(game.windowSize().value())`.

### ports/ (interfaces)
- `GameRepository`:
  `Game create(WindowSize size) throws InfraFailure` (the adapter mints the id);
  `Optional<Game> find(GameId id) throws InfraFailure`;
  `void save(Game game) throws ConcurrentPress, InfraFailure` — `ConcurrentPress` when another
  transaction changed the same game since this transaction read it (JPA: optimistic lock).
  (Changed in T4; T2 may have written it without `ConcurrentPress`.)
- `GuessGateway`: `Guess guess(Bits lastBits) throws GuessFailed`.

### workflows/ (static, ports as arguments — D6, D8)
- `GameSnapshot` — `record GameSnapshot(Game game, Score score)`; `PressOutcome` — `record PressOutcome(Game game, Round round, Optional<GuessOutcome.Failed> guessFailure, Score score)`. Both are workflow results, so they live in `workflows`, not `vo`.
- `GuessWorkflows.guessNext(GuessGateway gateway, Game game)` → `GuessOutcome`.
  No history → `Skipped` without calling the gateway. `GuessFailed` → `Failed`.
- `GameWorkflows`:
  - `GameSnapshot start(GameRepository games, TransactionRunner tx, WindowSize size) throws InfraFailure`
  - `GameSnapshot load(GameRepository games, GameId id) throws GameNotFound, InfraFailure` (read, no tx)
  - `PressOutcome press(GameRepository games, GuessGateway gateway, TransactionRunner tx, GameId id, Bit bit) throws GuesserError, InfraFailure`
    1. `before = games.find(id)` or `GameNotFound` — no transaction.
    2. `outcome = GuessWorkflows.guessNext(gateway, before)` — **outside** the transaction (slow I/O).
    3. `tx.inTransaction("press", () -> { current = find(id); if current.rounds().size() != before.rounds().size() throw ConcurrentPress; next = GameOperations.press(current, bit, outcome.guess()); games.save(next); return PressOutcome(...) })`.
    The guess is made from history only; the gateway never sees the pressed bit.
  - `GameSnapshot changeWindowSize(GameRepository, TransactionRunner, GameId, WindowSize) throws GuesserError, InfraFailure`
  - `GameSnapshot reset(GameRepository, TransactionRunner, GameId) throws GuesserError, InfraFailure`

## 4. core

```java
// core/domain: s4y.guess.core.domain.errors
public class InfraFailure extends Exception {
  public InfraFailure(String message, Throwable cause) { super(message, cause); }
  public InfraFailure(String message) { super(message); }
}

// core/domain: s4y.guess.core.domain.ports
public interface TransactionRunner {
  <R, X extends Exception> R inTransaction(String name, Work<R, X> work) throws X, InfraFailure;

  @FunctionalInterface
  interface Work<R, X extends Exception> {
    R run() throws X, InfraFailure;
  }
}
```
- `core/infra/memory`: `TransactionRunnerInMemory` — runs the work directly (no rollback).
  Javadoc says: test and demo adapter.
- `core/infra/spring-tx`: `TransactionRunnerSpring(PlatformTransactionManager)` — uses
  `TransactionTemplate`. Inside the callback catch checked `Exception` → wrap in a private
  `static final class Carrier extends RuntimeException`; this makes Spring roll back.
  Outside: catch `Carrier` → rethrow the cause (`InfraFailure` as is, else cast to `X`
  with `@SuppressWarnings("unchecked")`). Catch Spring `TransactionException` → `InfraFailure`.
  Test it with a tiny fake `PlatformTransactionManager` (count commit/rollback), no Spring context.

## 5. app (features/guesser/app, package `s4y.guess.guesser.app.usecases`)

One class per use case. Each: `public final class XUseCase`, nested `public record Command(...)`,
constructor with ports, `execute(Command)` calls **one** workflow and returns a domain type.

| Use case | Command | Returns | Ports |
|---|---|---|---|
| `StartGameUseCase` | `Command(Optional<Integer> windowSize)` | `GameSnapshot` | GameRepository, TransactionRunner |
| `GetGameUseCase` | `Command(GameId id)` | `GameSnapshot` | GameRepository |
| `PressBitUseCase` | `Command(GameId id, Bit bit)` | `PressOutcome` | GameRepository, GuessGateway, TransactionRunner |
| `ChangeWindowSizeUseCase` | `Command(GameId id, int requested)` | `GameSnapshot` | GameRepository, TransactionRunner |
| `ResetRoundsUseCase` | `Command(GameId id)` | `GameSnapshot` | GameRepository, TransactionRunner |

`StartGameUseCase` and `ChangeWindowSizeUseCase` turn the int into `WindowSize.clamped(...)`.

## 6. infra adapters

- `infra/memory`: `GameRepositoryInMemory` (ConcurrentHashMap, `UUID.randomUUID()`),
  `GuessGatewayScripted` (returns queued guesses or throws queued `GuessFailed`; records the
  inputs it received so tests can check the window). Both are real adapters for tests.
- `infra/longest-match`: `LongestMatch.predictNext(Bits)` — port of `predict.ts`
  (pure helper; stays in infra because it is this adapter's own algorithm). `GuessGatewayLongestMatch implements GuessGateway`.
  Tests: copy the cases from `~/s4y/guess/.../adapter-longest-match/src/*.test.ts`.
- `infra/jev`: `GuessGatewayJev(JevConfig config)`; `record JevConfig(String apiKey, URI baseUrl, Duration timeout)`,
  default base `https://api.typesafe.ai`, timeout 10 s. Request/response shape: copy from
  the header comment in `adapter-jev/src/gateway.ts`. Map: IO error / non-2xx → `UNAVAILABLE`,
  bad JSON / no answer / choice not "0"/"1" → `BAD_ANSWER`, `HttpTimeoutException` → `TIMEOUT`.
  Wire DTOs for Jev are package-private records in this module. Tests use a JDK `HttpServer` on port 0.
- `infra/h2`: `GameRow` (`@Entity`, table `games`, `UUID id`, `int windowSize`,
  `@ElementCollection @OrderColumn(name="position") List<RoundRow> rounds`, `@Version long version`),
  `RoundRow` (`@Embeddable`, `int bit`, `Integer guess` nullable),
  `GameRowRepository extends JpaRepository<GameRow, UUID>`,
  `GameRepositoryJpa implements GameRepository` — maps rows ↔ domain. `save` loads the managed
  row by id (same persistence context as the `find` in the same transaction, so it keeps the
  version read there), copies the fields, and calls `saveAndFlush` so a version conflict fails
  inside the work, not at commit. `org.springframework.dao.OptimisticLockingFailureException`
  → `ConcurrentPress`; any other `DataAccessException` → `InfraFailure`.
  Backstop: `TransactionRunnerSpring` also maps `DataAccessException` thrown at commit → `InfraFailure`.
  Test: two `TransactionTemplate` transactions read the same game, both save; the second gets `ConcurrentPress`.
  `GuesserH2Config` (`@Configuration`, `@EnableJpaRepositories(basePackageClasses=GameRowRepository.class)`,
  `@EntityScan(basePackageClasses=GameRow.class)`, `@Bean GameRepository gameRepositoryJpa(GameRowRepository)`).
  Test: `@DataJpaTest` + `@Import(GuesserH2Config.class)` round-trips a game with both round kinds.

## 7. contract (features/guesser/contract, `s4y.guess.guesser.contract`)

Records only, Java types only (String, int, Integer, Boolean, double, List):
`StartGameRequest(Integer windowSize)`, `GameResponse(String id, int windowSize, List<RoundResponse> rounds, ScoreResponse score)`,
`RoundResponse(int bit, Integer guess, Boolean hit)`, `ScoreResponse(int hits, int guessed, int total, int unguessed, double ratio)`,
`PressRequest(int bit)`, `PressResponse(RoundResponse round, String guessFailure, ScoreResponse score)`,
`ChangeWindowSizeRequest(int windowSize)`, `ErrorResponse(String code, String message)`.
`GuesserPaths`: `GAMES = "/api/games"`, `GAME = GAMES + "/{id}"`, `PRESSES = GAME + "/presses"`,
`WINDOW_SIZE = GAME + "/window-size"`, `ROUNDS = GAME + "/rounds"`, and `static String expand(String template, String id)`.
Both server and CLI must use these constants — no path literals anywhere else.

## 8. presentation/rest

- `GamesController` (`@RestController`), constructor takes the five use cases.
  `POST GAMES` → 201 `GameResponse`; `GET GAME`; `POST PRESSES` → `PressResponse`;
  `PUT WINDOW_SIZE` → `GameResponse`; `DELETE ROUNDS` → `GameResponse`.
  Controller parses `GameId.parse(id)` and `Bit.of(int)`; maps with `GameMapper` (static methods, domain → DTO).
- `GuesserErrorHandler` (`@RestControllerAdvice`): `GameNotFound` 404, `ConcurrentPress` 409,
  `InvalidBit`/`InvalidGameId` 400, `InfraFailure` 503; body `ErrorResponse(code, message)`;
  code = simple class name.
- `GuesserRestConfig` (`@Configuration`, `@Import({GamesController.class, GuesserErrorHandler.class})`).

## 9. products

### guess-server (`s4y.guess.server`)
- `GuessServerApplication` (`@SpringBootApplication(scanBasePackageClasses = GuessServerApplication.class)`) —
  only its own package is scanned; features come in by `@Import`.
- `GuesserWiring` (`@Configuration`, `@Import({GuesserH2Config.class, GuesserRestConfig.class})`):
  `@Bean TransactionRunner` = `new TransactionRunnerSpring(ptm)`;
  `@Bean GuessGateway` longest-match `@ConditionalOnProperty(name="guess.gateway", havingValue="longest-match", matchIfMissing=true)`;
  `@Bean GuessGateway` jev `@ConditionalOnProperty(name="guess.gateway", havingValue="jev")` from
  `@Value("${JEV_API_KEY}")`; five `@Bean` use cases created with `new`.
  This class is the **only** place that names concrete adapters.
- `application.properties`: H2 in-memory URL `jdbc:h2:mem:guess`, `spring.jpa.hibernate.ddl-auto=update`,
  `guess.gateway=longest-match`.
- Tests:
  1. `GuessServerProductionWiringTest` — `@SpringBootTest @AutoConfigureMockMvc`, real H2 + longest-match:
     start game, press 0,1,0,1, get game, check counts and JSON shape.
  2. `GuessServerTestWiringTest` — `@SpringBootTest(properties="guess.gateway=none")` + a
     `@TestConfiguration` that provides `GameRepositoryInMemory`, `TransactionRunnerInMemory`,
     `GuessGatewayScripted` as `@Primary` beans: exact guess/hit values, `guessFailure` text,
     404, 409 is optional, 400 for bit 2.
     (If bean override fights you, set `spring.main.allow-bean-definition-overriding=true` in the test only.)

### guess-cli (`s4y.guess.cli`)
- `GuessCli` main. Server URL: `--server URL` or env `GUESS_SERVER`, default `http://localhost:8080`.
- Commands: `new [--window N]`, `show ID`, `press ID BITS` (e.g. `0110`, one request per bit, prints each round),
  `window ID N`, `reset ID`, `play [--window N]` (new game, then reads lines from stdin; each line of 0/1
  is pressed; prints guess, hit, score; `q` quits).
- `GuessHttpClient` — java.net.http + Jackson `JsonMapper`, uses `GuesserPaths` and contract records.
  Non-2xx → read `ErrorResponse`, print `code: message`, exit 1.
- Packaging: `maven-shade-plugin` → runnable `target/guess-cli.jar` (manifest main class).
  Spring Boot parent manages the plugin version; if not, use the latest 3.x.

## 10. Skills (`.claude/skills/<name>/SKILL.md`)

Front matter: `name`, `description` ("Use when …", concrete triggers), no other keys.
Each skill: Overview, Rules, a short Java example from **this** repo (file path), Common mistakes,
and a "Differs from" line when meridian or vocabla-mill made another choice. Java only:
no ZIO, no Scala, no Mill words. Keep each under ~200 lines.

| Skill | Content |
|---|---|
| `hexagonal-layers` | entry point: core / features / products; layer map; module dependency table (copy §2); where things go; links to the others |
| `domain-operations-and-workflows` | D5–D7; static workflow shape and why (no Spring in domain, adapters passed down); state behind ports; test litmus; naming |
| `transaction-port` | D8; `TransactionRunner` signature; checked exceptions through the lambda; no slow I/O inside; read-then-check (`ConcurrentPress`); meridian alternative |
| `domain-value-objects` | records, compact ctor, `of`/`clamped` factories, typed checked errors, sealed interfaces for variants (`Round`, `GuessOutcome`), no framework annotations |
| `usecase-command` | D10; one use case per command; no use-case groups; returns domain types; no transaction |
| `spring-repository-as-adapter` | D11; Spring Data interface + entity in infra only; adapter maps and converts exceptions; config class per infra module |
| `composition-root` | D1, D9, D15; per product; only place naming concrete adapters; `@ConditionalOnProperty` instead of if/else; prod vs test wiring |
| `endpoint-contract-separation` | D12–D14; contract = DTOs + paths; server writes / CLI reads; mapper in presentation; error mapping |
| `maven-module-layout` | aggregator poms, leaf poms, dependencyManagement, how a missing dependency is the layer check |

## 11. Tasks (run in order; each ends with `mvn -q -o verify` green, or `mvn -q verify` if offline fails)

Rules for every task:
- Do not run `git commit`. This folder is not a git repo; do not run `git init`.
- Do not edit files outside `/Users/dsa/s4y/guess-java`.
- Follow §1 exactly. If a decision looks wrong, write the problem in your final report; do not change it.
- No Mockito. No Lombok. No field injection. No `@Service`/`@Component` in domain or app.
- Report: files created, build result (last 30 lines on failure), deviations.

T1 — Skills (Sonnet). Write the 9 skills from §10, using §1–§9 of this plan as the source of
truth and the meridian/vocabla skills as style/reference. Examples point to the planned file
paths (code comes later; T7 fixes any drift).

T2 — Maven skeleton + core + guesser domain (Sonnet). All aggregator poms; `core/*`; `features/guesser/domain`
with unit tests: operations with literals; workflows with `TransactionRunnerInMemory` and small
hand-written fakes in `src/test` (domain cannot depend on `infra/memory` — Maven cycle).
Test `press` covers: guess made before the bit, Skipped on empty history, Failed on `GuessFailed`,
`ConcurrentPress` when the round count changed between read and write (fake repo that changes on 2nd find).

T3 — app + infra/memory + infra/longest-match + infra/jev (Sonnet). Tests for each.

T4 — infra/h2 + core/infra/spring-tx tests (Sonnet).

T5 — contract + presentation/rest + guess-server product + both server tests (Sonnet).

T6 — guess-cli (Haiku or Sonnet). Build the shaded jar.

T7 — Verify (main session): full `mvn verify`; start server; run the CLI against it; review the
code against the skills; fix drift in code or skills.

T8 — Lessons (Sonnet): update skills with what the code taught (real file paths, real snags,
Spring Boot 4 package names). Write `README.md` (how to build/run, module map) and `CLAUDE.md`
(`@AGENTS.md`) + `AGENTS.md` (project overview + skill table, like meridian's).
