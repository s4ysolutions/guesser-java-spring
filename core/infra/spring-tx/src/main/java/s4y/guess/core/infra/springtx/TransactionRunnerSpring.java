package s4y.guess.core.infra.springtx;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.core.domain.ports.TransactionRunner;

/**
 * Production adapter that uses Spring transactions. A checked exception from the work rolls the
 * transaction back and is then thrown again unchanged. Spring transaction errors and database
 * errors that escape are turned into {@link InfraFailure}.
 */
public final class TransactionRunnerSpring implements TransactionRunner {

  private final PlatformTransactionManager manager;

  public TransactionRunnerSpring(PlatformTransactionManager manager) {
    this.manager = manager;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R, X extends Exception> R inTransaction(String name, Work<R, X> work)
      throws X, InfraFailure {
    TransactionTemplate template = new TransactionTemplate(manager);
    template.setName(name);
    try {
      return template.execute(
          status -> {
            try {
              return work.run();
            } catch (RuntimeException e) {
              throw e;
            } catch (Exception e) {
              // Spring rolls back only on unchecked exceptions, so carry the checked one.
              throw new Carrier(e);
            }
          });
    } catch (Carrier carrier) {
      Throwable cause = carrier.getCause();
      if (cause instanceof InfraFailure infraFailure) {
        throw infraFailure;
      }
      throw (X) cause;
    } catch (TransactionException e) {
      throw new InfraFailure("Transaction '" + name + "' failed", e);
    } catch (DataAccessException e) {
      // A database error that was not mapped inside the work, for example one thrown at commit.
      throw new InfraFailure("Transaction '" + name + "' failed in the database", e);
    }
  }

  /** Carries a checked exception through the Spring callback. */
  private static final class Carrier extends RuntimeException {
    Carrier(Exception cause) {
      super(cause);
    }
  }
}
