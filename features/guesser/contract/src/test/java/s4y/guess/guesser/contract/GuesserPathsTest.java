package s4y.guess.guesser.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GuesserPathsTest {

  @Test
  void expandReplacesTheId() {
    assertEquals("/api/games/abc/presses", GuesserPaths.expand(GuesserPaths.PRESSES, "abc"));
    assertEquals("/api/games/abc", GuesserPaths.expand(GuesserPaths.GAME, "abc"));
  }

  @Test
  void expandLeavesPathsWithoutIdAlone() {
    assertEquals("/api/games", GuesserPaths.expand(GuesserPaths.GAMES, "abc"));
  }
}
