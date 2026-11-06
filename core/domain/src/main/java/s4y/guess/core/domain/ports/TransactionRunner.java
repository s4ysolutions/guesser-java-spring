package s4y.guess.core.domain.ports;

import s4y.guess.core.domain.errors.InfraFailure;

/**
 * Port that runs a piece of work inside one transaction. If the work throws, the transaction
 * is rolled back and the same exception reaches the caller.
 */
public interface TransactionRunner {

  <R, X extends Exception> R inTransaction(String name, Work<R, X> work) throws X, InfraFailure;

  /** The work to run inside a transaction. It may throw a domain error or {@link InfraFailure}. */
  @FunctionalInterface
  interface Work<R, X extends Exception> {
    R run() throws X, InfraFailure;
  }
}
