# guess-java

A Java demo of hexagonal architecture and DDD: a bit-guessing game.
It has a Spring Boot REST server (`products/guess-server`) and a plain Java CLI (`products/guess-cli`).
The skills in `.claude/skills/` are the main result. Keep them true to the code.

## Hard rules

- No Spring in `core/domain`, `features/*/domain`, `features/*/app` and `features/*/contract`. Maven dependencies enforce it. Never add a Spring dependency there.
- Operations are pure. Workflows are `final` classes with static methods; ports and `TransactionRunner` are method arguments. No fields, no state.
- The workflow owns the transaction. A use case never opens one. Slow I/O (gateway, Jev) runs before the transaction.
- No Mockito. The build excludes it. Test with hand-written fakes and the `infra/memory` modules.
- No path literals outside `GuesserPaths` in `features/guesser/contract`.
- Domain errors are checked exceptions. `GuesserError` is the feature base. `InfraFailure` is in core.
- Docs, comments and skills: plain English, B1 level, short sentences.
- Do not run `git commit` unless asked.

## Build

```bash
mvn verify              # first build needs network
mvn -q -o verify        # offline, after the first build
mvn -q -o verify -pl features/guesser/domain -am    # one module and what it needs
```

Jars: `products/guess-server/target/guess-server.jar`, `products/guess-cli/target/guess-cli.jar`.
The plan and decisions (D1 to D16) are in `docs/plans/2026-11-06-guess-java.md`.

# Local Skills (playbooks)

Detailed rules live as skills in `.claude/skills/<name>/SKILL.md`, not in this file.
**If your harness does not auto-load Claude Code skills, open the relevant file before starting the task.**

| Skill | File | Use when |
|---|---|---|
| hexagonal-layers | [.claude/skills/hexagonal-layers/SKILL.md](.claude/skills/hexagonal-layers/SKILL.md) | Deciding where a class belongs; core vs feature; which module may depend on which. Entry point |
| create-usecase | [.claude/skills/create-usecase/SKILL.md](.claude/skills/create-usecase/SKILL.md) | Adding a new use case or endpoint through all layers, in order |
| domain-value-objects | [.claude/skills/domain-value-objects/SKILL.md](.claude/skills/domain-value-objects/SKILL.md) | Adding a record, factory, sealed variant or domain error |
| domain-operations-and-workflows | [.claude/skills/domain-operations-and-workflows/SKILL.md](.claude/skills/domain-operations-and-workflows/SKILL.md) | Writing a `*Operations` or `*Workflows` class; pure vs port; static workflow shape |
| transaction-port | [.claude/skills/transaction-port/SKILL.md](.claude/skills/transaction-port/SKILL.md) | Touching `TransactionRunner`; read-then-write; `ConcurrentPress`; slow call near a transaction |
| usecase-command | [.claude/skills/usecase-command/SKILL.md](.claude/skills/usecase-command/SKILL.md) | Adding or changing a `*UseCase` and its nested `Command` |
| spring-repository-as-adapter | [.claude/skills/spring-repository-as-adapter/SKILL.md](.claude/skills/spring-repository-as-adapter/SKILL.md) | Working in `infra/h2`; JPA entity, Spring Data, `@DataJpaTest`, optimistic lock |
| composition-root | [.claude/skills/composition-root/SKILL.md](.claude/skills/composition-root/SKILL.md) | Editing `GuesserWiring`, `application.properties`, gateway choice, test wiring |
| endpoint-contract-separation | [.claude/skills/endpoint-contract-separation/SKILL.md](.claude/skills/endpoint-contract-separation/SKILL.md) | Changing a DTO, path constant, controller, error mapping or the CLI client |
| maven-module-layout | [.claude/skills/maven-module-layout/SKILL.md](.claude/skills/maven-module-layout/SKILL.md) | Editing a `pom.xml`; adding a module; Spring Boot 4 class and starter names; shade plugin |
