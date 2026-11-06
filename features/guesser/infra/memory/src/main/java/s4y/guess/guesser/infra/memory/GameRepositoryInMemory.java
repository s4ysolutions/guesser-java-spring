package s4y.guess.guesser.infra.memory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.WindowSize;

/**
 * Test and demo adapter for {@link GameRepository}. It keeps games in a map in memory. It does
 * not detect concurrent changes.
 */
public final class GameRepositoryInMemory implements GameRepository {

  private final Map<GameId, Game> store = new ConcurrentHashMap<>();

  @Override
  public Game create(WindowSize size) {
    Game game = new Game(new GameId(UUID.randomUUID()), size, Rounds.empty());
    store.put(game.id(), game);
    return game;
  }

  @Override
  public Optional<Game> find(GameId id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public void save(Game game) {
    store.put(game.id(), game);
  }
}
