package s4y.guess.guesser.domain.errors;

/**
 * The guess gateway could not give a guess. This error stays inside the domain: the workflow
 * turns it into a failed guess outcome. It is not a {@link GuesserError}.
 */
public final class GuessFailed extends Exception {

  /** Why the guess failed. */
  public enum Reason {
    UNAVAILABLE,
    BAD_ANSWER,
    TIMEOUT
  }

  private final Reason reason;

  public GuessFailed(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public GuessFailed(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
