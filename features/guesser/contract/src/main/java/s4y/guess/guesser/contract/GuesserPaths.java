package s4y.guess.guesser.contract;

/** The URL paths of the guesser API. Server and client both use these constants. */
public final class GuesserPaths {

  public static final String GAMES = "/api/games";
  public static final String GAME = GAMES + "/{id}";
  public static final String PRESSES = GAME + "/presses";
  public static final String WINDOW_SIZE = GAME + "/window-size";
  public static final String ROUNDS = GAME + "/rounds";

  private GuesserPaths() {}

  /** Puts the id into the {id} part of a path template. */
  public static String expand(String template, String id) {
    return template.replace("{id}", id);
  }
}
