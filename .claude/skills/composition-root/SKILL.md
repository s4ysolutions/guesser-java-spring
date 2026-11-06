---
name: composition-root
description: Use when wiring adapters to ports; when editing GuesserWiring, GuessServerApplication or application.properties in guess-server; when a class outside a product names a concrete adapter (GameRepositoryJpa, GuessGatewayJev); when choosing the gateway by a property (guess.gateway); when adding a @ConditionalOnProperty, a @Bean for a use case, or a test wiring with in-memory adapters; when adding a new product.
---

# Composition root

## Overview

A product (`products/guess-server`) is the one place that decides which concrete adapter implements each port and creates the use cases. Everything else depends on interfaces.

Features have no `main`. A feature offers `@Configuration` classes for its own adapters (`GuesserH2Config`, `GuesserRestConfig`). The product imports them and adds the rest.

## Rules

1. **Only a product names concrete adapter classes** of other modules, for example `GuessGatewayLongestMatch`, `GuessGatewayJev`, `TransactionRunnerSpring`. The exception: a feature's own config class names its own adapter (`GuesserH2Config` builds `GameRepositoryJpa`).
2. The product has no business logic. It is a flat list of `@Bean` methods.
3. Use cases are created with `new` in `@Bean` methods. They have no annotations.
4. Choose an adapter with a property and `@ConditionalOnProperty`, not with `if`/`else` in code:
   - `guess.gateway=longest-match` (default, `matchIfMissing = true`)
   - `guess.gateway=jev`
5. The Jev API key comes from the environment variable `JEV_API_KEY`, read only in the jev bean as `@Value("${JEV_API_KEY:}")` plus an explicit blank check. With a plain `${JEV_API_KEY}` a missing variable gives a long Spring placeholder error; with the default and the check, the start fails with one clear line (`guess.gateway=jev needs the environment variable JEV_API_KEY ...`). Never write a key into a file, a test or a log. `JevConfig.toString()` hides it.
6. `@SpringBootApplication(scanBasePackageClasses = GuessServerApplication.class)` scans only the product's own package (`s4y.guess.server`). Feature code comes in with `@Import`, and each feature config brings what it needs: `GuesserH2Config` carries `@EnableJpaRepositories` and `@EntityScan` for its own module. Auto-configuration still runs, so the product needs no JPA setup of its own. That keeps wiring visible and stops accidental beans.
7. Every port has at least a production adapter and a test adapter. Test adapters are real classes in `infra/memory` modules (`GameRepositoryInMemory`, `GuessGatewayScripted`, `TransactionRunnerInMemory`). **No Mockito.**
8. The product has two wiring tests (`products/guess-server/src/test/java/s4y/guess/server/`):
   - `GuessServerProductionWiringTest`: real H2 and longest-match, through MockMvc;
   - `GuessServerTestWiringTest`: `@SpringBootTest(properties = "guess.gateway=none")` plus a nested `@TestConfiguration` that gives `GameRepositoryInMemory` and `TransactionRunnerInMemory` as `@Primary` beans and a `GuessGatewayScripted` bean.
   `guess.gateway=none` matches neither `@ConditionalOnProperty`, so both production gateway beans are gone and the scripted one is the only `GuessGateway`. The repository and the transaction runner are not conditional, so the test beans are `@Primary` and win.
9. **Give production and test beans different names.** Production: `gameRepositoryJpa`, `transactionRunner`. Test: `gameRepositoryInMemory`, `transactionRunnerInMemory`. With different names nothing is overridden, so `@Primary` is enough and you do not need `spring.main.allow-bean-definition-overriding=true`. The same name would need that flag, and the flag hides real mistakes.
10. A feature never imports another feature's infra. If feature A needs feature B, A declares a port; the adapter that calls B lives in the product.
11. One composition root per deployable program. One per feature is a warning sign.

## Example

`products/guess-server/src/main/java/s4y/guess/server/GuesserWiring.java`

```java
@Configuration
@Import({GuesserH2Config.class, GuesserRestConfig.class})
public class GuesserWiring {

  @Bean
  public TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
    return new TransactionRunnerSpring(transactionManager);
  }

  @Bean
  @ConditionalOnProperty(name = "guess.gateway", havingValue = "longest-match", matchIfMissing = true)
  public GuessGateway guessGatewayLongestMatch() { return new GuessGatewayLongestMatch(); }

  @Bean
  @ConditionalOnProperty(name = "guess.gateway", havingValue = "jev")
  public GuessGateway guessGatewayJev(@Value("${JEV_API_KEY:}") String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("guess.gateway=jev needs the environment variable JEV_API_KEY ...");
    }
    return new GuessGatewayJev(JevConfig.withDefaults(apiKey));
  }

  @Bean
  public PressBitUseCase pressBitUseCase(
      GameRepository games, GuessGateway gateway, TransactionRunner tx) {
    return new PressBitUseCase(games, gateway, tx);
  }
  // four more use-case beans, same shape
}
```

## Not done yet

The Jev base URL is fixed by `JevConfig.withDefaults` (`https://api.typesafe.ai`). So no product test can run the `jev` wiring against a stub server. A property `guess.jev.base-url` (read in `guessGatewayJev`, passed to `new JevConfig(apiKey, baseUrl, timeout)`) would allow a product test with a JDK `HttpServer` stub. No product test starts the server with `guess.gateway=jev` today. `GuessGatewayJevTest` tests the adapter alone.

## Common mistakes

- `if (env.equals("prod")) ... else ...` in a config class. Use properties and conditions.
- `@ComponentScan` over `s4y.guess` in the product. It pulls in classes by accident.
- A controller or use case that creates `new GameRepositoryJpa(...)`.
- Business rules in `GuesserWiring` (for example clamping the window size).
- Mocking a port in a wiring test. Use the in-memory adapter.
- A test adapter that lives in `src/test` of the domain module and is copied into three modules. Put it in `infra/memory` once. (The domain module itself cannot depend on `infra/memory`, because that would be a Maven cycle; domain tests use small hand-written fakes.)
- Putting a real API key in `application.properties` or in a test.
- Same bean name in the production and the test wiring, then turning on `allow-bean-definition-overriding`. Use different names and `@Primary`.
- `@Value("${JEV_API_KEY}")` without a default. The error for a missing variable is hard to read. Use `:` and check for blank.

## Differs from

- meridian: use cases are `@Service` beans found by component scan; workflows are plain classes created by `@Bean` methods in each feature's app-layer `@Configuration` (`LabelFeatureConfig`). Here nothing in domain or app is a bean; the product builds them with `new`, and the product scans only itself.
- vocabla-mill: wires with a layer graph and type aliases for the transaction type. Here wiring is a list of `@Bean` methods, and the choice of adapter is a property.
