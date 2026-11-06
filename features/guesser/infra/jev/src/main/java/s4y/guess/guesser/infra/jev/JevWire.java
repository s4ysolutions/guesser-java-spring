package s4y.guess.guesser.infra.jev;

import java.util.Map;

/**
 * The JSON shapes of the Jev API, as far as this adapter uses them. They are package-private on
 * purpose: nothing outside this module may depend on the wire format.
 */
final class JevWire {

  private JevWire() {}

  /** Body of POST /v1/systemone. */
  record Request(String model, String state, Map<String, Question> questions) {}

  /** One question. Here it is always a choice question. */
  record Question(String type, String instructions, Map<String, String> criteria) {}

  /** Body of the answer. Fields that this adapter does not read are ignored. */
  record Response(Map<String, Answer> answers) {}

  /** The answer to one question. The confidence may be missing. */
  record Answer(String choice, Object confidence) {}
}
