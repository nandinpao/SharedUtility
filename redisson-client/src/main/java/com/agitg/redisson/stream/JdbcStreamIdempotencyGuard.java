package com.agitg.redisson.stream;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/** PostgreSQL business-side exactly-once EFFECTS when (and only when) business writes
 * use the provided Connection in the SAME transaction and the ledger is never purged early.
 * Redis itself remains at-least-once; this cannot protect HTTP/external side effects.
 */
public final class JdbcStreamIdempotencyGuard {
    @FunctionalInterface
    public interface SqlWork { void execute(Connection connection) throws SQLException; }

    private final DataSource dataSource;
    public JdbcStreamIdempotencyGuard(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }
    public boolean executeOnce(String sourceStream, String sourceGroup, String sourceId,
                               SqlWork work) throws SQLException {
        if (sourceStream == null || sourceStream.isBlank() || sourceGroup == null || sourceGroup.isBlank()
                || sourceId == null || !sourceId.matches("[0-9]+-[0-9]+")) {
            throw new IllegalArgumentException("Invalid durable stream idempotency identity");
        }
        Objects.requireNonNull(work);
        try (Connection connection = dataSource.getConnection()) {
            // Deliberately own the transaction: do not enlist in an unrelated parent transaction.
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO shareutility_stream_delivery_log(stream_key, group_key, message_id) " +
                        "VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
                    statement.setString(1, sourceStream);
                    statement.setString(2, sourceGroup);
                    statement.setString(3, sourceId);
                    inserted = statement.executeUpdate();
                }
                if (inserted == 0) {
                    connection.rollback();
                    return false;
                }
                if (inserted != 1) throw new SQLException("Unexpected idempotency insert count");
                work.execute(connection);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        }
    }
}
