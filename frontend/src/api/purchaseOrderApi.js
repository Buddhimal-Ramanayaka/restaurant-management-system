import axiosClient from "./axiosClient";

export const fetchActionablePurchaseOrders = () =>
  axiosClient.get("/api/purchase-orders/active").then((r) => r.data);

export const advancePurchaseOrder = (id) =>
  axiosClient.patch(`/api/purchase-orders/${id}/advance`).then((r) => r.data);

export const cancelPurchaseOrder = (id) =>
  axiosClient.patch(`/api/purchase-orders/${id}/cancel`).then((r) => r.data);

export const fetchPurchaseOrderPdf = (id) =>
  axiosClient.get(`/api/purchase-orders/${id}/pdf`, { responseType: "blob" }).then((r) => r.data);
