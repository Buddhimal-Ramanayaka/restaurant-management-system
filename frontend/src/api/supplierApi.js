import axiosClient from "./axiosClient";

export const fetchSuppliers = () => axiosClient.get("/api/suppliers").then((r) => r.data);

export const createSupplier = (supplier) => axiosClient.post("/api/suppliers", supplier).then((r) => r.data);

export const updateSupplier = (id, supplier) =>
  axiosClient.put(`/api/suppliers/${id}`, supplier).then((r) => r.data);
