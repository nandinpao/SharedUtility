package com.agitg.database.aspect;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import com.agitg.database.RoutingDataSource;
import com.agitg.database.annotation.Master;
import com.agitg.database.annotation.ReadOnly;
import com.agitg.database.annotation.WriteOnly;

class RoutingConnectionAspectTest {
    static class Sample {
        @ReadOnly public String read() { return "reader"; }
        @Master public String write() { return "writer"; }
        @ReadOnly @WriteOnly public String conflicting() { return "invalid"; }
    }

    private Object invoke(String methodName, org.mockito.stubbing.Answer<Object> answer) throws Throwable {
        Method method = Sample.class.getMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(signature);
        when(jp.getTarget()).thenReturn(new Sample());
        when(jp.proceed()).thenAnswer(answer);
        return new RoutingConnectionAspect().route(jp);
    }

    @Test void innerCallDoesNotDestroyOuterContext() throws Throwable {
        try (var outer = RoutingDataSource.readScope("replica")) {
            Object result = invoke("write", call -> {
                assertEquals(RoutingDataSource.Mode.WRITE, RoutingDataSource.currentRoute().mode());
                return "worked";
            });
            assertEquals("worked", result);
            assertEquals("replica", RoutingDataSource.currentRoute().preferred());
        }
    }

    @Test void exceptionsLeaveNoScopeBehind() {
        assertThrows(IllegalArgumentException.class, () ->
                invoke("read", call -> { throw new IllegalArgumentException("failure"); }));
        assertEquals(RoutingDataSource.Mode.WRITE, RoutingDataSource.currentRoute().mode());
    }

    @Test void conflictingAnnotationsRejectedBeforeCallingTarget() {
        assertThrows(IllegalStateException.class, () -> invoke("conflicting", call -> "unexpected"));
    }
}
