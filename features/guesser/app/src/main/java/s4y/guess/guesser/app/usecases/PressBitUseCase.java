package s4y.guess.guesser.app.usecases;

import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.guesser.domain.errors.GuesserError;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.workflows.GameWorkflows;
import s4y.guess.guesser.domain.workflows.GameWorkflows.PressOutcome;

/** Presses a bit in a game. The server guesses the bit first, then the round is stored. */
public final class PressBitUseCase {

  /** The game and the bit that was pressed. */
  public record Command(GameId id, Bit bit) {}

  private final GameRepository games;
  private final GuessGateway gateway;
  private final TransactionRunner tx;

  public PressBitUseCase(GameRepository games, GuessGateway gateway, TransactionRunner tx) {
    this.games = games;
    this.gateway = gateway;
    this.tx = tx;
  }

  public PressOutcome execute(Command command) throws GuesserError, InfraFailure {
    return GameWorkflows.press(games, gateway, tx, command.id(), command.bit());
  }
}
