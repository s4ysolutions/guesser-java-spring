package s4y.guess.guesser.infra.h2;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to game rows. Only {@link GameRepositoryJpa} uses it. */
public interface GameRowRepository extends JpaRepository<GameRow, UUID> {}
