---
name: maven-module-layout
description: Use when adding or editing a pom.xml in guess-java; when creating a new module, aggregator or product; when an import fails because a module has no dependency on it; when someone adds Spring to a domain or app module; when setting internal versions or dependencyManagement; when a test in one module needs an adapter from another (cycle risk).
---

# Maven module layout

## Overview

Maven modules are the layer check. Each layer is its own module with only the dependencies it may use. If a domain class imports Spring, the build fails because the domain module has no Spring dependency. **If it compiles, the layers are right.** Do not turn this into a rule in a README only.

## Rules

1. Root `pom.xml`: parent `org.springframework.boot:spring-boot-starter-parent:4.0.8`, `<java.version>25</java.version>`, group id `s4y.guess`, version `0.1.0-SNAPSHOT`, `<packaging>pom</packaging>`, modules `core`, `features`, `products`.
2. **Aggregator poms** only list modules (`<packaging>pom</packaging>`). Their parent is the aggregator above them: `core/pom.xml`, `core/infra/pom.xml`, `features/pom.xml`, `features/guesser/pom.xml`, `features/guesser/infra/pom.xml`, `features/guesser/presentation/pom.xml`, `products/pom.xml`.
3. **Leaf poms** build a jar. Their parent is the nearest aggregator, with `relativePath` set.
4. The root `<dependencyManagement>` lists every internal artifact with `${project.version}`. A leaf then names a dependency without a version.
5. Artifact ids follow the path: `guess-core-domain`, `guess-guesser-infra-h2`, `guess-guesser-presentation-rest`. Products: `guess-server`, `guess-cli`.
6. **Allowed dependencies** (compile scope). Anything not listed is a violation. The full table is in [hexagonal-layers](../hexagonal-layers/SKILL.md).

| Module | Compile deps | Never |
|---|---|---|
| core-domain, guesser-domain, guesser-app | domain: core-domain; app: guesser-domain | any `org.springframework.*`, any infra, presentation |
| guesser-infra-* | guesser-domain, plus that technology's library | guesser-app, other infra modules |
| guesser-contract | none | everything |
| guesser-presentation-rest | app, domain, contract, `spring-boot-starter-webmvc` | infra |
| products | what they run | business logic |

7. Domain and app tests use `junit` and `assertj` only. Spring test starters appear only in infra and product tests.
8. **Test-scope cycles.** `guesser-domain` can not depend on `guesser-infra-memory`, even in test scope. That module depends on `guesser-domain`, so Maven stops with a reactor cycle. Domain tests use small hand-written fakes in `src/test` (`GameRepositoryFake`, `GuessGatewayFake`, `TestGames`). `core-infra-memory` is fine for them: it depends only on `core-domain`, and gives `TransactionRunnerInMemory`. `guesser-app` has no cycle: its tests use `guesser-infra-memory` and `core-infra-memory`.
9. `guess-server` runs `com.h2database:h2` at runtime scope. The domain and infra code never name an H2 class.
10. Spring Boot 4 names differ from Boot 3. See "Snags" below. If a class is not where you expect, look in the jar: `unzip -l <jar> | grep <Name>`.
11. Do not add Lombok to any pom. **Mockito is excluded by the build**, not only by convention: the Spring Boot test starters in `features/guesser/infra/h2/pom.xml` and `products/guess-server/pom.xml` carry an exclusion (snippet below). A test that imports Mockito does not compile.
12. The CLI shades into one runnable jar (`maven-shade-plugin`, manifest main class). The server is a normal Spring Boot app.
13. Build everything with `mvn -q -o verify`. Build one module and what it needs with `mvn -q -o verify -pl <path> -am`. The **first build needs network** (Tomcat, Jackson 3.1.x, plugins). After that, `-o` (offline) works.

## Example

`features/guesser/domain/pom.xml` (the whole dependency section)

```xml
<dependencies>
  <dependency>
    <groupId>s4y.guess</groupId>
    <artifactId>guess-core-domain</artifactId>
  </dependency>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>s4y.guess</groupId>
    <artifactId>guess-core-infra-memory</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

No Spring here. A test for the layer rule is the build itself: add `import org.springframework.stereotype.Service;` to a domain class and the module does not compile.

Root `pom.xml`, internal versions:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>s4y.guess</groupId>
      <artifactId>guess-guesser-domain</artifactId>
      <version>${project.version}</version>
    </dependency>
    <!-- one entry per internal module -->
  </dependencies>
</dependencyManagement>
```

## Snags

**Spring Boot 4 names** (checked against 4.0.8):

| Thing | Boot 4 name |
|---|---|
| Web starter | `spring-boot-starter-webmvc` |
| Web test starter | `spring-boot-starter-webmvc-test` |
| JPA test starter | `spring-boot-starter-data-jpa-test` |
| `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@EntityScan` | `org.springframework.boot.persistence.autoconfigure` |
| Jackson 3 | package `tools.jackson.databind`; Boot gives a `JsonMapper` bean (`tools.jackson.databind.json.JsonMapper`) |

Find a moved class with `unzip -l ~/.m2/repository/.../<jar> | grep <Name>`. Do not guess the package.

**Mockito exclusion.** Put this in every Spring Boot test starter:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webmvc-test</artifactId>
  <scope>test</scope>
  <exclusions>
    <exclusion><groupId>org.mockito</groupId><artifactId>*</artifactId></exclusion>
  </exclusions>
</dependency>
```

**maven-shade under the Boot parent** (`products/guess-cli/pom.xml`). The parent already runs a shade execution that merges Spring transformers (`spring.factories` and similar). The CLI has no Spring, so use `combine.self="override"` on the execution `<configuration>`. The override drops plugin-level configuration too, so `createDependencyReducedPom` must sit **inside** that execution configuration:

```xml
<execution>
  <phase>package</phase>
  <goals><goal>shade</goal></goals>
  <configuration combine.self="override">
    <createDependencyReducedPom>false</createDependencyReducedPom>
    <transformers>
      <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
      <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
        <mainClass>s4y.guess.cli.GuessCli</mainClass>
        <manifestEntries><Multi-Release>true</Multi-Release></manifestEntries>
      </transformer>
    </transformers>
  </configuration>
</execution>
```

Without it a `dependency-reduced-pom.xml` appears in the module (it is in `.gitignore`).

## Common mistakes

- Adding `spring-context` to `guess-guesser-app` "for one annotation".
- `spring-boot-starter-data-jpa` in a domain module because a type "needs" `@Entity`. The type belongs in infra.
- One big module per feature. It compiles, but checks nothing.
- An infra module that depends on a sibling infra module. Use a domain port.
- A feature module with a `spring-boot-maven-plugin` and a `main`. Only products run.
- Test helpers copied between modules. Put shared fakes in an `infra/memory` module.
- Internal version written in a leaf pom. Use the root `dependencyManagement`.
- Running a failing import through "optional" or "provided" scope to make it compile.
- Adding `guess-guesser-infra-memory` to the domain pom "for tests". It is a reactor cycle. Write a small fake in `src/test`.
- Copying a Boot 3 package name (`org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`) from the internet. Look in the Boot 4 jar.

## Differs from

- meridian: `app` modules depend on `spring-context`, and it uses a starter module that bundles many infra modules. Here `app` has no Spring, and there is one infra module per technology that the product lists by itself.
- vocabla-mill: modules are declared in a build file with explicit module dependency lists, and test sub-modules are children of the module. Maven has the same effect through `<dependencies>`; tests sit in the same module under `src/test`.
