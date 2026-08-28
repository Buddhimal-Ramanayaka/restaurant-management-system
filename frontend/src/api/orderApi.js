import axiosClient from "./axiosClient";

export const submitOrder = (payload) =>
  axiosClient.post("/api/orders", payload).then((r) => r.data);

export const updateOrderStatus = (orderId, status, orderDetailId = null) =>
  axiosClient
    .patch(`/api/orders/${orderId}/status`, { status, orderDetailId })
    .then((r) => r.data);

export const voidOrder = (orderId, managerUsername = null, managerPassword = null) =>
  axiosClient
    .post(`/api/orders/${orderId}/void`, { managerUsername, managerPassword })
    .then((r) => r.data);

export const fetchActiveOrders = () =>
  axiosClient.get("/api/orders/active").then((r) => r.data);
