package com.agitg.database.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import com.agitg.database.RoutingDataSource;
import com.agitg.database.annotation.Master;
import com.agitg.database.annotation.ReadOnly;
import com.agitg.database.annotation.Slave;
import com.agitg.database.annotation.WriteOnly;

/** Runs before the default @Transactional advisor to pick the connection first. */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RoutingConnectionAspect {
    @Around("@annotation(com.agitg.database.annotation.Master) || "
          + "@annotation(com.agitg.database.annotation.ReadOnly) || "
          + "@annotation(com.agitg.database.annotation.Slave) || "
          + "@annotation(com.agitg.database.annotation.WriteOnly)")
    public Object route(ProceedingJoinPoint joinPoint) throws Throwable {
        Method invoked = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Method method = AopUtils.getMostSpecificMethod(invoked, joinPoint.getTarget().getClass());
        Master master = annotation(method, invoked, Master.class);
        ReadOnly readOnly = annotation(method, invoked, ReadOnly.class);
        Slave slave = annotation(method, invoked, Slave.class);
        WriteOnly writeOnly = annotation(method, invoked, WriteOnly.class);
        int count = (master != null ? 1 : 0) + (readOnly != null ? 1 : 0)
                + (slave != null ? 1 : 0) + (writeOnly != null ? 1 : 0);
        if (count != 1) {
            throw new IllegalStateException("Exactly one routing annotation is required on " + method
                    + "; found " + count);
        }
        RoutingDataSource.Scope scope;
        if (master != null) {
            scope = RoutingDataSource.writeScope(master.value());
        } else if (slave != null) {
            if (slave.value().isBlank()) {
                throw new IllegalArgumentException("@Slave requires a non-blank datasource name");
            }
            scope = RoutingDataSource.readScope(slave.value());
        } else if (readOnly != null) {
            scope = RoutingDataSource.readScope(null);
        } else {
            // @WriteOnly used to select a random writer; multi-writer is forbidden now.
            scope = RoutingDataSource.writeScope(null);
        }
        try (scope) {
            return joinPoint.proceed();
        }
    }

    private static <T extends java.lang.annotation.Annotation> T annotation(
            Method specific, Method invoked, Class<T> type) {
        T match = AnnotatedElementUtils.findMergedAnnotation(specific, type);
        return match != null ? match : AnnotatedElementUtils.findMergedAnnotation(invoked, type);
    }
}
