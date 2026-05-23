package com.firstclub.membership.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstclub.membership.api.rest.dto.request.SubscribeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for subscription API.
 * Uses Testcontainers PostgreSQL via the 'tc:' JDBC URL in application-test.yml.
 * Requires Docker running OR use the local PostgreSQL.
 *
 * To run: ./gradlew test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Subscription Controller Integration Test")
class SubscriptionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID MONTHLY_SILVER_PLAN_ID =
            UUID.fromString("a1b2c3d4-0001-0001-0001-000000000001");
    private static final UUID MONTHLY_SILVER_TIER_ID =
            UUID.fromString("b1b2c3d4-0001-0001-0001-000000000001");

    @Test
    @DisplayName("GET /api/v1/plans returns active plans list")
    void getPlansReturnsActivePlans() throws Exception {
        mockMvc.perform(get("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans").isArray())
                .andExpect(jsonPath("$.plans.length()").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions: creates subscription for Silver tier")
    void subscribeToSilverTierSucceeds() throws Exception {
        SubscribeRequest request = new SubscribeRequest(
                UUID.randomUUID(),
                MONTHLY_SILVER_PLAN_ID,
                MONTHLY_SILVER_TIER_ID,
                true, null, 0, 0,
                UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").value(request.userId().toString()));
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions: idempotent call returns same result")
    void subscribeIsIdempotent() throws Exception {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        SubscribeRequest request = new SubscribeRequest(
                userId, MONTHLY_SILVER_PLAN_ID, MONTHLY_SILVER_TIER_ID,
                true, null, 0, 0, idempotencyKey);

        // First call
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second call with same idempotency key should return the same subscription (not 409)
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions: second subscribe for same user returns 409")
    void duplicateSubscriptionReturns409() throws Exception {
        UUID userId = UUID.randomUUID();

        SubscribeRequest first = new SubscribeRequest(
                userId, MONTHLY_SILVER_PLAN_ID, MONTHLY_SILVER_TIER_ID,
                true, null, 0, 0, UUID.randomUUID().toString());
        SubscribeRequest second = new SubscribeRequest(
                userId, MONTHLY_SILVER_PLAN_ID, MONTHLY_SILVER_TIER_ID,
                true, null, 0, 0, UUID.randomUUID().toString());

        // First subscribe succeeds
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Second subscribe with different idempotency key returns 409
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/benefits/validate: returns not eligible for non-member")
    void benefitValidationForNonMember() throws Exception {
        String requestBody = """
                {
                    "userId": "%s",
                    "orderId": "%s",
                    "orderValueCents": 50000,
                    "orderCategories": ["dairy"],
                    "deliveryRequested": true
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/benefits/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/subscriptions/me: returns 404 for user with no subscription")
    void getActiveSubscriptionNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }
}
