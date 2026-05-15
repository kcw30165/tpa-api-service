package com.bct.ngtpa.apiservice.adapter.out.redis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "redis-cache")
@Getter
@Setter
public class RedisCacheProperties {

    private boolean enabled = true;
    private String password = "";
    private int timeoutMilliseconds = 1000;

    private final Sentinel sentinel = new Sentinel();
    private final Ssl ssl = new Ssl();
    private final Lettuce lettuce = new Lettuce();

    @Getter
    @Setter
    public static class Sentinel {
        private String master = "ngtpaMaster";
        private List<String> nodes = new ArrayList<>();
        private String password = "";
    }

    @Getter
    @Setter
    public static class Ssl {
        private boolean enabled = true;
        private String bundle = "redis-mtls";
    }

    @Getter
    @Setter
    public static class Lettuce {
        private final Pool pool = new Pool();
        private long shutdownTimeoutMilliseconds = 5000;

        @Getter
        @Setter
        public static class Pool {
            private int maxActive = 20;
            private int maxIdle = 10;
            private int minIdle = 0;
            private long maxWaitMilliseconds = 3000;
        }
    }
}
