package s4y.guess.core.domain.errors;

/**
 * Something went wrong in infrastructure, for example the database is down.
 * The caller cannot fix it, so it is separate from domain errors.
 */
public class InfraFailure extends Exception {

  public InfraFailure(String message, Throwable cause) {
    super(message, cause);
  }

  public InfraFailure(String message) {
    super(message);
  }
}
