package com.firstclub.membership.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.firstclub.membership.domain.benefit.*;
import com.firstclub.membership.domain.model.BenefitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Benefit Evaluators")
class BenefitEvaluatorTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private BenefitContext context(long orderValueCents, List<String> categories,
                                   boolean delivery) {
        return new BenefitContext(UUID.randomUUID(), UUID.randomUUID(),
                orderValueCents, categories, delivery, "Gold");
    }

    @Nested
    @DisplayName("FreeDeliveryEvaluator")
    class FreeDeliveryTests {

        private final FreeDeliveryEvaluator evaluator = new FreeDeliveryEvaluator();

        @Test
        @DisplayName("applies free delivery when requested and order meets minimum")
        void appliesWhenEligible() throws Exception {
            String config = "{\"minOrderValueCents\": 9900}";
            var configNode = objectMapper.readTree(config);
            var ctx = context(15000, List.of(), true);
            var result = evaluator.evaluate(ctx, configNode);
            assertThat(result.isApplied()).isTrue();
            assertThat(result.isFreeDelivery()).isTrue();
            assertThat(result.getDiscountCents()).isEqualTo(0);
        }

        @Test
        @DisplayName("does not apply when delivery not requested")
        void doesNotApplyWhenNoDelivery() throws Exception {
            String config = "{\"minOrderValueCents\": 0}";
            var configNode = objectMapper.readTree(config);
            var ctx = context(15000, List.of(), false);
            var result = evaluator.evaluate(ctx, configNode);
            assertThat(result.isApplied()).isFalse();
        }

        @Test
        @DisplayName("does not apply when order below minimum value")
        void doesNotApplyBelowMinimum() throws Exception {
            String config = "{\"minOrderValueCents\": 20000}";
            var configNode = objectMapper.readTree(config);
            var ctx = context(5000, List.of(), true);
            var result = evaluator.evaluate(ctx, configNode);
            assertThat(result.isApplied()).isFalse();
        }
    }

    @Nested
    @DisplayName("DiscountEvaluator")
    class DiscountTests {

        private final DiscountEvaluator evaluator = new DiscountEvaluator();

        @Test
        @DisplayName("calculates 10% discount correctly")
        void calculatesTenPercentDiscount() throws Exception {
            String config = "{\"discountPercent\": 10, \"applicableCategories\": [\"all\"]}";
            var configNode = objectMapper.readTree(config);
            var ctx = context(100000, List.of("dairy"), false);
            var result = evaluator.evaluate(ctx, configNode);
            assertThat(result.isApplied()).isTrue();
            assertThat(result.getDiscountCents()).isEqualTo(10000);
        }

        @Test
        @DisplayName("respects max discount cap")
        void respectsMaxCap() throws Exception {
            String config = "{\"discountPercent\": 15, \"applicableCategories\": [\"all\"], " +
                            "\"maxDiscountCents\": 5000}";
            var configNode = objectMapper.readTree(config);
            var ctx = context(100000, List.of("snacks"), false);
            var result = evaluator.evaluate(ctx, configNode);
            assertThat(result.isApplied()).isTrue();
            assertThat(result.getDiscountCents()).isEqualTo(5000);
        }

        @Test
        @DisplayName("does not apply when no matching category")
        void doesNotApplyForNonMatchingCategory() throws Exception {
            String config = "{\"discountPercent\": 10, \"applicableCategories\": [\"dairy\"]}";
            var configNode = objectMapper.readTree(config);
            var ctx = context(100000, List.of("electronics"), false);
            var result = evaluator.evaluate(ctx, configNode);
            assertThat(result.isApplied()).isFalse();
        }

        @Test
        @DisplayName("applies when category matches one of the configured categories")
        void appliesWhenCategoryMatches() throws Exception {
            String config = "{\"discountPercent\": 5, \"applicableCategories\": [\"dairy\", \"snacks\"]}";
            var configNode = objectMapper.readTree(config);
            var ctx = context(50000, List.of("dairy", "fresh"), false);
            var result = evaluator.evaluate(ctx, configNode);
            assertThat(result.isApplied()).isTrue();
            assertThat(result.getDiscountCents()).isEqualTo(2500);
        }
    }
}
