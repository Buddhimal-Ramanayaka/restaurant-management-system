import axiosClient from "./axiosClient";

export const fetchAllMenuItems = () => axiosClient.get("/api/menu-items").then((r) => r.data);

export const fetchAvailableMenuItems = () =>
  axiosClient.get("/api/menu-items/available").then((r) => r.data);

export const setMenuItemAvailability = (id, available) =>
  axiosClient
    .patch(`/api/menu-items/${id}/availability`, null, { params: { available } })
    .then((r) => r.data);

export const fetchMenuItemDetail = (id) => axiosClient.get(`/api/menu-items/${id}`).then((r) => r.data);

export const createMenuItem = (request) => axiosClient.post("/api/menu-items", request).then((r) => r.data);

export const updateMenuItem = (id, request) =>
  axiosClient.put(`/api/menu-items/${id}`, request).then((r) => r.data);
