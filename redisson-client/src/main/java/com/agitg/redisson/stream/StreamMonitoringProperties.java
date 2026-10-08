package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Only explicitly selected finite stream/group pairs become Micrometer tag values. */
@ConfigurationProperties(prefix = "redis.stream.monitoring")
public class StreamMonitoringProperties {
    public static class Watch {
        private String stream;
        private String group;
        public String getStream() { return stream; }
        public void setStream(String s) { stream=s; }
        public String getGroup() { return group; }
        public void setGroup(String g) { group=g; }
    }
    private boolean enabled;
    private Duration interval = Duration.ofSeconds(30);
    private int maxWatchedStreams = 20;
    private List<Watch> watch = new ArrayList<>();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean b) { enabled=b; }
    public Duration getInterval() { return interval; }
    public void setInterval(Duration interval) { this.interval=interval; }
    public int getMaxWatchedStreams() { return maxWatchedStreams; }
    public void setMaxWatchedStreams(int max) { maxWatchedStreams=max; }
    public List<Watch> getWatch() { return watch; }
    public void setWatch(List<Watch> watch) { this.watch=watch; }
    public void validate() {
        if (!enabled) return;
        if (interval == null || interval.compareTo(Duration.ofSeconds(5)) < 0 ||
                interval.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("redis.stream.monitoring.interval must be 5s..1h");
        }
        if (maxWatchedStreams < 1 || maxWatchedStreams > 100 || watch == null ||
                watch.isEmpty() || watch.size() > maxWatchedStreams) {
            throw new IllegalArgumentException("invalid redis.stream.monitoring watch size");
        }
        var keys = new HashSet<String>();
        for (var w : watch) {
            if (w == null || w.getStream() == null || w.getGroup() == null ||
                    !w.getStream().matches("[A-Za-z0-9:_.-]{1,128}") ||
                    !w.getGroup().matches("[A-Za-z0-9:_.-]{1,128}")) {
                throw new IllegalArgumentException("invalid monitoring stream/group label");
            }
            if (!keys.add(w.getStream() + "|" + w.getGroup())) {
                throw new IllegalArgumentException("duplicate monitoring watch entry");
            }
        }
    }
}
