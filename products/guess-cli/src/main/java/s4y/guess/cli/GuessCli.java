package s4y.guess.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import s4y.guess.guesser.contract.GameResponse;

/**
 * Command line client for the guess server. It parses the arguments, calls {@link GuessHttpClient}
 * and prints with {@link Printer}. Exit code 0 is ok, 1 is a server or connection error, 2 is bad usage.
 */
public final class GuessCli {

  private static final String DEFAULT_SERVER = "http://localhost:8080";

  private static final String USAGE =
      """
      usage: guess-cli [--server URL] COMMAND

      commands:
        new [--window N]      start a new game and print it
        show ID               print a game
        press ID BITS         press bits one by one, e.g. 0110
        window ID N           change the window size
        reset ID              delete all rounds of a game
        play [--window N]     start a game, then read bits from stdin; q quits
        help                  print this text

      The server URL is --server URL, or env GUESS_SERVER, or %s.
      """
          .formatted(DEFAULT_SERVER);

  private GuessCli() {}

  /** A wrong command line. The CLI prints the message and exits with code 2. */
  private static final class UsageError extends RuntimeException {
    UsageError(String message) {
      super(message);
    }
  }

  public static void main(String[] args) {
    System.exit(run(args));
  }

  private static int run(String[] args) {
    String server = null;
    List<String> rest = new ArrayList<>();
    try {
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("--server")) {
          if (i + 1 >= args.length) {
            throw new UsageError("--server needs a URL");
          }
          server = args[++i];
        } else {
          rest.add(args[i]);
        }
      }
      if (rest.isEmpty() || rest.get(0).equals("help") || rest.get(0).equals("--help")) {
        System.out.print(USAGE);
        return rest.isEmpty() ? 2 : 0;
      }
      if (server == null) {
        String fromEnv = System.getenv("GUESS_SERVER");
        server = fromEnv == null || fromEnv.isBlank() ? DEFAULT_SERVER : fromEnv;
      }
      execute(new GuessHttpClient(server), rest);
      return 0;
    } catch (UsageError e) {
      System.err.println("error: " + e.getMessage());
      System.err.print(USAGE);
      return 2;
    } catch (ServerError e) {
      System.err.println(e.code() + ": " + e.getMessage());
      return 1;
    } catch (UncheckedIOException e) {
      System.err.println("cannot reach server at " + server);
      return 1;
    }
  }

  private static void execute(GuessHttpClient client, List<String> args) {
    String command = args.get(0);
    List<String> params = args.subList(1, args.size());
    switch (command) {
      case "new" -> {
        Integer window = optionalWindow(params);
        System.out.println(Printer.game(client.start(window)));
      }
      case "show" -> {
        needArgs(command, params, 1);
        System.out.println(Printer.game(client.get(params.get(0))));
      }
      case "press" -> {
        needArgs(command, params, 2);
        String bits = params.get(1);
        if (bits.isEmpty() || !bits.chars().allMatch(c -> c == '0' || c == '1')) {
          throw new UsageError("BITS must contain only 0 and 1, got '" + bits + "'");
        }
        for (char c : bits.toCharArray()) {
          System.out.println(Printer.press(client.press(params.get(0), c - '0')));
        }
      }
      case "window" -> {
        needArgs(command, params, 2);
        System.out.println(Printer.game(client.changeWindowSize(params.get(0), parseInt(params.get(1), "N"))));
      }
      case "reset" -> {
        needArgs(command, params, 1);
        System.out.println(Printer.game(client.reset(params.get(0))));
      }
      case "play" -> play(client, optionalWindow(params));
      default -> throw new UsageError("unknown command '" + command + "'");
    }
  }

  /** Starts a game and presses every 0 or 1 read from stdin. A line with q, or the end of input, stops. */
  private static void play(GuessHttpClient client, Integer window) {
    GameResponse game = client.start(window);
    String id = game.id();
    System.out.println("game " + id + ", window " + game.windowSize());
    System.out.println("type 0 and 1 (several per line are fine), q to quit");
    try (var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = in.readLine()) != null) {
        if (line.strip().equals("q")) {
          break;
        }
        for (char c : line.toCharArray()) {
          if (c == '0' || c == '1') {
            System.out.println(Printer.press(client.press(id, c - '0')));
          } else if (!Character.isWhitespace(c)) {
            System.out.println("ignored '" + c + "'");
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    System.out.println("final " + Printer.score(client.get(id).score()));
  }

  private static Integer optionalWindow(List<String> params) {
    if (params.isEmpty()) {
      return null;
    }
    if (params.size() == 2 && params.get(0).equals("--window")) {
      return parseInt(params.get(1), "--window");
    }
    throw new UsageError("expected: [--window N]");
  }

  private static void needArgs(String command, List<String> params, int count) {
    if (params.size() != count) {
      throw new UsageError("'" + command + "' needs " + count + " argument(s)");
    }
  }

  private static int parseInt(String text, String name) {
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new UsageError(name + " must be a number, got '" + text + "'");
    }
  }
}
