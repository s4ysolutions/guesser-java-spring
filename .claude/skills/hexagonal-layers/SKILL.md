---
name: hexagonal-layers
description: Use when deciding where a class, record, port, adapter, use case, controller or config belongs in guess-java; when adding a feature or a new module; when reviewing for a layer break such as Spring in domain, infra in app, or a main method in a feature; when you ask "core or feature?" or "which module may depend on which?". Entry point for all other skills in this project.
---

# Hexagonal layers (guess-java)

## Overview

Dependencies point inward: presentation -> app -> domain <- infra.
The domain knows no adapter and no framework. Products wire everything together.

The code has three top-level groups:

| Group | Holds | Has `main`? |
|---|---|---|
| `core/` | Only things with no feature owner: `InfraFailure`, the `TransactionRunner` port and its adapters | No |
| `features/` | One folder per bounded context. Today: `guesser` | No |
| `products/` | One runnable program each: `guess-server`, `guess-cli`. Each product is a composition root: it decides which adapters run | Yes |

Read the skill for the layer you touch:

- [domain-operations-and-workflows](../domain-operations-and-workflows/SKILL.md): pure rules vs side effects
- [transaction-port](../transaction-port/SKILL.md): who opens the transaction
- [domain-value-objects](../domain-value-objects/SKILL.md): records, factories, errors
- [usecase-command](../usecase-command/SKILL.md): the app layer
- [spring-repository-as-adapter](../spring-repository-as-adapter/SKILL.md): JPA and Spring Data in infra
- [composition-root](../composition-root/SKILL.md): wiring in products
- [endpoint-contract-separation](../endpoint-contract-separation/SKILL.md): contract, controller, CLI
- [maven-module-layout](../maven-module-layout/SKILL.md): poms, the dependency check, Spring Boot 4 names
- [create-usecase](../create-usecase/SKILL.md): checklist to add a new use case through all layers, in order

## Rules

1. `core` is for things with no feature owner. Ask: "Is there a second real feature that needs this today?" If not, it goes into the feature. If one feature already depends on the other, put the shared type in the feature that is depended on, not in `core`.
2. Only `products/*` have a `main`. Features and core never start a program.
3. `domain` and `app` modules have no Spring dependency. Spring appears only in `infra`, `presentation` and `products`. The Maven dependencies enforce this (see maven-module-layout).
4. Ports are interfaces in `domain/ports`. Infra only implements them. Infra never invents a port.
5. Each port has at least two adapters: a production one and a test one. Test adapters are real modules (`infra/memory`). No Mockito.
6. Presentation maps domain types to contract DTOs. A controller never returns a domain type.
7. Only a product names a concrete adapter class.
8. One technology per infra module. Infra modules do not depend on each other.

## Layer map (feature `guesser`)

```
features/guesser/
  domain/            vo, entities, errors, operations, ports, workflows  (no Spring)
                     workflows also holds the result records GameSnapshot, PressOutcome
  app/               usecases                                            (no Spring)
  infra/memory       test adapters (fake repository, scripted gateway)
  infra/longest-match, infra/jev, infra/h2    production adapters
  contract/          DTO records + path constants (no deps)
  presentation/rest  controller, error handler, mapper
products/guess-server    composition root: picks adapters, creates use cases
products/guess-cli       simple client, not hexagonal
```

Module dependencies (compile scope). Group id `s4y.guess`.

| Directory | Package | Main deps |
|---|---|---|
| `core/domain` | `...core.domain.{errors,ports}` | none |
| `core/infra/memory` | `...core.infra.memory` | core-domain |
| `core/infra/spring-tx` | `...core.infra.springtx` | core-domain, `spring-tx` |
| `features/guesser/domain` | `...guesser.domain.*` | core-domain |
| `features/guesser/app` | `...guesser.app.usecases` | guesser-domain |
| `features/guesser/infra/memory` | `...guesser.infra.memory` | guesser-domain |
| `features/guesser/infra/longest-match` | `...infra.longestmatch` | guesser-domain |
| `features/guesser/infra/jev` | `...infra.jev` | guesser-domain, jackson-databind |
| `features/guesser/infra/h2` | `...infra.h2` | guesser-domain, spring-boot-starter-data-jpa |
| `features/guesser/contract` | `...guesser.contract` | none |
| `features/guesser/presentation/rest` | `...presentation.rest` | guesser-app, guesser-domain, guesser-contract, spring-boot-starter-webmvc |
| `products/guess-server` | `s4y.guess.server` | all modules above that it runs, h2 at runtime |
| `products/guess-cli` | `s4y.guess.cli` | guesser-contract, jackson-databind |

## Where does it go?

| Thing | Place |
|---|---|
| Value object, entity, domain error | `domain/vo`, `domain/entities`, `domain/errors` |
| Pure rule | `domain/operations` (`*Operations`) |
| Rule that uses a port | `domain/workflows` (`*Workflows`) |
| Result record of a workflow (`GameSnapshot`, `PressOutcome`) | `domain/workflows`, next to the workflow |
| Port interface | `domain/ports` |
| Use case and its `Command` | `app/usecases` |
| JPA entity, Spring Data interface | `infra/h2` only |
| Algorithm that is one adapter's own (longest match) | that adapter's module |
| HTTP status, JSON shape | `presentation/rest`, `contract` |
| `@Configuration` that creates use cases or picks adapters | `products/guess-server` |

Why the result records are in `workflows` and not in `vo`: they hold a `Game` (in `entities`), and `Game` holds value objects (in `vo`). A record in `vo` that names `Game` makes a package cycle `vo` <-> `entities`. In `workflows` the arrows only go one way.

To add a new capability end to end, follow [create-usecase](../create-usecase/SKILL.md).

## Example

A workflow file imports only domain and core-domain types. If an import below does not compile, the layer rule is broken.

`features/guesser/domain/src/main/java/s4y/guess/guesser/domain/workflows/GameWorkflows.java`

```java
package s4y.guess.guesser.domain.workflows;

import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.guesser.domain.operations.GameOperations;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.ports.GuessGateway;
// no org.springframework.*, no jakarta.persistence.*, no s4y.guess.guesser.infra.*
```

## Common mistakes

- Putting a type in `core` because "another feature might want it later".
- A `@Service` or `@Component` on a domain or app class.
- A JPA annotation on a domain record.
- A controller that injects a repository.
- A feature module that has a `main` or a `@SpringBootApplication`.
- An infra module that depends on another infra module. Share through a domain port.
- A workflow result record in `vo`. It makes a `vo` <-> `entities` cycle. Put it in `workflows`.

## Differs from

- meridian: use cases are `@Service` beans and `app` depends on `spring-context`. Here `app` has no Spring and the product creates use cases with `new` (D10). meridian also lets the use case own the transaction; here the workflow does (D8).
- vocabla-mill: splits domain into a pure module and a workflow module because of an effect library. Here one `domain` module holds both; Java has no such split, and the "no Spring" rule is enough.
