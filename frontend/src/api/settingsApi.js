import axiosClient from "./axiosClient";

export const fetchBillingRates = () =>
  axiosClient.get("/api/settings/billing-rates").then((r) => r.data);

export const updateBillingRates = (serviceChargeRate, vatRate) =>
  axiosClient.put("/api/settings/billing-rates", { serviceChargeRate, vatRate }).then((r) => r.data);
