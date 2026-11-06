package s4y.guess.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.core.infra.memory.TransactionRunnerInMemory;
import s4y.guess.guesser.contract.GameResponse;
import s4y.guess.guesser.contract.GuesserPaths;
import s4y.guess.guesser.contract.PressRequest;
import s4y.guess.guesser.contract.PressResponse;
import s4y.guess.guesser.contract.StartGameRequest;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;
import s4y.guess.guesser.infra.memory.GameRepositoryInMemory;
import s4y.guess.guesser.infra.memory.GuessGatewayScripted;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts the server with test adapters: games in memory, no real transactions, and a gateway
 * that answers from a script. The property {@code guess.gateway=none} switches both production
 * gateways off, so the scripted one is the only {@code GuessGateway}. The test repository and
 * transaction runner are {@code @Primary}, so they win over the production beans.
 */
@SpringBootTest(properties = "guess.gateway=none")
@AutoConfigureMockMvc
class GuessServerTestWiringTest {

  @TestConfiguration
  static class FakeAdapters {

    @Bean
    @Primary
    GameRepository gameRepositoryInMemory() {
      return new GameRepositoryInMemory();
    }

    @Bean
    @Primary
    TransactionRunner transactionRunnerInMemory() {
      return new TransactionRunnerInMemory();
    }

    @Bean
    GuessGatewayScripted guessGatewayScripted() {
      return new GuessGatewayScripted()
          .then(Guess.of(Bit.ONE))
          .thenFail(GuessFailed.Reason.TIMEOUT, "slow");
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired GuessGatewayScripted scripted;

  @Test
  void followsTheScript() throws Exception {
    GameResponse game = startGame();

    PressResponse first = press(game.id(), 1);
    PressResponse second = press(game.id(), 1);
    PressResponse third = press(game.id(), 0);

    assertThat(first.round().guess()).isNull();
    assertThat(first.round().hit()).isNull();
    assertThat(first.guessFailure()).isNull();

    assertThat(second.round().bit()).isEqualTo(1);
    assertThat(second.round().guess()).isEqualTo(1);
    assertThat(second.round().hit()).isTrue();
    assertThat(second.guessFailure()).isNull();

    assertThat(third.round().bit()).isZero();
    assertThat(third.round().guess()).isNull();
    assertThat(third.round().hit()).isNull();
    assertThat(third.guessFailure()).isEqualTo("TIMEOUT: slow");

    assertThat(third.score().hits()).isEqualTo(1);
    assertThat(third.score().guessed()).isEqualTo(1);
    assertThat(third.score().total()).isEqualTo(3);

    assertThat(scripted.inputs())
        .containsExactly(
            new Bits(List.of(Bit.ONE)), new Bits(List.of(Bit.ONE, Bit.ONE)));
  }

  private GameResponse startGame() throws Exception {
    MvcResult result =
        mvc.perform(
                post(GuesserPaths.GAMES)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new StartGameRequest(null))))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readValue(result.getResponse().getContentAsString(), GameResponse.class);
  }

  private PressResponse press(String id, int bit) throws Exception {
    MvcResult result =
        mvc.perform(
                post(GuesserPaths.PRESSES, id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new PressRequest(bit))))
            .andExpect(status().isOk())
            .andReturn();
    return json.readValue(result.getResponse().getContentAsString(), PressResponse.class);
  }
}
