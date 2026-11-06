package s4y.guess.guesser.presentation.rest;

import s4y.guess.guesser.contract.GameResponse;
import s4y.guess.guesser.contract.PressResponse;
import s4y.guess.guesser.contract.RoundResponse;
import s4y.guess.guesser.contract.ScoreResponse;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Score;
import s4y.guess.guesser.domain.workflows.GameSnapshot;
import s4y.guess.guesser.domain.workflows.PressOutcome;

/** Turns domain results into contract records. All methods are static and pure. */
final class GameMapper {

  private GameMapper() {}

  static GameResponse toResponse(GameSnapshot snapshot) {
    return new GameResponse(
        snapshot.game().id().value().toString(),
        snapshot.game().windowSize().value(),
        snapshot.game().rounds().values().stream().map(GameMapper::toResponse).toList(),
        toResponse(snapshot.score()));
  }

  static PressResponse toResponse(PressOutcome outcome) {
    String failure =
        outcome
            .guessFailure()
            .map(f -> f.reason().name() + ": " + f.message())
            .orElse(null);
    return new PressResponse(toResponse(outcome.round()), failure, toResponse(outcome.score()));
  }

  static RoundResponse toResponse(Round round) {
    return switch (round) {
      case Round.Guessed guessed ->
          new RoundResponse(guessed.bit().value(), guessed.guess().value(), guessed.hit());
      case Round.Unguessed unguessed -> new RoundResponse(unguessed.bit().value(), null, null);
    };
  }

  static ScoreResponse toResponse(Score score) {
    return new ScoreResponse(
        score.hits(), score.guessed(), score.total(), score.unguessed(), score.ratio());
  }
}
