package s4y.guess.guesser.infra.h2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.errors.ConcurrentPress;
import s4y.guess.guesser.domain.errors.InvalidBit;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.WindowSize;

/**
 * Stores games in a database with Spring Data JPA. It maps rows to domain games and turns
 * Spring exceptions into domain errors.
 */
public final class GameRepositoryJpa implements GameRepository {

  private final GameRowRepository rows;

  public GameRepositoryJpa(GameRowRepository rows) {
    this.rows = rows;
  }

  @Override
  public Game create(WindowSize size) throws InfraFailure {
    UUID id = UUID.randomUUID();
    try {
      rows.saveAndFlush(new GameRow(id, size.value(), List.of()));
    } catch (DataAccessException e) {
      throw new InfraFailure("Could not create game", e);
    }
    return new Game(new GameId(id), size, Rounds.empty());
  }

  @Override
  public Optional<Game> find(GameId id) throws InfraFailure {
    Optional<GameRow> row;
    try {
      row = rows.findById(id.value());
    } catch (DataAccessException e) {
      throw new InfraFailure("Could not read game " + id.value(), e);
    }
    if (row.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toGame(row.get()));
  }

  /**
   * Inside one transaction the row loaded here is the same object that an earlier {@code find}
   * loaded, so it still has the version that transaction read. A newer version in the database
   * then makes the flush fail, and that failure is a {@link ConcurrentPress}.
   */
  @Override
  public void save(Game game) throws ConcurrentPress, InfraFailure {
    try {
      Optional<GameRow> found = rows.findById(game.id().value());
      if (found.isEmpty()) {
        throw new InfraFailure("game not found on save");
      }
      GameRow row = found.get();
      row.replaceContent(game.windowSize().value(), toRoundRows(game.rounds()));
      rows.saveAndFlush(row);
    } catch (OptimisticLockingFailureException e) {
      throw new ConcurrentPress(game.id());
    } catch (DataAccessException e) {
      throw new InfraFailure("Could not save game " + game.id().value(), e);
    }
  }

  private static Game toGame(GameRow row) throws InfraFailure {
    try {
      List<Round> rounds = new ArrayList<>();
      for (RoundRow r : row.getRounds()) {
        rounds.add(toRound(r));
      }
      return new Game(
          new GameId(row.getId()), new WindowSize(row.getWindowSize()), new Rounds(rounds));
    } catch (InvalidBit | IllegalArgumentException e) {
      throw new InfraFailure("Stored game " + row.getId() + " is not valid", e);
    }
  }

  private static Round toRound(RoundRow row) throws InvalidBit {
    Bit bit = Bit.of(row.getBit());
    if (row.getGuess() == null) {
      return new Round.Unguessed(bit);
    }
    return new Round.Guessed(bit, Bit.of(row.getGuess()));
  }

  private static List<RoundRow> toRoundRows(Rounds rounds) {
    List<RoundRow> result = new ArrayList<>();
    for (Round round : rounds.values()) {
      result.add(toRoundRow(round));
    }
    return result;
  }

  private static RoundRow toRoundRow(Round round) {
    return switch (round) {
      case Round.Guessed g -> new RoundRow(g.bit().value(), g.guess().value());
      case Round.Unguessed u -> new RoundRow(u.bit().value(), null);
    };
  }
}
