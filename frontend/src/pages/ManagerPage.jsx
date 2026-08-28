import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Navbar from "../components/common/Navbar.jsx";
import KpiCard from "../components/manager/KpiCard.jsx";
import RevenueChart from "../components/manager/RevenueChart.jsx";
import TopSellingItemsPanel from "../components/manager/TopSellingItemsPanel.jsx";
import PoApprovalPanel from "../components/manager/PoApprovalPanel.jsx";
import GrnReceiptsPanel from "../components/manager/GrnReceiptsPanel.jsx";
import WasteLogPanel from "../components/manager/WasteLogPanel.jsx";
import ShiftReviewPanel from "../components/manager/ShiftReviewPanel.jsx";
import BillingRatesPanel from "../components/manager/BillingRatesPanel.jsx";
import StockAdjustmentPanel from "../components/manager/StockAdjustmentPanel.jsx";
import ReservationsPanel from "../components/tables/ReservationsPanel.jsx";
import LogWasteModal from "../components/kitchen/LogWasteModal.jsx";
import { useStompClient } from "../hooks/useStompClient.js";
import { useAuth } from "../context/AuthContext.jsx";
import { fetchLowStockIngredients } from "../api/ingredientApi.js";
import { fetchDailySales, fetchRevenueTrend } from "../api/analyticsApi.js";
import { fetchTables } from "../api/tableApi.js";

function todayIso() {
  // Local calendar date, not toISOString()'s UTC date - the two disagree for
  // several hours after local midnight in any timezone ahead of UTC (like the
  // +05:30 this restaurant runs in), which would otherwise ask the backend for
  // "today" and silently get yesterday's report back.
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatCurrency(value) {
  return `LKR ${Number(value ?? 0).toLocaleString("en-LK", { maximumFractionDigits: 0 })}`;
}

export default function ManagerPage() {
  const { user } = useAuth();
  const [lowStock, setLowStock] = useState([]);
  const [liveAlerts, setLiveAlerts] = useState([]);
  const [todaySales, setTodaySales] = useState(null);
  const [activeTables, setActiveTables] = useState(0);
  const [revenueTrend, setRevenueTrend] = useState([]);
  const [tables, setTables] = useState([]);
  const [showWasteModal, setShowWasteModal] = useState(false);
  const [highlightPoId, setHighlightPoId] = useState(null);

  useEffect(() => {
    fetchLowStockIngredients().then(setLowStock);
    fetchDailySales(todayIso()).then(setTodaySales);
    fetchRevenueTrend(7).then(setRevenueTrend);
    fetchTables().then((fetchedTables) => {
      setTables(fetchedTables);
      setActiveTables(fetchedTables.filter((t) => t.operationalStatus === "OCCUPIED").length);
    });
  }, []);

  const subscriptions = useMemo(
    () => [
      {
        destination: "/topic/alerts/stock",
        onMessage: (msg) => {
          setLiveAlerts((prev) => [msg, ...prev].slice(0, 10));
        },
      },
    ],
    []
  );
  useStompClient(subscriptions);

  return (
    <div className="min-h-screen bg-ink-900">
      <Navbar
        title="Manager Dashboard"
        right={
          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowWasteModal(true)}
              className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-status-cleaning hover:text-status-cleaning"
            >
              Log Waste
            </button>
            {user?.role === "ADMIN" && (
              <Link
                to="/admin"
                className="min-h-11 flex items-center rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-accent hover:text-accent"
              >
                Admin Panel →
              </Link>
            )}
          </div>
        }
      />

      <div className="space-y-4 p-4">
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <KpiCard label="Total Revenue" value={formatCurrency(todaySales?.totalRevenue)} accent="blue" />
          <KpiCard label="Orders Today" value={todaySales?.totalOrders ?? 0} accent="accent" />
          <KpiCard label="COGS" value={formatCurrency(todaySales?.totalCogs)} accent="orange" />
          <KpiCard label="Active Tables" value={activeTables} accent="slate" />
        </div>

        {revenueTrend.length > 0 && <RevenueChart data={revenueTrend} />}

        <TopSellingItemsPanel date={todayIso()} />

        <h2 className="font-display text-sm font-bold text-white">⚠ Stock Alerts</h2>

        {liveAlerts.length > 0 && (
          <div className="mb-4 space-y-2">
            {liveAlerts.map((alert, idx) => (
              <div
                key={idx}
                className={`rounded-lg border-l-4 p-3 text-sm ${
                  alert.severity === "OUT_OF_STOCK"
                    ? "border-status-occupied bg-status-occupied/10 text-status-occupied"
                    : "border-status-cleaning bg-status-cleaning/10 text-status-cleaning"
                }`}
              >
                <span className="font-semibold">{alert.severity === "OUT_OF_STOCK" ? "OUT OF STOCK" : "LOW STOCK"}</span>
                {" — "}
                {alert.ingredientName} ({Number(alert.currentStock).toFixed(2)} remaining, reorder at{" "}
                {Number(alert.reorderLevel).toFixed(2)})
                {alert.autoGeneratedPurchaseOrderId && (
                  <button
                    onClick={() => setHighlightPoId(alert.autoGeneratedPurchaseOrderId)}
                    className="ml-2 min-h-11 text-xs font-semibold underline opacity-90 hover:opacity-100"
                  >
                    → Draft PO #{alert.autoGeneratedPurchaseOrderId} awaiting approval
                  </button>
                )}
              </div>
            ))}
          </div>
        )}

        <div className="overflow-hidden rounded-lg border border-ink-700">
          <table className="w-full text-left text-sm">
            <thead className="bg-ink-800 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-4 py-2">Ingredient</th>
                <th className="px-4 py-2">Current Stock</th>
                <th className="px-4 py-2">Reorder Level</th>
                <th className="px-4 py-2">Unit</th>
                <th className="px-4 py-2">Supplier</th>
              </tr>
            </thead>
            <tbody>
              {lowStock.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                    No ingredients currently below reorder level.
                  </td>
                </tr>
              )}
              {lowStock.map((ing, i) => (
                <tr key={ing.id} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                  <td className="px-4 py-2 text-white">{ing.name}</td>
                  <td className="px-4 py-2 font-mono text-status-occupied">
                    {Number(ing.currentStock).toFixed(2)}
                  </td>
                  <td className="px-4 py-2 font-mono text-slate-400">
                    {Number(ing.reorderLevel).toFixed(2)}
                  </td>
                  <td className="px-4 py-2 text-slate-400">{ing.unitType}</td>
                  <td className="px-4 py-2 text-slate-400">{ing.preferredSupplierName || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <PoApprovalPanel highlightPoId={highlightPoId} />
        <GrnReceiptsPanel />
        <WasteLogPanel />
        <ShiftReviewPanel />
        <BillingRatesPanel />
        <StockAdjustmentPanel />
        <div className="rounded-lg border border-ink-700 bg-ink-800">
          <ReservationsPanel tables={tables} />
        </div>
      </div>

      {showWasteModal && <LogWasteModal onClose={() => setShowWasteModal(false)} />}
    </div>
  );
}
