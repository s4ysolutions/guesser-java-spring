package s4y.guess.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import s4y.guess.guesser.contract.ChangeWindowSizeRequest;
import s4y.guess.guesser.contract.ErrorResponse;
import s4y.guess.guesser.contract.GameResponse;
import s4y.guess.guesser.contract.GuesserPaths;
import s4y.guess.guesser.contract.PressRequest;
import s4y.guess.guesser.contract.PressResponse;
import s4y.guess.guesser.contract.StartGameRequest;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.infra.longestmatch.GuessGatewayLongestMatch;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts the real server wiring: H2 database, Spring transactions and the longest-match gateway.
 * Requests go through MockMvc, so this test checks the wire format too.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GuessServerProductionWiringTest {

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired GuessGateway gateway;

  @Test
  void usesTheLongestMatchGateway() {
    assertThat(gateway).isInstanceOf(GuessGatewayLongestMatch.class);
  }

  @Test
  void startsAGameWithTheDefaultWindowSize() throws Exception {
    GameResponse game = startGame(new StartGameRequest(null));

    assertThat(game.windowSize()).isEqualTo(32);
    assertThat(game.rounds()).isEmpty();
    assertThat(game.score().total()).isZero();
  }

  @Test
  void pressesBitsAndReadsTheGameBack() throws Exception {
    GameResponse game = startGame(new StartGameRequest(null));

    PressResponse first = press(game.id(), 0);
    PressResponse second = press(game.id(), 1);
    press(game.id(), 0);
    PressResponse fourth = press(game.id(), 1);

    assertThat(first.round().bit()).isZero();
    assertThat(first.round().guess()).isNull();
    assertThat(first.round().hit()).isNull();
    assertThat(first.guessFailure()).isNull();
    assertThat(second.round().guess()).isNotNull();
    assertThat(fourth.score().total()).isEqualTo(4);

    GameResponse loaded = getGame(game.id());

    assertThat(loaded.id()).isEqualTo(game.id());
    assertThat(loaded.rounds()).hasSize(4);
    assertThat(loaded.rounds()).extracting("bit").containsExactly(0, 1, 0, 1);
    assertThat(loaded.rounds().get(0).guess()).isNull();
    assertThat(loaded.score().total()).isEqualTo(4);
    assertThat(loaded.score().guessed()).isEqualTo(3);
    assertThat(loaded.score().unguessed()).isEqualTo(1);
  }

  @Test
  void clampsTheWindowSize() throws Exception {
    GameResponse game = startGame(new StartGameRequest(null));

    MvcResult result =
        mvc.perform(
                put(GuesserPaths.WINDOW_SIZE, game.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new ChangeWindowSizeRequest(999))))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(read(result, GameResponse.class).windowSize()).isEqualTo(256);
  }

  @Test
  void resetRemovesTheRounds() throws Exception {
    GameResponse game = startGame(new StartGameRequest(5));
    press(game.id(), 1);
    press(game.id(), 0);

    MvcResult result =
        mvc.perform(delete(GuesserPaths.ROUNDS, game.id())).andExpect(status().isOk()).andReturn();

    GameResponse reset = read(result, GameResponse.class);
    assertThat(reset.rounds()).isEmpty();
    assertThat(reset.windowSize()).isEqualTo(5);
    assertThat(getGame(game.id()).rounds()).isEmpty();
  }

  @Test
  void unknownGameIsNotFound() throws Exception {
    MvcResult result =
        mvc.perform(get(GuesserPaths.GAME, UUID.randomUUID().toString()))
            .andExpect(status().isNotFound())
            .andReturn();

    assertThat(read(result, ErrorResponse.class).code()).isEqualTo("GameNotFound");
  }

  @Test
  void badGameIdIsABadRequest() throws Exception {
    MvcResult result =
        mvc.perform(get(GuesserPaths.GAME, "abc")).andExpect(status().isBadRequest()).andReturn();

    assertThat(read(result, ErrorResponse.class).code()).isEqualTo("InvalidGameId");
  }

  @Test
  void badBitIsABadRequest() throws Exception {
    GameResponse game = startGame(new StartGameRequest(null));

    MvcResult result =
        mvc.perform(
                post(GuesserPaths.PRESSES, game.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new PressRequest(2))))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(read(result, ErrorResponse.class).code()).isEqualTo("InvalidBit");
  }

  private GameResponse startGame(StartGameRequest request) throws Exception {
    MvcResult result =
        mvc.perform(
                post(GuesserPaths.GAMES)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, GameResponse.class);
  }

  private GameResponse getGame(String id) throws Exception {
    MvcResult result = mvc.perform(get(GuesserPaths.GAME, id)).andExpect(status().isOk()).andReturn();
    return read(result, GameResponse.class);
  }

  private PressResponse press(String id, int bit) throws Exception {
    MvcResult result =
        mvc.perform(
                post(GuesserPaths.PRESSES, id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new PressRequest(bit))))
            .andExpect(status().isOk())
            .andReturn();
    return read(result, PressResponse.class);
  }

  private <T> T read(MvcResult result, Class<T> type) throws Exception {
    return json.readValue(result.getResponse().getContentAsString(), type);
  }
}
