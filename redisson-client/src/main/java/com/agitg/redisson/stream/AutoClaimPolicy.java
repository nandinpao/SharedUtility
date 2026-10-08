package com.agitg.redisson.stream;

import java.time.Duration;

/** Immutable XAUTOCLAIM scheduling policy. */
public record AutoClaimPolicy(boolean enabled, Duration idle, Duration interval, int batch, int maxRounds) {
    public static AutoClaimPolicy defaults() {
        return new AutoClaimPolicy(true, Duration.ofSeconds(60), Duration.ofSeconds(5), 100, 5);
    }
    public static AutoClaimPolicy disabled() {
        return new AutoClaimPolicy(false, Duration.ofSeconds(60), Duration.ofSeconds(5), 100, 1);
    }
    /** Compatible fluent builder for existing client source code. */
    public static AutoClaimPolicyBuilder builder() { return new AutoClaimPolicyBuilder(); }
    public static final class AutoClaimPolicyBuilder {
        private boolean enabled;
        private Duration idle;
        private Duration interval;
        private int batch;
        private int maxRounds;
        public AutoClaimPolicyBuilder enabled(boolean value) { this.enabled=value;return this; }
        public AutoClaimPolicyBuilder idle(Duration value) { this.idle=value;return this; }
        public AutoClaimPolicyBuilder interval(Duration value) { this.interval=value;return this; }
        public AutoClaimPolicyBuilder batch(int value) { this.batch=value;return this; }
        public AutoClaimPolicyBuilder maxRounds(int value) { this.maxRounds=value;return this; }
        public AutoClaimPolicy build() { return new AutoClaimPolicy(enabled,idle,interval,batch,maxRounds); }
    }
}
