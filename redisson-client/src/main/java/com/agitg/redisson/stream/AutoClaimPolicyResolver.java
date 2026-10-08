package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.lang.Nullable;

/** Resolution: global defaults -> stream overlay -> consumer overlay -> explicit override. */
public class AutoClaimPolicyResolver {
    private final AutoClaimProperties props;
    public AutoClaimPolicyResolver(AutoClaimProperties props) {
        this.props = Objects.requireNonNull(props);
    }

    public AutoClaimPolicy resolve(StreamConsumerConfig cfg, @Nullable AutoClaimPolicy override) {
        AutoClaimPolicy result = AutoClaimPolicy.defaults();
        result = merge(result, props.getDefaults());
        Map<String, AutoClaimProperties.PolicyProps> streams = props.getByStream();
        if (streams != null) result = merge(result, streams.get(cfg.getStreamKey()));
        Map<String, AutoClaimProperties.PolicyProps> consumers = props.getByConsumer();
        if (consumers != null) result = merge(result, consumers.get(cfg.getConsumer()));
        return override == null ? result : normalize(override);
    }

    private AutoClaimPolicy merge(AutoClaimPolicy base, AutoClaimProperties.PolicyProps change) {
        if (change == null) return base;
        return normalize(new AutoClaimPolicy(
                change.getEnabled() == null ? base.enabled() : change.getEnabled(),
                change.getIdle() == null ? base.idle() : change.getIdle(),
                change.getInterval() == null ? base.interval() : change.getInterval(),
                change.getBatch() == null ? base.batch() : change.getBatch(),
                change.getMaxRounds() == null ? base.maxRounds() : change.getMaxRounds()));
    }

    private AutoClaimPolicy normalize(AutoClaimPolicy p) {
        AutoClaimPolicy def = AutoClaimPolicy.defaults();
        Duration idle = valid(p.idle()) ? p.idle() : def.idle();
        Duration interval = valid(p.interval()) ? p.interval() : def.interval();
        return new AutoClaimPolicy(p.enabled(), idle, interval,
                p.batch() > 0 ? p.batch() : def.batch(),
                p.maxRounds() > 0 ? p.maxRounds() : def.maxRounds());
    }

    private static boolean valid(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
