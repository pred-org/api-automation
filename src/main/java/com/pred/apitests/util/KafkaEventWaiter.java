package com.pred.apitests.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pred.apitests.config.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class KafkaEventWaiter implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaEventWaiter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private boolean ready = false;

    /**
     * Creates a waiter and subscribes to the topic immediately.
     * Call this BEFORE performing the action (place order, cancel, etc.)
     */
    private KafkaEventWaiter(String topic) {
        this.topic = topic;
        String groupId = "api-test-" + UUID.randomUUID();
        this.consumer = new KafkaConsumer<>(KafkaConfig.consumerProps(groupId));
        this.consumer.subscribe(Collections.singletonList(topic));
        // Wait for partition assignment (MSK can be slow)
        Set<TopicPartition> assignment = Collections.emptySet();
        long assignDeadline = System.currentTimeMillis() + 10_000;
        while (assignment.isEmpty() && System.currentTimeMillis() < assignDeadline) {
            this.consumer.poll(Duration.ofMillis(1000));
            assignment = this.consumer.assignment();
        }
        if (assignment.isEmpty()) {
            LOG.warn("[KAFKA] No partitions assigned for '{}' after 10s — consumer may not receive events", topic);
            this.ready = false;
            return;
        }
        // Seek to end so we only see NEW events
        this.consumer.seekToEnd(assignment);
        this.consumer.poll(Duration.ofMillis(500));
        this.ready = true;
        LOG.info("[KAFKA] Listener ready on topic '{}' ({} partitions)", topic, assignment.size());
    }

    /**
     * Start listening on a topic. Call BEFORE performing the action.
     * Returns null if Kafka is not configured (tests fall back to polling).
     */
    public static KafkaEventWaiter startListening(String topic) {
        if (!KafkaConfig.isEnabled()) {
            LOG.info("[KAFKA] Kafka not configured, skipping listener for '{}'", topic);
            return null;
        }
        try {
            return new KafkaEventWaiter(topic);
        } catch (Exception e) {
            LOG.warn("[KAFKA] Failed to start listener on '{}': {}", topic, e.getMessage());
            return null;
        }
    }

    /**
     * Wait for a matching event. Call AFTER performing the action.
     * Returns the matching JsonNode, or null on timeout.
     */
    public JsonNode awaitEvent(Predicate<JsonNode> matcher, long timeoutMs) {
        if (!ready) return null;
        long deadline = System.currentTimeMillis() + timeoutMs;
        LOG.info("[KAFKA] Awaiting event on '{}' (timeout {}ms)", topic, timeoutMs);

        while (System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(Math.min(remaining, 500)));
            for (ConsumerRecord<String, String> record : records) {
                try {
                    JsonNode node = MAPPER.readTree(record.value());
                    LOG.info("[KAFKA] Event received on '{}': event_type={}, order_id={}, status={}",
                            topic,
                            node.path("event_type").asText("N/A"),
                            node.path("order_id").asText("N/A"),
                            node.path("status").asText("N/A"));
                    if (matcher.test(node)) {
                        LOG.info("[KAFKA] Matched event: {}",
                                node.has("event_type") ? node.get("event_type").asText() : "match");
                        return node;
                    }
                } catch (Exception e) {
                    // skip unparseable
                }
            }
        }
        LOG.warn("[KAFKA] Timeout on '{}' after {}ms", topic, timeoutMs);
        return null;
    }

    @Override
    public void close() {
        try {
            consumer.close(Duration.ofSeconds(2));
        } catch (Exception e) {
            // ignore
        }
    }
}
