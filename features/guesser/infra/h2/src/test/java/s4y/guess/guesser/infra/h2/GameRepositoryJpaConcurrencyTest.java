package s4y.guess.guesser.infra.h2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import s4y.guess.core.infra.springtx.TransactionRunnerSpring;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.errors.ConcurrentPress;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.WindowSize;

/**
 * The optimistic lock needs real transactions, so the default test transaction is switched off
 * here. The inner transaction runs in the middle of the outer one, on its own connection.
 */
@DataJpaTest(showSql = false)
@Import(GuesserH2Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameRepositoryJpaConcurrencyTest {

  @Autowired GameRepository games;
  @Autowired PlatformTransactionManager manager;

  @Test
  void secondSaveOfAGameReadBeforeAnotherCommitFailsWithConcurrentPress() throws Exception {
    Game created = games.create(WindowSize.defaultSize());
    TransactionRunnerSpring tx = new TransactionRunnerSpring(manager);
    TransactionTemplate separate = new TransactionTemplate(manager);
    separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    assertThatThrownBy(
            () ->
                tx.inTransaction(
                    "first",
                    () -> {
                      Game seen = games.find(created.id()).orElseThrow();

                      // Another transaction reads the same game, saves and commits.
                      separate.executeWithoutResult(
                          status -> {
                            try {
                              Game other = games.find(created.id()).orElseThrow();
                              games.save(withRound(other, new Round.Unguessed(Bit.ONE)));
                            } catch (Exception e) {
                              throw new IllegalStateException(e);
                            }
                          });

                      games.save(withRound(seen, new Round.Unguessed(Bit.ZERO)));
                      return null;
                    }))
        .isInstanceOf(ConcurrentPress.class);

    // Only the committed transaction left a trace.
    Game stored = games.find(created.id()).orElseThrow();
    assertThat(stored.rounds().values()).containsExactly(new Round.Unguessed(Bit.ONE));
  }

  @Test
  void sequentialSavesInSeparateTransactionsDoNotConflict() throws Exception {
    Game created = games.create(WindowSize.defaultSize());
    TransactionRunnerSpring tx = new TransactionRunnerSpring(manager);

    for (Bit bit : List.of(Bit.ONE, Bit.ZERO, Bit.ONE)) {
      tx.inTransaction(
          "press",
          () -> {
            Game current = games.find(created.id()).orElseThrow();
            games.save(withRound(current, new Round.Unguessed(bit)));
            return null;
          });
    }

    assertThat(games.find(created.id()).orElseThrow().rounds().size()).isEqualTo(3);
  }

  private static Game withRound(Game game, Round round) {
    Rounds rounds = game.rounds().append(round);
    return new Game(game.id(), game.windowSize(), rounds);
  }
}
