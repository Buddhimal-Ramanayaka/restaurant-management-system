import axiosClient from "./axiosClient";

export const fetchWasteLogs = () => axiosClient.get("/api/waste-logs").then((r) => r.data);

export const recordWaste = (payload) => axiosClient.post("/api/waste-logs", payload).then((r) => r.data);
