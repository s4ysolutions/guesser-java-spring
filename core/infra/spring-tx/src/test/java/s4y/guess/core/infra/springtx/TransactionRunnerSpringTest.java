package s4y.guess.core.infra.springtx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import s4y.guess.core.domain.errors.InfraFailure;

class TransactionRunnerSpringTest {

  /** A checked domain-like error, to prove that the same instance comes back. */
  private static final class SomeChecked extends Exception {
    SomeChecked(String message) {
      super(message);
    }
  }

  private static class CountingManager implements PlatformTransactionManager {
    int commits;
    int rollbacks;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      commits++;
    }

    @Override
    public void rollback(TransactionStatus status) {
      rollbacks++;
    }
  }

  /** A manager whose commit fails with a database error, like a deferred constraint would. */
  private static final class FailingCommitManager extends CountingManager {
    @Override
    public void commit(TransactionStatus status) {
      throw new DataRetrievalFailureException("commit failed");
    }
  }

  private final CountingManager manager = new CountingManager();
  private final TransactionRunnerSpring runner = new TransactionRunnerSpring(manager);

  @Test
  void commitsAndReturnsTheResultOnSuccess() throws Exception {
    String result = runner.inTransaction("ok", () -> "done");

    assertThat(result).isEqualTo("done");
    assertThat(manager.commits).isEqualTo(1);
    assertThat(manager.rollbacks).isZero();
  }

  @Test
  void rollsBackAndRethrowsTheSameCheckedException() {
    SomeChecked expected = new SomeChecked("boom");

    assertThatThrownBy(
            () ->
                runner.inTransaction(
                    "checked",
                    () -> {
                      throw expected;
                    }))
        .isSameAs(expected);

    assertThat(manager.rollbacks).isEqualTo(1);
    assertThat(manager.commits).isZero();
  }

  @Test
  void rollsBackAndPassesInfraFailureThrough() {
    InfraFailure expected = new InfraFailure("db down");

    assertThatThrownBy(
            () ->
                runner.inTransaction(
                    "infra",
                    () -> {
                      throw expected;
                    }))
        .isSameAs(expected);

    assertThat(manager.rollbacks).isEqualTo(1);
    assertThat(manager.commits).isZero();
  }

  @Test
  void rollsBackOnRuntimeException() {
    IllegalStateException expected = new IllegalStateException("bug");

    assertThatThrownBy(
            () ->
                runner.inTransaction(
                    "runtime",
                    () -> {
                      throw expected;
                    }))
        .isSameAs(expected);

    assertThat(manager.rollbacks).isEqualTo(1);
    assertThat(manager.commits).isZero();
  }

  @Test
  void mapsDatabaseErrorAtCommitToInfraFailure() {
    TransactionRunnerSpring failing = new TransactionRunnerSpring(new FailingCommitManager());

    assertThatThrownBy(() -> failing.inTransaction("commit", () -> "done"))
        .isInstanceOf(InfraFailure.class)
        .hasMessageContaining("commit")
        .hasCauseInstanceOf(DataAccessException.class);
  }

  @Test
  void mapsDatabaseErrorThrownByTheWorkToInfraFailureAfterRollback() {
    assertThatThrownBy(
            () ->
                runner.inTransaction(
                    "work",
                    () -> {
                      throw new DataRetrievalFailureException("read failed");
                    }))
        .isInstanceOf(InfraFailure.class)
        .hasCauseInstanceOf(DataAccessException.class);

    assertThat(manager.rollbacks).isEqualTo(1);
    assertThat(manager.commits).isZero();
  }
}
