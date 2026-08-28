import { useState } from "react";
import { voidOrder } from "../../api/orderApi.js";

/**
 * Appendix B: PENDING orders void immediately (kitchen hasn't started them).
 * PREPARING orders have already consumed stock and are being actively cooked, so
 * voiding one requires live Manager/Admin credential verification - the same
 * live-reauth pattern the cashier's manual-discount flow uses.
 */
export default function ActiveOrderPanel({ table, order, onBack, onVoided }) {
  const [showManagerPrompt, setShowManagerPrompt] = useState(false);
  const [managerUsername, setManagerUsername] = useState("");
  const [managerPassword, setManagerPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const canVoid = order && (order.status === "PENDING" || order.status === "PREPARING");

  async function handleVoidClick() {
    if (order.status === "PREPARING") {
      setShowManagerPrompt(true);
      return;
    }
    if (!window.confirm(`Void order #${order.id} for ${table.tableNumber}?`)) return;
    await submitVoid(null, null);
  }

  async function submitVoid(username, password) {
    setBusy(true);
    setError(null);
    try {
      await voidOrder(order.id, username, password);
      onVoided();
    } catch (err) {
      setError(err.response?.data?.message || "Could not void this order.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <button onClick={onBack} className="min-h-11 self-start px-2 text-xs text-slate-400 hover:text-white">
        ← Back to floor plan
      </button>

      <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-display text-sm font-bold text-white">
            Order #{order.id} - {table.tableNumber}
          </h2>
          <span className="rounded-full bg-ink-700 px-2 py-0.5 text-[10px] uppercase tracking-wide text-slate-300">
            {order.status}
          </span>
        </div>

        <div className="space-y-1.5">
          {order.items.map((item) => (
            <div key={item.id} className="flex justify-between text-sm text-slate-300">
              <span>
                {item.quantity}x {item.menuItemName}
              </span>
              <span className="text-[10px] uppercase text-slate-500">{item.lineStatus}</span>
            </div>
          ))}
        </div>

        {error && <p className="mt-3 text-xs text-status-occupied">{error}</p>}

        {canVoid ? (
          <button
            disabled={busy}
            onClick={handleVoidClick}
            className="mt-4 min-h-11 rounded-full border border-status-occupied px-4 text-xs font-semibold text-status-occupied hover:bg-status-occupied/10 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy ? "Voiding..." : "Void Order"}
          </button>
        ) : (
          <p className="mt-4 text-xs text-slate-500">
            This order is {order.status.toLowerCase()} and can no longer be voided.
          </p>
        )}
      </div>

      {showManagerPrompt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-xs rounded-lg border border-ink-700 bg-ink-800 p-4">
            <h3 className="mb-1 font-display text-sm font-bold text-white">Manager Approval Required</h3>
            <p className="mb-3 text-xs text-slate-400">
              This order is already being prepared. A manager must confirm the void.
            </p>
            <div className="space-y-2">
              <input
                type="text"
                placeholder="Manager username"
                value={managerUsername}
                onChange={(e) => setManagerUsername(e.target.value)}
                className="min-h-11 w-full rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
              />
              <input
                type="password"
                placeholder="Manager password"
                value={managerPassword}
                onChange={(e) => setManagerPassword(e.target.value)}
                className="min-h-11 w-full rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
              />
            </div>
            {error && <p className="mt-2 text-xs text-status-occupied">{error}</p>}
            <div className="mt-4 flex gap-2">
              <button
                disabled={busy || !managerUsername || !managerPassword}
                onClick={() => submitVoid(managerUsername, managerPassword)}
                className="min-h-11 rounded-full bg-status-occupied px-4 text-xs font-semibold text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {busy ? "Verifying..." : "Confirm Void"}
              </button>
              <button
                onClick={() => setShowManagerPrompt(false)}
                className="min-h-11 rounded-full border border-ink-700 px-4 text-xs text-slate-400 hover:bg-ink-700"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
