package com.firstclub.membership.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.firstclub.membership.domain.rule.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Eligibility Rules")
class EligibilityRuleTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("OrderCountRule")
    class OrderCountRuleTests {

        private final OrderCountRule rule = new OrderCountRule();

        @Test
        @DisplayName("passes when order count meets minimum")
        void passesWhenMet() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("minOrders", 5);
            EligibilityContext ctx = new EligibilityContext(
                    UUID.randomUUID(), 10, 0, null, null);
            assertThat(rule.evaluate(ctx, config).isEligible()).isTrue();
        }

        @Test
        @DisplayName("passes when order count exactly equals minimum")
        void passesWhenExact() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("minOrders", 5);
            EligibilityContext ctx = new EligibilityContext(
                    UUID.randomUUID(), 5, 0, null, null);
            assertThat(rule.evaluate(ctx, config).isEligible()).isTrue();
        }

        @Test
        @DisplayName("fails when order count below minimum")
        void failsWhenBelowMinimum() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("minOrders", 10);
            EligibilityContext ctx = new EligibilityContext(
                    UUID.randomUUID(), 3, 0, null, null);
            EligibilityResult result = rule.evaluate(ctx, config);
            assertThat(result.isEligible()).isFalse();
            assertThat(result.getReason()).contains("3").contains("10");
        }

        @Test
        @DisplayName("ruleType is ORDER_COUNT")
        void ruleType() {
            assertThat(rule.ruleType()).isEqualTo("ORDER_COUNT");
        }
    }

    @Nested
    @DisplayName("OrderValueRule")
    class OrderValueRuleTests {

        private final OrderValueRule rule = new OrderValueRule();

        @Test
        @DisplayName("passes when order value meets minimum")
        void passesWhenMet() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("minValueCents", 50000);
            EligibilityContext ctx = new EligibilityContext(
                    UUID.randomUUID(), 0, 100000, null, null);
            assertThat(rule.evaluate(ctx, config).isEligible()).isTrue();
        }

        @Test
        @DisplayName("fails when order value below minimum")
        void failsWhenBelowMinimum() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("minValueCents", 100000);
            EligibilityContext ctx = new EligibilityContext(
                    UUID.randomUUID(), 0, 50000, null, null);
            assertThat(rule.evaluate(ctx, config).isEligible()).isFalse();
        }

        @Test
        @DisplayName("ruleType is ORDER_VALUE")
        void ruleType() {
            assertThat(rule.ruleType()).isEqualTo("ORDER_VALUE");
        }
    }

    @Nested
    @DisplayName("CohortRule")
    class CohortRuleTests {

        private final CohortRule rule = new CohortRule();

        @Test
        @DisplayName("passes when user is in allowed cohort")
        void passesWhenInCohort() throws Exception {
            String configJson = "{\"allowedCohorts\": [\"early_adopter\", \"beta\"]}";
            var config = objectMapper.readTree(configJson);
            EligibilityContext ctx = new EligibilityContext(
                    UUID.randomUUID(), 0, 0, "early_adopter", null);
            assertThat(rule.evaluate(ctx, config).isEligible()).isTrue();
        }

        @Test
        @DisplayName("fails when user is not in allowed cohort")
        void failsWhenNotInCohort() throws Exception {
            String configJson = "{\"allowedCohorts\": [\"early_adopter\", \"beta\"]}";
            var config = objectMapper.readTree(configJson);
            EligibilityContext ctx = new EligibilityContext(
                    UUID.randomUUID(), 0, 0, "regular", null);
            assertThat(rule.evaluate(ctx, config).isEligible()).isFalse();
        }

        @Test
        @DisplayName("passes when allowedCohorts is empty (no restriction)")
        void passesWhenNoCohortRestriction() throws Exception {
            String configJson = "{\"allowedCohorts\": []}";
            var config = objectMapper.readTree(configJson);
            EligibilityContext ctx = new EligibilityContext(
                    UUID.randomUUID(), 0, 0, "any_cohort", null);
            assertThat(rule.evaluate(ctx, config).isEligible()).isTrue();
        }
    }
}
