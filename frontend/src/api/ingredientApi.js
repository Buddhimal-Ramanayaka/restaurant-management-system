import axiosClient from "./axiosClient";

export const fetchIngredients = () => axiosClient.get("/api/ingredients").then((r) => r.data);

export const fetchLowStockIngredients = () =>
  axiosClient.get("/api/ingredients/low-stock").then((r) => r.data);

export const correctIngredientStock = (id, newPhysicalCount) =>
  axiosClient.patch(`/api/ingredients/${id}/stock-correction`, { newPhysicalCount }).then((r) => r.data);
