package com.rms.controller;

import com.rms.dto.response.PromotionResponse;
import com.rms.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Backs the live loyalty-discount preview in the POS cart (Figure 3.8). */
@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping("/applicable")
    public ResponseEntity<PromotionResponse> applicable(@RequestParam(required = false) String loyaltyTier) {
        return promotionService.findApplicable(loyaltyTier)
                .map(p -> ResponseEntity.ok(PromotionResponse.from(p)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
