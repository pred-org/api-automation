package com.pred.apitests.experimental;

import com.pred.apitests.config.KafkaConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Standalone Kafka connectivity test. Run directly:
 *   mvn test -Dtest=com.pred.apitests.experimental.KafkaConnectionTest -pl .
 */
public class KafkaConnectionTest {

    public static void main(String[] args) {
        System.out.println("[KAFKA-TEST] Bootstrap: " + KafkaConfig.getBootstrapServers());
        System.out.println("[KAFKA-TEST] Protocol: " + KafkaConfig.getSecurityProtocol());
        System.out.println("[KAFKA-TEST] Enabled: " + KafkaConfig.isEnabled());

        if (!KafkaConfig.isEnabled()) {
            System.out.println("[KAFKA-TEST] FAILED — Kafka not configured in .env.tracker");
            return;
        }

        Properties props = KafkaConfig.consumerProps("kafka-test-" + UUID.randomUUID());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            System.out.println("[KAFKA-TEST] Consumer created, listing topics...");

            Map<String, List<PartitionInfo>> topics = consumer.listTopics();
            System.out.println("[KAFKA-TEST] SUCCESS — Connected! Found " + topics.size() + " topics");

            // Show non-internal topics
            topics.entrySet().stream()
                    .filter(e -> !e.getKey().startsWith("__"))
                    .filter(e -> !e.getKey().startsWith("order-events-0x"))
                    .forEach(e -> System.out.printf("[KAFKA-TEST]   topic: %-35s partitions: %d%n",
                            e.getKey(), e.getValue().size()));

            // Check one order-events topic
            topics.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("order-events-0x"))
                    .findFirst()
                    .ifPresent(e -> System.out.printf("[KAFKA-TEST]   topic: %-35s partitions: %d (+ %d more order-events-* topics)%n",
                            e.getKey(), e.getValue().size(),
                            topics.keySet().stream().filter(k -> k.startsWith("order-events-0x")).count() - 1));

        } catch (Exception e) {
            System.out.println("[KAFKA-TEST] FAILED — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
