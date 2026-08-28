import axiosClient from "./axiosClient";

export const login = (username, password) =>
  axiosClient.post("/api/auth/login", { username, password }).then((r) => r.data);
