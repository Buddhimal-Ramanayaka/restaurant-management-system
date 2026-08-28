import axiosClient from "./axiosClient";

export const fetchUpcomingReservations = () =>
  axiosClient.get("/api/reservations/upcoming").then((r) => r.data);

export const createReservation = (payload) =>
  axiosClient.post("/api/reservations", payload).then((r) => r.data);

export const checkInReservation = (id) =>
  axiosClient.patch(`/api/reservations/${id}/check-in`).then((r) => r.data);

export const cancelReservation = (id) =>
  axiosClient.patch(`/api/reservations/${id}/cancel`).then((r) => r.data);
