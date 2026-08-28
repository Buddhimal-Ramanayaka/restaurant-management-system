import axiosClient from "./axiosClient";

export const fetchAwaitingBilling = () =>
  axiosClient.get("/api/orders/awaiting-billing").then((r) => r.data);

export const fetchBillPreview = (orderId) =>
  axiosClient.get(`/api/billing/orders/${orderId}/bill`).then((r) => r.data);

export const fetchBillPdf = (orderId) =>
  axiosClient.get(`/api/billing/orders/${orderId}/bill/pdf`, { responseType: "blob" }).then((r) => r.data);

export const applyManualDiscount = (orderId, managerUsername, managerPassword, discountPercent) =>
  axiosClient
    .post(`/api/billing/orders/${orderId}/manual-discount`, { managerUsername, managerPassword, discountPercent })
    .then((r) => r.data);

export const settleOrder = (orderId, { shiftId, paymentMethod, amount, manualDiscountPercent }) =>
  axiosClient
    .post(`/api/billing/orders/${orderId}/settle`, { shiftId, paymentMethod, amount, manualDiscountPercent })
    .then((r) => r.data);

export const startShift = () => axiosClient.post("/api/billing/shifts/start").then((r) => r.data);

// 404 (no active shift) is an expected, non-error outcome here - callers should treat it as
// "show the Start Shift prompt", not surface it as a failure.
export const fetchActiveShift = () =>
  axiosClient.get("/api/billing/shifts/active").then(
    (r) => r.data,
    (err) => {
      if (err.response?.status === 404) return null;
      throw err;
    }
  );

export const closeShift = (shiftId, declaredDrawerAmount) =>
  axiosClient.post(`/api/billing/shifts/${shiftId}/close`, { declaredDrawerAmount }).then((r) => r.data);

export const fetchClosedShifts = () => axiosClient.get("/api/billing/shifts").then((r) => r.data);

export const reviewShift = (shiftId) =>
  axiosClient.post(`/api/billing/shifts/${shiftId}/review`).then((r) => r.data);
