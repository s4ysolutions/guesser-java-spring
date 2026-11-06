package s4y.guess.cli;

/** The server answered with an error status. It carries the error code, the message and the HTTP status. */
public class ServerError extends RuntimeException {

  private final String code;
  private final int status;

  public ServerError(String code, String message, int status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
