package s4y.guess.guesser.domain;

import java.util.UUID;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.WindowSize;

/** Small helpers to build values in tests. */
public final class TestGames {

  private TestGames() {}

  /** Bits from a text like "0110". */
  public static Bits bits(String text) {
    return new Bits(text.chars().mapToObj(c -> c == '1' ? Bit.ONE : Bit.ZERO).toList());
  }

  /** A game with the given window size and one unguessed round per character of the text. */
  public static Game game(int windowSize, String pressed) {
    Rounds rounds = Rounds.empty();
    for (Bit bit : bits(pressed).values()) {
      rounds = rounds.append(new Round.Unguessed(bit));
    }
    return new Game(new GameId(UUID.randomUUID()), new WindowSize(windowSize), rounds);
  }
}
