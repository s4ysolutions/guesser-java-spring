package s4y.guess.guesser.presentation.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Brings the REST controller and the error handler into a Spring context. The product imports
 * this class; the use case beans come from the product's wiring.
 */
@Configuration
@Import({GamesController.class, GuesserErrorHandler.class})
public class GuesserRestConfig {}
