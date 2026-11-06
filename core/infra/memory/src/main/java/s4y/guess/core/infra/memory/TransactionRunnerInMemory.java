package s4y.guess.core.infra.memory;

import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.core.domain.ports.TransactionRunner;

/**
 * Test and demo adapter. It runs the work directly, so there is no real transaction and no
 * rollback.
 */
public final class TransactionRunnerInMemory implements TransactionRunner {

  @Override
  public <R, X extends Exception> R inTransaction(String name, Work<R, X> work)
      throws X, InfraFailure {
    return work.run();
  }
}
