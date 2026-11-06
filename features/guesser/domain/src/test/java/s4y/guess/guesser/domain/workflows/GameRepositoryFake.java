package s4y.guess.guesser.domain.workflows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.errors.ConcurrentPress;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.WindowSize;

/** Test fake for {@link GameRepository}. It can also show a changed game on the second find. */
final class GameRepositoryFake implements GameRepository {

  private final Map<GameId, Game> store = new HashMap<>();
  private final List<Game> saved = new ArrayList<>();
  private int findCalls;
  private Game secondFindResult;
  private boolean conflictOnSave;

  void put(Game game) {
    store.put(game.id(), game);
  }

  /** The second call to find returns this game, like another press had changed it. */
  void secondFindReturns(Game game) {
    this.secondFindResult = game;
  }

  /** The next save fails with ConcurrentPress, like a version conflict in a database. */
  void conflictOnSave() {
    this.conflictOnSave = true;
  }

  List<Game> saved() {
    return saved;
  }

  int findCalls() {
    return findCalls;
  }

  @Override
  public Game create(WindowSize size) {
    Game game = new Game(new GameId(UUID.randomUUID()), size, Rounds.empty());
    store.put(game.id(), game);
    return game;
  }

  @Override
  public Optional<Game> find(GameId id) {
    findCalls++;
    if (findCalls == 2 && secondFindResult != null) {
      return Optional.of(secondFindResult);
    }
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public void save(Game game) throws ConcurrentPress {
    if (conflictOnSave) {
      throw new ConcurrentPress(game.id());
    }
    saved.add(game);
    store.put(game.id(), game);
  }
}
