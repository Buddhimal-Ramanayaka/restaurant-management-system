import axiosClient from "./axiosClient";

export const fetchDailySales = (date) =>
  axiosClient.get("/api/analytics/daily-sales", { params: { date } }).then((r) => r.data);

export const fetchRevenueTrend = (days = 7) =>
  axiosClient.get("/api/analytics/revenue-trend", { params: { days } }).then((r) => r.data);

export const fetchTopSellingItems = (date, limit = 5) =>
  axiosClient.get("/api/analytics/top-items", { params: { date, limit } }).then((r) => r.data);
