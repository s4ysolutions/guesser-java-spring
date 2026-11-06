package s4y.guess.guesser.domain.errors;

/** Base class of all domain errors in the guesser feature. They are checked exceptions. */
public abstract class GuesserError extends Exception {

  protected GuesserError(String message) {
    super(message);
  }
}
