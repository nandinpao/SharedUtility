package com.agitg.redisson.stream;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcStreamIdempotencyGuardTest {
    @Test void processedMessageAndLedgerCommitTogether() throws Exception {
        var data = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(data.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        var invoked = new AtomicInteger();
        assertTrue(new JdbcStreamIdempotencyGuard(data).executeOnce("order", "g", "4-0", c -> {
            assertSame(connection, c); invoked.incrementAndGet();
        }));
        assertEquals(1, invoked.get());
        verify(connection).commit();
        verify(connection, never()).rollback();
    }
    @Test void duplicateNeverExecutesBusinessSideEffect() throws Exception {
        var data = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(data.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        assertFalse(new JdbcStreamIdempotencyGuard(data).executeOnce("order", "g", "4-0",
                c -> fail("duplicate was executed")));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
    @Test void businessFailureRollsBackLedgerEntry() throws Exception {
        var data = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(data.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        assertThrows(SQLException.class, () -> new JdbcStreamIdempotencyGuard(data).executeOnce(
                "order", "g", "4-0", c -> { throw new SQLException("failed business write"); }));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
