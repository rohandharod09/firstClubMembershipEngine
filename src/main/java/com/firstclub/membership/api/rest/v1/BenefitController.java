package com.firstclub.membership.api.rest.v1;

import com.firstclub.membership.api.rest.dto.request.BenefitValidationRequest;
import com.firstclub.membership.api.rest.dto.response.BenefitValidationResponse;
import com.firstclub.membership.api.rest.mapper.SubscriptionResponseMapper;
import com.firstclub.membership.application.handler.BenefitValidationHandler;
import com.firstclub.membership.application.query.BenefitValidationQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/benefits")
@Tag(name = "Benefits", description = "Membership benefit validation for checkout integration")
public class BenefitController {

    private final BenefitValidationHandler validationHandler;
    private final SubscriptionResponseMapper mapper;

    public BenefitController(BenefitValidationHandler validationHandler,
                              SubscriptionResponseMapper mapper) {
        this.validationHandler = validationHandler;
        this.mapper = mapper;
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate and calculate membership benefits for a checkout order")
    public ResponseEntity<BenefitValidationResponse> validate(
            @Valid @RequestBody BenefitValidationRequest request) {
        var query = new BenefitValidationQuery(
                request.userId(), request.orderId(), request.orderValueCents(),
                request.orderCategories(), request.deliveryRequested());
        var result = validationHandler.validate(query);
        var response = mapper.toBenefitValidationResponse(
                result.eligible(), result.benefits(), result.subscription(), result.tier());
        return ResponseEntity.ok(response);
    }
}
