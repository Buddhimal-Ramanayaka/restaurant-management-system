import axios from "axios";

const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const axiosClient = axios.create({
  baseURL: BASE_URL,
  headers: { "Content-Type": "application/json" },
});

// Every outgoing request automatically carries the JWT - this is the one place
// in the whole frontend that touches the Authorization header, so a token
// refresh/rotation strategy only ever needs to change this file.
axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("rms_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// A 401 anywhere means the token has expired or was rejected - clear it and
// bounce to login rather than letting every screen invent its own handling.
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem("rms_token");
      localStorage.removeItem("rms_user");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);

export default axiosClient;
