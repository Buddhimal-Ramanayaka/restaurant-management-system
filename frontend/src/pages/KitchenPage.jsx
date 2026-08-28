import { useCallback, useEffect, useMemo, useState } from "react";
import Navbar from "../components/common/Navbar.jsx";
import KanbanBoard from "../components/kitchen/KanbanBoard.jsx";
import LogWasteModal from "../components/kitchen/LogWasteModal.jsx";
import { useStompClient } from "../hooks/useStompClient.js";
import { fetchActiveOrders, updateOrderStatus } from "../api/orderApi.js";

/**
 * Kitchen Display state model: one initial REST fetch to seed the board, then
 * every subsequent change arrives over STOMP and is merged into local state
 * in place - there is no polling and no full-board refetch after the initial
 * load, which is what "real-time without unnecessary refreshes" means here.
 */
export default function KitchenPage() {
  const [orders, setOrders] = useState([]);
  const [showWasteModal, setShowWasteModal] = useState(false);

  useEffect(() => {
    fetchActiveOrders().then(setOrders);
  }, []);

  const upsertOrder = useCallback((incoming) => {
    setOrders((prev) => {
      const exists = prev.some((o) => o.id === incoming.id);
      if (exists) {
        return prev.map((o) => (o.id === incoming.id ? incoming : o));
      }
      return [...prev, incoming];
    });
  }, []);

  const removeOrder = useCallback((orderId) => {
    setOrders((prev) => prev.filter((o) => o.id !== orderId));
  }, []);

  const subscriptions = useMemo(
    () => [
      {
        destination: "/topic/kitchen",
        onMessage: (msg) => {
          if (msg.eventType === "ORDER_VOIDED") {
            removeOrder(msg.orderId);
          } else {
            upsertOrder(msg.order);
          }
        },
      },
    ],
    [upsertOrder, removeOrder]
  );
  const { connected } = useStompClient(subscriptions);

  async function handleStartCooking(orderId) {
    const updated = await updateOrderStatus(orderId, "PREPARING");
    upsertOrder(updated);
  }

  async function handleMarkLineDone(orderId, lineId) {
    const updated = await updateOrderStatus(orderId, "READY", lineId);
    upsertOrder(updated);
  }

  async function handleMarkAllReady(orderId) {
    const updated = await updateOrderStatus(orderId, "READY");
    upsertOrder(updated);
  }

  return (
    <div className="flex h-screen flex-col bg-ink-900">
      <Navbar
        title="Kitchen Display System"
        right={
          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowWasteModal(true)}
              className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-status-cleaning hover:text-status-cleaning"
            >
              Log Waste
            </button>
            <div className="flex items-center gap-1.5 text-xs">
              <span className={`h-2 w-2 rounded-full ${connected ? "bg-status-available" : "bg-status-occupied"}`} />
              <span className="text-slate-400">{connected ? "Live" : "Reconnecting..."}</span>
            </div>
          </div>
        }
      />
      <KanbanBoard
        orders={orders}
        onStartCooking={handleStartCooking}
        onMarkLineDone={handleMarkLineDone}
        onMarkAllReady={handleMarkAllReady}
      />
      {showWasteModal && <LogWasteModal onClose={() => setShowWasteModal(false)} />}
    </div>
  );
}
