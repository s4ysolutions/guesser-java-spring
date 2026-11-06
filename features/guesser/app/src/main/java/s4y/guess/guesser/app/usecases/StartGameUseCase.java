package s4y.guess.guesser.app.usecases;

import java.util.Optional;
import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.WindowSize;
import s4y.guess.guesser.domain.workflows.GameWorkflows.GameSnapshot;
import s4y.guess.guesser.domain.workflows.GameWorkflows;

/**
 * Starts a new game. A missing window size means the default (32); any other number is moved
 * into the range 1 to 256.
 */
public final class StartGameUseCase {

  /** The wanted window size. Empty means the default. */
  public record Command(Optional<Integer> windowSize) {}

  private final GameRepository games;
  private final TransactionRunner tx;

  public StartGameUseCase(GameRepository games, TransactionRunner tx) {
    this.games = games;
    this.tx = tx;
  }

  public GameSnapshot execute(Command command) throws InfraFailure {
    WindowSize size =
        command.windowSize().map(WindowSize::clamped).orElseGet(WindowSize::defaultSize);
    return GameWorkflows.start(games, tx, size);
  }
}
