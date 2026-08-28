package com.rms.service;

import com.rms.domain.Promotion;
import com.rms.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Optional;

/**
 * Extracted out of BillingService so the same tier-matching rule can also back the
 * live discount preview in the waiter's cart (Figure 3.8) - previously this logic only
 * ran once, at final billing, so a Gold customer never saw their discount until the
 * cashier's screen even though the wireframe shows it live during ordering.
 *
 * Simple, explainable matching: an enabled promotion applies if its loyalty-tier
 * requirement (if any) matches the given tier, and its active time window (if any)
 * contains the current time. Category-scoped promotions and buy-X-get-Y logic are
 * intentionally out of scope for this MVP pass - see PromotionRepository for the
 * full entity shape a future iteration would extend this against.
 */
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;

    @Transactional(readOnly = true)
    public Optional<Promotion> findApplicable(String loyaltyTier) {
        LocalTime now = LocalTime.now();
        return promotionRepository.findByEnabledTrue().stream()
                .filter(p -> p.getRequiredLoyaltyTier() == null
                        || (loyaltyTier != null && p.getRequiredLoyaltyTier().equalsIgnoreCase(loyaltyTier)))
                .filter(p -> p.getActiveFrom() == null || p.getActiveTo() == null
                        || (!now.isBefore(p.getActiveFrom()) && !now.isAfter(p.getActiveTo())))
                .filter(p -> p.getDiscountPercent() != null)
                .findFirst();
    }
}
