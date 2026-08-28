package com.rms.service;

import com.rms.domain.Promotion;
import com.rms.repository.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Backs the POS cart's live loyalty-discount preview (Figure 3.8) as well as final billing. */
@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private PromotionRepository promotionRepository;
    private PromotionService promotionService;

    @Test
    @DisplayName("Matching tier returns the promotion")
    void findApplicable_matchingTier_returnsPromotion() {
        promotionService = new PromotionService(promotionRepository);
        Promotion goldDiscount = Promotion.builder()
                .id(1L).name("Gold Tier 10% Off").discountPercent(new BigDecimal("10"))
                .requiredLoyaltyTier("GOLD").enabled(true)
                .build();
        when(promotionRepository.findByEnabledTrue()).thenReturn(List.of(goldDiscount));

        Optional<Promotion> result = promotionService.findApplicable("GOLD");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Gold Tier 10% Off");
    }

    @Test
    @DisplayName("Non-matching tier returns empty")
    void findApplicable_wrongTier_returnsEmpty() {
        promotionService = new PromotionService(promotionRepository);
        Promotion goldDiscount = Promotion.builder()
                .id(1L).name("Gold Tier 10% Off").discountPercent(new BigDecimal("10"))
                .requiredLoyaltyTier("GOLD").enabled(true)
                .build();
        when(promotionRepository.findByEnabledTrue()).thenReturn(List.of(goldDiscount));

        assertThat(promotionService.findApplicable("STANDARD")).isEmpty();
        assertThat(promotionService.findApplicable(null)).isEmpty();
    }

    @Test
    @DisplayName("A promotion with no discountPercent (e.g. buy-X-get-Y) is never returned as a preview")
    void findApplicable_nullDiscountPercent_isExcluded() {
        promotionService = new PromotionService(promotionRepository);
        Promotion buyXGetY = Promotion.builder()
                .id(2L).name("Happy Hour BOGO").buyXGetYFree(true).enabled(true)
                .build();
        when(promotionRepository.findByEnabledTrue()).thenReturn(List.of(buyXGetY));

        assertThat(promotionService.findApplicable(null)).isEmpty();
    }
}
