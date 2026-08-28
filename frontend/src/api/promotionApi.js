import axiosClient from "./axiosClient";

// Backs the POS cart's live loyalty-discount preview (Figure 3.8). A 204 (no applicable
// promotion) is an expected, non-error outcome - the cart simply shows no discount line.
export const fetchApplicablePromotion = (loyaltyTier) =>
  axiosClient
    .get("/api/promotions/applicable", { params: loyaltyTier ? { loyaltyTier } : {} })
    .then((r) => r.data || null);
