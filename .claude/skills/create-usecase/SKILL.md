---
name: create-usecase
description: Use when adding a new use case or API endpoint to guess-java end to end; when someone asks "what do I touch to add a feature?" or "in which order do I write the layers?"; when a new rule, port, workflow, DTO, controller method, wiring bean or CLI command is needed for one new capability.
---

# Create a use case (checklist)

## Overview

A new capability touches many modules. Work **from the inside out**: domain first, then app, then contract and presentation, then the product, then the CLI. After each step the build is green and the new code has a test. Do not start the next step with a red build.

The smallest existing example is `ResetRoundsUseCase` (reset a game). Follow it through every file below. For a use case with a slow call, copy `PressBitUseCase`.

Paths are shortened: `D/` = `features/guesser/domain/src/main/java/s4y/guess/guesser/domain/`, `A/` = `features/guesser/app/src/main/java/s4y/guess/guesser/app/usecases/`.

## The checklist

| # | Step | Create or edit | Copy from | Test (write it first) |
|---|---|---|---|---|
| 1 | Domain value object or error, if the rule needs one | `D/vo/`, `D/errors/` | `vo/WindowSize.java`; `errors/GameNotFound.java` (extends `GuesserError`) | `ValueObjectsTest` |
| 2 | Operation: the pure rule | `D/operations/*Operations.java` | `operations/GameOperations.java` | `OperationsTest`, literal values |
| 3 | Port, only if there is new I/O | `D/ports/`, then an adapter in `infra/memory`, then the real adapter | `ports/GuessGateway.java`; `GuessGatewayScripted`; `GuessGatewayJev` | adapter test next to each adapter |
| 4 | Workflow: static method, ports as arguments, owns the transaction | `D/workflows/*Workflows.java` (+ result record next to it) | `GameWorkflows.reset` | `GameWorkflowsTest` with `GameRepositoryFake` and `TransactionRunnerInMemory` |
| 5 | Use case: nested `Command`, one `execute` | `A/XUseCase.java` | `A/ResetRoundsUseCase.java` | `UseCasesTest` with `infra/memory` |
| 6 | Contract: DTO records and the path constant | `features/guesser/contract/.../` | `ChangeWindowSizeRequest`, `GuesserPaths` | `GuesserPathsTest` if `expand` changes |
| 7 | Controller method, mapper, error mapping | `presentation/rest/.../GamesController`, `GameMapper`, `GuesserErrorHandler` | `GamesController.reset` | covered in step 9 |
| 8 | Wiring bean | `products/guess-server/.../GuesserWiring.java` | `resetRoundsUseCase(...)` bean | covered in step 9 |
| 9 | Product MockMvc test | `products/guess-server/src/test/.../` | `GuessServerProductionWiringTest`, `GuessServerTestWiringTest` | status codes and JSON shape |
| 10 | CLI command, only if people need it | `products/guess-cli/.../GuessHttpClient`, `GuessCli`, `Printer` | `reset` in all three | run it by hand (the CLI has no tests) |

## Rules for each step

1. **Value object / error.** Follow [domain-value-objects](../domain-value-objects/SKILL.md). A new error extends `GuesserError`. It gets a status in step 7, not now.
2. **Operation.** Values in, value out. If you need a fake to test it, it is a workflow (step 4). See [domain-operations-and-workflows](../domain-operations-and-workflows/SKILL.md).
3. **Port.** An interface in `domain/ports` with real checked exceptions in `throws`. Then **two adapters**: one in `infra/memory` (a real class, no Mockito) and one production adapter in its own infra module. A new method on an existing port means a new method in every adapter. For JPA, follow [spring-repository-as-adapter](../spring-repository-as-adapter/SKILL.md). A new infra module needs a pom (see [maven-module-layout](../maven-module-layout/SKILL.md)) and a config class that the product imports.
4. **Workflow.** `final` class, private constructor, static method. Put the method in the existing `*Workflows` class when the aggregate is the same. The workflow calls `tx.inTransaction(...)`. Slow I/O goes **before** it; inside, read again and check. See [transaction-port](../transaction-port/SKILL.md). Return a domain record that lives in `domain/workflows`. Test the happy path and every `throws`.
5. **Use case.** `public final class XUseCase`, `public record Command(...)`, constructor with ports, `execute` calls **one** workflow. No Spring annotation, no transaction. See [usecase-command](../usecase-command/SKILL.md). Add its test to `UseCasesTest`.
6. **Contract.** Records with Java types only, no annotations. Add the path as a constant in `GuesserPaths`. **No path literal in any other file.** See [endpoint-contract-separation](../endpoint-contract-separation/SKILL.md).
7. **Controller.** Add the use case to the `GamesController` constructor. The method: `@XxxMapping(GuesserPaths.NAME)`, `@PathVariable("id")`, parse with `GameId.parse` / `Bit.of`, build the `Command`, call `execute`, return `GameMapper.toResponse(...)`. Declare `throws GuesserError, InfraFailure`; no `try`. New error type: add an `@ExceptionHandler` method in `GuesserErrorHandler` (the table lives only there). Domain types never leave the controller.
8. **Wiring.** One `@Bean` method in `GuesserWiring` that calls `new XUseCase(...)`. If the controller constructor changed, `GuesserRestConfig` needs no edit: it imports the controller class. See [composition-root](../composition-root/SKILL.md).
9. **Product test.** At least one test in each wiring test class when behaviour differs (`GuessServerTestWiringTest` has exact guess values from the script; `GuessServerProductionWiringTest` uses real H2). Check status code and the JSON fields.
10. **CLI.** A method in `GuessHttpClient` (uses `GuesserPaths.expand`), a `case` in `GuessCli.execute`, output in `Printer`. The CLI reads contract records only. No domain class, no path literal.

## Final checks

Run these from the repository root.

```bash
mvn -q -o verify
grep -rn '"/api' --include=*.java . | grep -v GuesserPaths      # expect nothing
grep -rln 'org.springframework' features/guesser/domain features/guesser/app core/domain features/guesser/contract --include=*.java   # expect nothing
grep -rn 'org.mockito' --include=*.java --include=pom.xml . | grep -v '<exclusion>'                         # expect nothing
```

## Common mistakes

- Starting with the controller and working inward. You then invent DTO fields that the domain can not fill.
- A use case that calls a repository directly "because it is one call". Add a workflow (step 4).
- Adding a port method and forgetting the memory adapter. The product test with `infra/memory` then fails to compile.
- Adding the workflow but no test for the new `throws` case.
- A new domain error without an `@ExceptionHandler`. It falls into the `GuesserError` fallback and gives 400, which may be the wrong status.
- Forgetting the `@Bean` in `GuesserWiring`. The server fails at start with "no qualifying bean" for the controller.
- A path string in the CLI. Use `GuesserPaths`.
- Putting the result record in `vo`. It belongs in `workflows`.
- Renaming a request field in the contract and trusting the tests. They use the same record on both sides and do not fail (known gap, see endpoint-contract-separation).

## Differs from

- meridian: a new use case there is a `@Service` bean found by component scan, and the use case opens the transaction. Here step 5 is a plain class, step 8 creates it with `new`, and the transaction is opened in step 4 (the workflow).
