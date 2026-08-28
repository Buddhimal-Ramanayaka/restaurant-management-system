import { useEffect, useState } from "react";
import { fetchWasteLogs } from "../../api/wasteLogApi.js";

function formatDateTime(iso) {
  return new Date(iso).toLocaleString("en-LK", {
    day: "numeric", month: "short", hour: "2-digit", minute: "2-digit",
  });
}

export default function WasteLogPanel() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchWasteLogs()
      .then((all) => setLogs(all.sort((a, b) => b.loggedAt.localeCompare(a.loggedAt)).slice(0, 10)))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <h2 className="mb-3 font-display text-sm font-bold text-white">Recent Waste</h2>

      {loading && <div className="py-6 text-center text-sm text-slate-500">Loading...</div>}

      {!loading && logs.length === 0 && (
        <div className="py-6 text-center text-sm text-slate-500">No waste logged yet.</div>
      )}

      <div className="overflow-hidden rounded-lg border border-ink-700">
        {logs.length > 0 && (
          <table className="w-full text-left text-sm">
            <thead className="bg-ink-900 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-3 py-1.5">Ingredient</th>
                <th className="px-3 py-1.5">Qty</th>
                <th className="px-3 py-1.5">Reason</th>
                <th className="px-3 py-1.5">Logged By</th>
                <th className="px-3 py-1.5">When</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log, i) => (
                <tr key={log.id} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                  <td className="px-3 py-1.5 text-white">{log.ingredientName}</td>
                  <td className="px-3 py-1.5 font-mono text-status-occupied">{Number(log.quantityWasted).toFixed(2)}</td>
                  <td className="px-3 py-1.5 text-slate-400">{log.reasonCode}</td>
                  <td className="px-3 py-1.5 text-slate-400">{log.loggedByUsername}</td>
                  <td className="px-3 py-1.5 text-slate-400">{formatDateTime(log.loggedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
