import { useEffect, useState } from "react";
import { fetchClosedShifts, reviewShift } from "../../api/billingApi.js";

function formatCurrency(value) {
  return `LKR ${Number(value ?? 0).toLocaleString("en-LK", { minimumFractionDigits: 2 })}`;
}

function formatDateTime(iso) {
  return new Date(iso).toLocaleString("en-LK", {
    day: "numeric", month: "short", hour: "2-digit", minute: "2-digit",
  });
}

/** Manager use case "Review Shift Reports" (Figure 2.1) - reconciles each closed cashier
 *  shift's declared drawer against the system-computed cash total and records who reviewed it. */
export default function ShiftReviewPanel() {
  const [shifts, setShifts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [reviewingId, setReviewingId] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchClosedShifts()
      .then(setShifts)
      .finally(() => setLoading(false));
  }, []);

  async function handleReview(shiftId) {
    setReviewingId(shiftId);
    setError(null);
    try {
      const updated = await reviewShift(shiftId);
      setShifts((prev) => prev.map((s) => (s.id === shiftId ? updated : s)));
    } catch (err) {
      setError(err.response?.data?.message || "Could not mark this shift as reviewed.");
    } finally {
      setReviewingId(null);
    }
  }

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <h2 className="mb-3 font-display text-sm font-bold text-white">Shift Reports</h2>

      {loading && <div className="py-6 text-center text-sm text-slate-500">Loading...</div>}

      {!loading && shifts.length === 0 && (
        <div className="py-6 text-center text-sm text-slate-500">No closed shifts yet.</div>
      )}

      {error && <p className="mb-2 text-xs text-status-occupied">{error}</p>}

      {shifts.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-ink-700">
          <table className="w-full text-left text-sm">
            <thead className="bg-ink-900 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-3 py-1.5">Cashier</th>
                <th className="px-3 py-1.5">Ended</th>
                <th className="px-3 py-1.5">System Cash</th>
                <th className="px-3 py-1.5">Declared</th>
                <th className="px-3 py-1.5">Variance</th>
                <th className="px-3 py-1.5">Reviewed By</th>
                <th className="px-3 py-1.5" />
              </tr>
            </thead>
            <tbody>
              {shifts.map((shift, i) => {
                const variance = Number(shift.variance ?? 0);
                return (
                  <tr key={shift.id} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                    <td className="px-3 py-1.5 text-white">{shift.cashierUsername}</td>
                    <td className="px-3 py-1.5 text-slate-400">{formatDateTime(shift.endedAt)}</td>
                    <td className="px-3 py-1.5 font-mono text-slate-300">{formatCurrency(shift.systemCashTotal)}</td>
                    <td className="px-3 py-1.5 font-mono text-slate-300">{formatCurrency(shift.declaredDrawerAmount)}</td>
                    <td
                      className={`px-3 py-1.5 font-mono ${
                        variance === 0 ? "text-status-available" : "text-status-occupied"
                      }`}
                    >
                      {variance > 0 ? "+" : ""}
                      {formatCurrency(variance)}
                    </td>
                    <td className="px-3 py-1.5 text-slate-400">{shift.reviewedByUsername || "—"}</td>
                    <td className="px-3 py-1.5">
                      {!shift.reviewedByUsername && (
                        <button
                          disabled={reviewingId === shift.id}
                          onClick={() => handleReview(shift.id)}
                          className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-accent hover:text-accent disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {reviewingId === shift.id ? "Marking..." : "Mark Reviewed"}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
