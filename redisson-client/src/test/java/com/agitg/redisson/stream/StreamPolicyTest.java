package com.agitg.redisson.stream;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreamPolicyTest {
    @Test void invalidMaxDeliveriesFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new StreamRetryPolicy(0, ":dlq"));
        assertThrows(IllegalArgumentException.class, () -> new StreamRetryPolicy(-3, ":dlq"));
    }
    @Test void invalidDeadLetterSuffixFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new StreamRetryPolicy(2, ""));
        assertThrows(IllegalArgumentException.class, () -> new StreamRetryPolicy(2, null));
    }
    @Test void invalidConfigurationFailsAtBeanConstruction() {
        var props = new StreamRetryProperties();
        props.setMaxDeliveries(0);
        assertThrows(IllegalArgumentException.class, props::toPolicy);
    }
    @Test void partialConsumerOverrideInheritsAndDoesNotDisableAutoClaim() {
        var props = new AutoClaimProperties();
        var change = new AutoClaimProperties.PolicyProps();
        change.setIdle(Duration.ofSeconds(12));
        props.getByConsumer().put("c", change);
        var result = new AutoClaimPolicyResolver(props).resolve(
                new StreamConsumerConfig("orders", "g", "c", true, false), null);
        assertTrue(result.enabled());
        assertEquals(Duration.ofSeconds(12), result.idle());
        assertEquals(Duration.ofSeconds(5), result.interval());
        assertEquals(100, result.batch());
    }
    @Test void precedenceStreamThenConsumerThenExplicitOverride() {
        var props = new AutoClaimProperties();
        var streamChange = new AutoClaimProperties.PolicyProps();
        streamChange.setBatch(7);
        props.getByStream().put("orders", streamChange);
        var consumerChange = new AutoClaimProperties.PolicyProps();
        consumerChange.setBatch(4);
        props.getByConsumer().put("c", consumerChange);
        var resolver = new AutoClaimPolicyResolver(props);
        var config = new StreamConsumerConfig("orders", "g", "c", true, false);
        assertEquals(4, resolver.resolve(config, null).batch());
        assertEquals(2, resolver.resolve(config,
                new AutoClaimPolicy(true, Duration.ofSeconds(2), Duration.ofSeconds(1), 2, 2)).batch());
    }
    @Test void globallyDisabledAutoClaimCanBeOverriddenByConsumer() {
        var props = new AutoClaimProperties();
        props.getDefaults().setEnabled(false);
        var cfg = new StreamConsumerConfig("orders", "g", "c", true, false);
        var resolver = new AutoClaimPolicyResolver(props);
        assertFalse(resolver.resolve(cfg, null).enabled());
        var byConsumer = new AutoClaimProperties.PolicyProps();
        byConsumer.setEnabled(true);
        props.getByConsumer().put("c", byConsumer);
        assertTrue(resolver.resolve(cfg, null).enabled());
    }
}
