package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** redis.stream.autoclaim defaults and per-stream/per-consumer partial overlays. */
@ConfigurationProperties(prefix = "redis.stream.autoclaim")
public class AutoClaimProperties {
    private PolicyProps defaults = PolicyProps.defaultValues();
    private Map<String, PolicyProps> byConsumer = new HashMap<>();
    private Map<String, PolicyProps> byStream = new HashMap<>();

    public PolicyProps getDefaults() { return defaults; }
    public void setDefaults(PolicyProps defaults) { this.defaults = defaults; }
    public Map<String, PolicyProps> getByConsumer() { return byConsumer; }
    public void setByConsumer(Map<String, PolicyProps> byConsumer) { this.byConsumer = byConsumer; }
    public Map<String, PolicyProps> getByStream() { return byStream; }
    public void setByStream(Map<String, PolicyProps> byStream) { this.byStream = byStream; }

    public static class PolicyProps {
        private Boolean enabled;
        private Duration idle;
        private Duration interval;
        private Integer batch;
        private Integer maxRounds;
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Duration getIdle() { return idle; }
        public void setIdle(Duration idle) { this.idle = idle; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
        public Integer getBatch() { return batch; }
        public void setBatch(Integer batch) { this.batch = batch; }
        public Integer getMaxRounds() { return maxRounds; }
        public void setMaxRounds(Integer maxRounds) { this.maxRounds = maxRounds; }
        public static PolicyProps defaultValues() {
            var p = new PolicyProps();
            p.enabled = true;
            p.idle = Duration.ofSeconds(60);
            p.interval = Duration.ofSeconds(5);
            p.batch = 100;
            p.maxRounds = 5;
            return p;
        }
        public AutoClaimPolicy toPolicy() {
            AutoClaimPolicy def = AutoClaimPolicy.defaults();
            return new AutoClaimPolicy(enabled == null ? def.enabled() : enabled,
                    idle == null ? def.idle() : idle,
                    interval == null ? def.interval() : interval,
                    batch == null ? def.batch() : batch,
                    maxRounds == null ? def.maxRounds() : maxRounds);
        }
    }
}
