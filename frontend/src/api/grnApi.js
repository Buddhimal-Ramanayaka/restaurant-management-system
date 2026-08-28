import axiosClient from "./axiosClient";

export const fetchGrns = () => axiosClient.get("/api/grn").then((r) => r.data);

export const recordGrn = (payload) => axiosClient.post("/api/grn", payload).then((r) => r.data);
