package com.example.mqstub.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Config {
    public static class TLSConfig {
        public boolean enabled = false;
        public String cipher;
        public String truststorePath;
        public String truststorePassword;
        public String keystorePath;
        public String keystorePassword;
    }

    public static class BrokerConfig {
        public String name;
        public String queueManager;
        public String channel;
        public String connectionName;
        public String user;
        public String password;
        public TLSConfig tls = new TLSConfig();
    }

    public static class Endpoint {
        public String broker;
        public String queue;
        public String user;      // optional per-endpoint override
        public String password;  // optional per-endpoint override
    }

    public static class RouteConfig {
        public Endpoint source;
        public Endpoint target;
        public Integer waitIntervalMs = 2000;
        public Integer idleSleepMs = 500;
    }

    public static class AppConfig {
        public List<BrokerConfig> brokers;
        public List<RouteConfig> routes;
        public String logLevel = "INFO";
    }

    public static AppConfig load(String path) throws Exception {
        try (InputStream is = Files.newInputStream(Path.of(path))) {
            Yaml yaml = new Yaml();
            Map<String, Object> raw = yaml.load(is);
            if (raw == null) throw new IllegalArgumentException("Empty config");
            AppConfig cfg = new AppConfig();
            // brokers
            Object brokersObj = raw.get("brokers");
            if (!(brokersObj instanceof List)) throw new IllegalArgumentException("'brokers' must be list");
            cfg.brokers = ((List<?>) brokersObj).stream().map(o -> parseBroker((Map<String,Object>) o)).toList();
            if (cfg.brokers.isEmpty()) throw new IllegalArgumentException("'brokers' is empty");
            // routes
            Object routesObj = raw.get("routes");
            if (!(routesObj instanceof List)) throw new IllegalArgumentException("'routes' must be list");
            cfg.routes = ((List<?>) routesObj).stream().map(o -> parseRoute((Map<String,Object>) o)).toList();
            if (cfg.routes.isEmpty()) throw new IllegalArgumentException("'routes' is empty");
            // log level
            Object ll = raw.getOrDefault("log_level", raw.getOrDefault("logLevel", "INFO"));
            cfg.logLevel = String.valueOf(ll).toUpperCase();
            // basic referential check
            var names = cfg.brokers.stream().map(b -> b.name).collect(java.util.stream.Collectors.toSet());
            for (RouteConfig r : cfg.routes) {
                if (!names.contains(r.source.broker)) throw new IllegalArgumentException("Unknown source broker: " + r.source.broker);
                if (!names.contains(r.target.broker)) throw new IllegalArgumentException("Unknown target broker: " + r.target.broker);
            }
            return cfg;
        }
    }

    private static BrokerConfig parseBroker(Map<String, Object> m) {
        BrokerConfig b = new BrokerConfig();
        b.name = req(m, "name");
        b.queueManager = req(m, "queue_manager", "queueManager");
        b.channel = req(m, "channel");
        b.connectionName = req(m, "connection_name", "connectionName");
        b.user = opt(m, "user");
        b.password = opt(m, "password");
        Object tlsObj = m.get("tls");
        if (tlsObj instanceof Map<?, ?> tm) {
            TLSConfig t = new TLSConfig();
            t.enabled = Boolean.parseBoolean(String.valueOf(tm.getOrDefault("enabled", false)));
            t.cipher = sopt(tm, "cipher");
            t.truststorePath = sopt(tm, "truststore", "truststorePath");
            t.truststorePassword = sopt(tm, "truststore_password", "truststorePassword");
            t.keystorePath = sopt(tm, "keystore", "keystorePath");
            t.keystorePassword = sopt(tm, "keystore_password", "keystorePassword");
            b.tls = t;
        }
        return b;
    }

    private static RouteConfig parseRoute(Map<String, Object> m) {
        RouteConfig r = new RouteConfig();
        Map<String,Object> src = (Map<String, Object>) (m.containsKey("from") ? m.get("from") : m.get("source"));
        Map<String,Object> dst = (Map<String, Object>) (m.containsKey("to") ? m.get("to") : m.get("target"));
        r.source = parseEndpoint(src);
        r.target = parseEndpoint(dst);
        r.waitIntervalMs = toInt(m.getOrDefault("wait_interval_ms", m.getOrDefault("waitIntervalMs", 2000)));
        r.idleSleepMs = toInt(m.getOrDefault("idle_sleep_ms", m.getOrDefault("idleSleepMs", 500)));
        return r;
    }

    private static Endpoint parseEndpoint(Map<String, Object> m) {
        Endpoint e = new Endpoint();
        e.broker = req(m, "broker");
        e.queue = req(m, "queue");
        e.user = opt(m, "user");
        e.password = opt(m, "password");
        return e;
    }

    private static String req(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return String.valueOf(v);
        }
        throw new IllegalArgumentException("Missing required field: " + String.join("/", keys));
    }
    private static String opt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }
    private static String sopt(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return String.valueOf(v);
        }
        return null;
    }
    private static int toInt(Object o) { return Integer.parseInt(String.valueOf(o)); }
}