package s4y.guess.guesser.infra.h2;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The JPA row of a game. It lives only in this module; the domain never sees it. The version
 * column makes two parallel writes of the same game fail instead of overwrite each other.
 */
@Entity
@Table(name = "games")
public class GameRow {

  @Id private UUID id;

  private int windowSize;

  // Eager, because the adapter reads games outside of a transaction too.
  @ElementCollection(fetch = FetchType.EAGER)
  @OrderColumn(name = "position")
  private List<RoundRow> rounds = new ArrayList<>();

  @Version private Long version;

  protected GameRow() {}

  public GameRow(UUID id, int windowSize, List<RoundRow> rounds) {
    this.id = id;
    this.windowSize = windowSize;
    this.rounds = new ArrayList<>(rounds);
  }

  public UUID getId() {
    return id;
  }

  public int getWindowSize() {
    return windowSize;
  }

  public List<RoundRow> getRounds() {
    return rounds;
  }

  public Long getVersion() {
    return version;
  }

  /** Copies the changeable fields into this managed row. The id and the version stay. */
  public void replaceContent(int newWindowSize, List<RoundRow> newRounds) {
    this.windowSize = newWindowSize;
    this.rounds.clear();
    this.rounds.addAll(newRounds);
  }
}
