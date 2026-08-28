import { useEffect, useState } from "react";

const LANE_ACCENT = {
  PENDING: "border-status-cleaning",
  PREPARING: "border-status-billed",
  READY: "border-status-available",
};

function elapsedMinutes(fromIso) {
  return Math.floor((Date.now() - new Date(fromIso).getTime()) / 60000);
}

export default function TicketCard({ order, onStartCooking, onMarkLineDone, onMarkAllReady }) {
  const [elapsed, setElapsed] = useState(elapsedMinutes(order.createdAt));

  useEffect(() => {
    const id = setInterval(() => setElapsed(elapsedMinutes(order.createdAt)), 30000);
    return () => clearInterval(id);
  }, [order.createdAt]);

  const isUrgent = order.status === "PENDING" && elapsed > 15;
  const allLinesReady = order.items.every((i) => i.lineStatus === "READY");

  return (
    <div
      className={`ticket-perforation rounded-lg border-2 bg-ink-950 p-3 text-status-available ${
        isUrgent ? "border-status-occupied" : LANE_ACCENT[order.status] || "border-ink-700"
      }`}
    >
      <div className="flex items-center justify-between text-white">
        <span className="font-display text-sm font-bold">Order #{order.id}</span>
        <span className="font-mono text-xs text-slate-400">Table {order.tableId}</span>
      </div>
      <div className={`mt-0.5 text-xs ${isUrgent ? "text-status-occupied font-semibold" : "text-slate-500"}`}>
        {isUrgent ? `⚠ Waiting ${elapsed} min — URGENT` : `Waiting ${elapsed} min`}
      </div>

      <div className="mt-3 space-y-2">
        {order.items.map((line) => (
          <div key={line.id} className="rounded bg-ink-800 px-2 py-1.5">
            <div className="flex items-center justify-between">
              <span className="text-sm text-slate-100">
                {line.quantity}x {line.menuItemName}
              </span>
              {order.status !== "PENDING" && (
                <button
                  onClick={() => onMarkLineDone(order.id, line.id)}
                  disabled={line.lineStatus === "READY"}
                  className={`min-h-11 min-w-11 rounded-full px-3 text-xs font-medium ${
                    line.lineStatus === "READY"
                      ? "bg-status-available/20 text-status-available"
                      : "bg-ink-700 text-slate-300 hover:bg-status-billed/30"
                  }`}
                >
                  {line.lineStatus === "READY" ? "Done" : "Mark Done"}
                </button>
              )}
            </div>
            {line.specialNotes && (
              <div className="mt-0.5 text-[11px] italic text-slate-500">Note: {line.specialNotes}</div>
            )}
          </div>
        ))}
      </div>

      <div className="mt-3">
        {order.status === "PENDING" && (
          <button
            onClick={() => onStartCooking(order.id)}
            className="min-h-11 w-full rounded-md bg-status-billed text-sm font-semibold text-white"
          >
            ▶ Start Cooking
          </button>
        )}
        {order.status === "PREPARING" && (
          <button
            onClick={() => onMarkAllReady(order.id)}
            disabled={!allLinesReady}
            className="min-h-11 w-full rounded-md bg-status-available text-sm font-semibold text-ink-950 disabled:opacity-30"
          >
            ✓ All Ready
          </button>
        )}
        {order.status === "READY" && (
          <div className="w-full rounded-md bg-ink-800 py-2 text-center text-sm font-semibold text-status-available">
            Waiter notified ✓
          </div>
        )}
      </div>
    </div>
  );
}
