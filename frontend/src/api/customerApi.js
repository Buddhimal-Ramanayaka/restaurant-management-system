import axiosClient from "./axiosClient";

// 404 (no match) is an expected, non-error outcome here - callers treat it as
// "offer to register a walk-in", not surface it as a failure.
export const lookupCustomer = (phone) =>
  axiosClient.get("/api/customers/lookup", { params: { phone } }).then(
    (r) => r.data,
    (err) => {
      if (err.response?.status === 404) return null;
      throw err;
    }
  );

export const registerCustomer = (name, phoneNumber) =>
  axiosClient.post("/api/customers", { name, phoneNumber }).then((r) => r.data);
