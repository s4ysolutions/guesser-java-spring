package s4y.guess.guesser.app.usecases;

import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.guesser.domain.errors.GameNotFound;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.workflows.GameSnapshot;
import s4y.guess.guesser.domain.workflows.GameWorkflows;

/** Reads a game and its score. */
public final class GetGameUseCase {

  /** The id of the game to read. */
  public record Command(GameId id) {}

  private final GameRepository games;

  public GetGameUseCase(GameRepository games) {
    this.games = games;
  }

  public GameSnapshot execute(Command command) throws GameNotFound, InfraFailure {
    return GameWorkflows.load(games, command.id());
  }
}
