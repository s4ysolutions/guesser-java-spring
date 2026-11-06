package s4y.guess.guesser.app.usecases;

import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.guesser.domain.errors.GuesserError;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.WindowSize;
import s4y.guess.guesser.domain.workflows.GameWorkflows.GameSnapshot;
import s4y.guess.guesser.domain.workflows.GameWorkflows;

/** Changes the window size of a game. The number is moved into the range 1 to 256. */
public final class ChangeWindowSizeUseCase {

  /** The game and the wanted window size. */
  public record Command(GameId id, int requested) {}

  private final GameRepository games;
  private final TransactionRunner tx;

  public ChangeWindowSizeUseCase(GameRepository games, TransactionRunner tx) {
    this.games = games;
    this.tx = tx;
  }

  public GameSnapshot execute(Command command) throws GuesserError, InfraFailure {
    return GameWorkflows.changeWindowSize(
        games, tx, command.id(), WindowSize.clamped(command.requested()));
  }
}
