package com.sentinel.integration.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.integration.adapter.out.persistence.WebhookDeliveryRepository;
import com.sentinel.integration.adapter.out.persistence.WebhookSubscriptionRepository;
import com.sentinel.integration.domain.model.WebhookDelivery;
import com.sentinel.integration.domain.model.WebhookSubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Outbound event delivery.
 *
 * <p>Deliveries are persisted before they are attempted, retried with
 * exponential backoff, and signed with an HMAC the receiver can verify. The
 * persistence matters more than it looks: an integration that drops an event
 * on a transient network failure is an integration the client cannot build a
 * workflow on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final int MAX_ATTEMPTS = 6;
    /** Consecutive failures past this disable the endpoint instead of retrying forever. */
    private static final int FAILURE_DISABLE_THRESHOLD = 20;

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> subscribe(UUID orgId, String name, String url, List<String> events) {
        String secret = "whsec_" + randomToken();

        WebhookSubscription subscription = subscriptionRepository.save(WebhookSubscription.builder()
                .organizationId(orgId)
                .name(name)
                .targetUrl(url)
                .events(events == null ? List.of() : events)
                .secretHash(sha256(secret))
                .active(true)
                .failureCount(0)
                .build());

        // The plaintext secret is returned once and never stored.
        Map<String, Object> response = new HashMap<>();
        response.put("id", subscription.getId());
        response.put("name", subscription.getName());
        response.put("targetUrl", subscription.getTargetUrl());
        response.put("events", subscription.getEvents());
        response.put("secret", secret);
        response.put("note", "Store this secret now. It is not recoverable.");
        return response;
    }

    /** Queues an event for every subscriber that asked for it. */
    @Transactional
    public int publish(UUID orgId, String eventType, Object payload) {
        String body = serialise(eventType, payload);
        int queued = 0;

        for (WebhookSubscription subscription : subscriptionRepository.findByOrganizationIdAndActiveTrue(orgId)) {
            if (!subscription.getEvents().isEmpty() && !subscription.getEvents().contains(eventType)) continue;
            deliveryRepository.save(WebhookDelivery.builder()
                    .organizationId(orgId)
                    .subscriptionId(subscription.getId())
                    .eventType(eventType)
                    .payload(body)
                    .status("PENDING")
                    .attempts(0)
                    .nextAttemptAt(Instant.now())
                    .build());
            queued++;
        }
        return queued;
    }

    /** Drains the queue. Backoff is 2^attempts minutes, capped by MAX_ATTEMPTS. */
    @Scheduled(fixedDelayString = "${sentinel.webhooks.sweep-ms:30000}")
    @Transactional
    public void deliverPending() {
        List<WebhookDelivery> due = deliveryRepository.findByStatusAndNextAttemptAtBefore("PENDING", Instant.now());
        for (WebhookDelivery delivery : due) {
            subscriptionRepository.findById(delivery.getSubscriptionId())
                    .ifPresent(subscription -> attempt(delivery, subscription));
        }
    }

    private void attempt(WebhookDelivery delivery, WebhookSubscription subscription) {
        delivery.setAttempts(delivery.getAttempts() + 1);
        try {
            HttpStatusCode status = RestClient.create().post()
                    .uri(subscription.getTargetUrl())
                    .header("Content-Type", "application/json")
                    .header("X-Sentinel-Event", delivery.getEventType())
                    .header("X-Sentinel-Delivery", delivery.getId().toString())
                    .header("X-Sentinel-Signature", sign(delivery.getPayload(), subscription.getSecretHash()))
                    .body(delivery.getPayload())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode();

            delivery.setResponseCode(status.value());
            delivery.setStatus("DELIVERED");
            delivery.setDeliveredAt(Instant.now());
            subscription.setFailureCount(0);
            subscription.setLastDeliveryAt(Instant.now());
        } catch (Exception e) {
            delivery.setErrorMessage(e.getMessage());
            if (delivery.getAttempts() >= MAX_ATTEMPTS) {
                delivery.setStatus("ABANDONED");
            } else {
                delivery.setStatus("PENDING");
                delivery.setNextAttemptAt(Instant.now().plus(
                        (long) Math.pow(2, delivery.getAttempts()), ChronoUnit.MINUTES));
            }
            subscription.setFailureCount(subscription.getFailureCount() + 1);
            if (subscription.getFailureCount() >= FAILURE_DISABLE_THRESHOLD) {
                subscription.setActive(false);
                subscription.setDisabledReason("Disabled after " + subscription.getFailureCount()
                        + " consecutive delivery failures");
                log.warn("Webhook {} disabled after repeated failures", subscription.getId());
            }
        }
        subscriptionRepository.save(subscription);
        deliveryRepository.save(delivery);
    }

    @Transactional
    public WebhookSubscription setActive(UUID orgId, UUID id, boolean active) {
        WebhookSubscription subscription = subscriptionRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookSubscription", id.toString()));
        subscription.setActive(active);
        if (active) {
            subscription.setFailureCount(0);
            subscription.setDisabledReason(null);
        }
        return subscriptionRepository.save(subscription);
    }

    private String serialise(String eventType, Object payload) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event", eventType);
            envelope.put("timestamp", Instant.now().toString());
            envelope.put("data", payload);
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            return "{\"event\":\"" + eventType + "\"}";
        }
    }

    private String sign(String body, String secretHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretHash.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + hex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private String sha256(String value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return hex(bytes);
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
