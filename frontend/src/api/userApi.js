import axiosClient from "./axiosClient";

export const fetchUsers = () => axiosClient.get("/api/users").then((r) => r.data);

export const createUser = (user) => axiosClient.post("/api/users", user).then((r) => r.data);

export const updateUser = (id, update) => axiosClient.patch(`/api/users/${id}`, update).then((r) => r.data);
