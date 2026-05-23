package com.firstclub.membership.domain.benefit;

import com.fasterxml.jackson.databind.JsonNode;
import com.firstclub.membership.domain.model.BenefitType;

/**
 * Strategy interface for benefit evaluation.
 * Each implementation handles one benefit type.
 * New benefits: implement this interface + @Component + insert DB row.
 */
public interface BenefitEvaluator {

    BenefitType supportedType();

    BenefitResult evaluate(BenefitContext context, JsonNode config);
}
