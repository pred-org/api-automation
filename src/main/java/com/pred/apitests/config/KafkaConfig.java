package com.pred.apitests.config;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConfig {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConfig.class);
    private static final Map<String, String> ENV = new HashMap<>();

    static {
        loadEnvFile(".env.tracker");
    }

    private static void loadEnvFile(String filename) {
        Path path = Path.of(System.getProperty("user.dir"), filename);
        if (!Files.exists(path)) return;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String normalized = trimmed.startsWith("export ") ? trimmed.substring(7).trim() : trimmed;
                int eq = normalized.indexOf('=');
                if (eq > 0) {
                    String key = normalized.substring(0, eq).trim();
                    String value = normalized.substring(eq + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\""))
                        value = value.substring(1, value.length() - 1);
                    ENV.put(key, value);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to load {}: {}", filename, e.getMessage());
        }
    }

    public static String getBootstrapServers() {
        String val = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (val == null || val.isBlank()) val = ENV.get("KAFKA_BOOTSTRAP_SERVERS");
        return val;
    }

    public static String getSecurityProtocol() {
        String val = System.getenv("KAFKA_SECURITY_PROTOCOL");
        if (val == null || val.isBlank()) val = ENV.get("KAFKA_SECURITY_PROTOCOL");
        return val != null ? val : "SSL";
    }

    public static boolean isEnabled() {
        return getBootstrapServers() != null && !getBootstrapServers().isBlank();
    }

    public static Properties consumerProps(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
        props.put("security.protocol", getSecurityProtocol());
        // MSK SSL settings
        props.put("ssl.endpoint.identification.algorithm", "");  // disable hostname verification for MSK
        props.put("request.timeout.ms", "15000");
        props.put("session.timeout.ms", "10000");
        props.put("default.api.timeout.ms", "15000");
        return props;
    }
}
