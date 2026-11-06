package s4y.guess.cli;

import s4y.guess.guesser.contract.GameResponse;
import s4y.guess.guesser.contract.PressResponse;
import s4y.guess.guesser.contract.RoundResponse;
import s4y.guess.guesser.contract.ScoreResponse;

/** Turns server answers into short lines of text for the terminal. */
public final class Printer {

  private Printer() {}

  /** Game header, one line per round, and the score. */
  public static String game(GameResponse game) {
    var out = new StringBuilder();
    out.append("game ").append(game.id()).append(", window ").append(game.windowSize()).append('\n');
    int number = 1;
    for (RoundResponse round : game.rounds()) {
      out.append(round(number++, round, null)).append('\n');
    }
    out.append(score(game.score()));
    return out.toString();
  }

  /** One line for a press: the new round, a guess failure if any, and the score. */
  public static String press(PressResponse press) {
    return round(press.score().total(), press.round(), press.guessFailure()) + " | " + score(press.score());
  }

  /** For example {@code score 2/4 (50%)}. */
  public static String score(ScoreResponse score) {
    return "score " + score.hits() + "/" + score.guessed() + " (" + Math.round(score.ratio() * 100) + "%)";
  }

  private static String round(int number, RoundResponse round, String guessFailure) {
    var out = new StringBuilder("round " + number + ": bit " + round.bit() + ", ");
    if (round.guess() == null) {
      out.append("no guess");
      if (guessFailure != null) {
        out.append(" (").append(guessFailure).append(')');
      }
    } else {
      out.append("guess ").append(round.guess()).append(", ").append(Boolean.TRUE.equals(round.hit()) ? "hit" : "miss");
    }
    return out.toString();
  }
}
