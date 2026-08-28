import axiosClient from "./axiosClient";

export const fetchTables = () => axiosClient.get("/api/tables").then((r) => r.data);

export const markTableAvailable = (id) =>
  axiosClient.patch(`/api/tables/${id}/available`).then((r) => r.data);
