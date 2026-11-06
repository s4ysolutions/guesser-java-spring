package s4y.guess.guesser.presentation.rest;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.guesser.app.usecases.ChangeWindowSizeUseCase;
import s4y.guess.guesser.app.usecases.GetGameUseCase;
import s4y.guess.guesser.app.usecases.PressBitUseCase;
import s4y.guess.guesser.app.usecases.ResetRoundsUseCase;
import s4y.guess.guesser.app.usecases.StartGameUseCase;
import s4y.guess.guesser.contract.ChangeWindowSizeRequest;
import s4y.guess.guesser.contract.GameResponse;
import s4y.guess.guesser.contract.GuesserPaths;
import s4y.guess.guesser.contract.PressRequest;
import s4y.guess.guesser.contract.PressResponse;
import s4y.guess.guesser.contract.StartGameRequest;
import s4y.guess.guesser.domain.errors.GuesserError;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.GameId;

/**
 * The HTTP entry point of the guesser. It parses ids and bits, calls one use case per request,
 * and maps the result to contract records. Errors are turned into answers by {@link
 * GuesserErrorHandler}.
 */
@RestController
public class GamesController {

  private final StartGameUseCase startGame;
  private final GetGameUseCase getGame;
  private final PressBitUseCase pressBit;
  private final ChangeWindowSizeUseCase changeWindowSize;
  private final ResetRoundsUseCase resetRounds;

  public GamesController(
      StartGameUseCase startGame,
      GetGameUseCase getGame,
      PressBitUseCase pressBit,
      ChangeWindowSizeUseCase changeWindowSize,
      ResetRoundsUseCase resetRounds) {
    this.startGame = startGame;
    this.getGame = getGame;
    this.pressBit = pressBit;
    this.changeWindowSize = changeWindowSize;
    this.resetRounds = resetRounds;
  }

  @PostMapping(GuesserPaths.GAMES)
  public ResponseEntity<GameResponse> start(@RequestBody(required = false) StartGameRequest request)
      throws InfraFailure {
    Optional<Integer> windowSize = Optional.ofNullable(request).map(StartGameRequest::windowSize);
    var snapshot = startGame.execute(new StartGameUseCase.Command(windowSize));
    return ResponseEntity.status(HttpStatus.CREATED).body(GameMapper.toResponse(snapshot));
  }

  @GetMapping(GuesserPaths.GAME)
  public GameResponse get(@PathVariable("id") String id) throws GuesserError, InfraFailure {
    var snapshot = getGame.execute(new GetGameUseCase.Command(GameId.parse(id)));
    return GameMapper.toResponse(snapshot);
  }

  @PostMapping(GuesserPaths.PRESSES)
  public PressResponse press(@PathVariable("id") String id, @RequestBody PressRequest request)
      throws GuesserError, InfraFailure {
    var outcome =
        pressBit.execute(new PressBitUseCase.Command(GameId.parse(id), Bit.of(request.bit())));
    return GameMapper.toResponse(outcome);
  }

  @PutMapping(GuesserPaths.WINDOW_SIZE)
  public GameResponse changeWindowSize(
      @PathVariable("id") String id, @RequestBody ChangeWindowSizeRequest request)
      throws GuesserError, InfraFailure {
    var snapshot =
        changeWindowSize.execute(
            new ChangeWindowSizeUseCase.Command(GameId.parse(id), request.windowSize()));
    return GameMapper.toResponse(snapshot);
  }

  @DeleteMapping(GuesserPaths.ROUNDS)
  public GameResponse reset(@PathVariable("id") String id) throws GuesserError, InfraFailure {
    var snapshot = resetRounds.execute(new ResetRoundsUseCase.Command(GameId.parse(id)));
    return GameMapper.toResponse(snapshot);
  }
}
