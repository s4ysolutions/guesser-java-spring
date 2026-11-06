package s4y.guess.guesser.presentation.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.guesser.contract.ErrorResponse;
import s4y.guess.guesser.domain.errors.ConcurrentPress;
import s4y.guess.guesser.domain.errors.GameNotFound;
import s4y.guess.guesser.domain.errors.GuesserError;
import s4y.guess.guesser.domain.errors.InvalidBit;
import s4y.guess.guesser.domain.errors.InvalidGameId;

/**
 * Turns domain errors into HTTP answers. The body is an {@link ErrorResponse} whose code is the
 * simple class name of the error.
 */
@RestControllerAdvice
public class GuesserErrorHandler {

  @ExceptionHandler(GameNotFound.class)
  public ResponseEntity<ErrorResponse> gameNotFound(GameNotFound e) {
    return answer(HttpStatus.NOT_FOUND, e);
  }

  @ExceptionHandler(ConcurrentPress.class)
  public ResponseEntity<ErrorResponse> concurrentPress(ConcurrentPress e) {
    return answer(HttpStatus.CONFLICT, e);
  }

  @ExceptionHandler(InvalidBit.class)
  public ResponseEntity<ErrorResponse> invalidBit(InvalidBit e) {
    return answer(HttpStatus.BAD_REQUEST, e);
  }

  @ExceptionHandler(InvalidGameId.class)
  public ResponseEntity<ErrorResponse> invalidGameId(InvalidGameId e) {
    return answer(HttpStatus.BAD_REQUEST, e);
  }

  @ExceptionHandler(InfraFailure.class)
  public ResponseEntity<ErrorResponse> infraFailure(InfraFailure e) {
    return answer(HttpStatus.SERVICE_UNAVAILABLE, e);
  }

  /** Any other guesser error is a bad request. */
  @ExceptionHandler(GuesserError.class)
  public ResponseEntity<ErrorResponse> otherGuesserError(GuesserError e) {
    return answer(HttpStatus.BAD_REQUEST, e);
  }

  private static ResponseEntity<ErrorResponse> answer(HttpStatus status, Exception e) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(e.getClass().getSimpleName(), e.getMessage()));
  }
}
