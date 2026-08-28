import { useEffect, useState } from "react";
import { fetchTopSellingItems } from "../../api/analyticsApi.js";

function formatCurrency(value) {
  return `LKR ${Number(value ?? 0).toLocaleString("en-LK", { maximumFractionDigits: 0 })}`;
}

export default function TopSellingItemsPanel({ date }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetchTopSellingItems(date, 5)
      .then(setItems)
      .finally(() => setLoading(false));
  }, [date]);

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <h2 className="mb-3 font-display text-sm font-bold text-white">Top Selling Items Today</h2>

      {loading && <div className="py-6 text-center text-sm text-slate-500">Loading...</div>}

      {!loading && items.length === 0 && (
        <div className="py-6 text-center text-sm text-slate-500">No items sold yet today.</div>
      )}

      {items.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-ink-700">
          <table className="w-full text-left text-sm">
            <thead className="bg-ink-900 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-3 py-1.5">Item</th>
                <th className="px-3 py-1.5">Qty</th>
                <th className="px-3 py-1.5">Revenue</th>
                <th className="px-3 py-1.5">COGS</th>
                <th className="px-3 py-1.5">Margin</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item, i) => (
                <tr key={item.menuItemName} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                  <td className="px-3 py-1.5 text-white">{item.menuItemName}</td>
                  <td className="px-3 py-1.5 font-mono text-slate-300">{item.quantitySold}</td>
                  <td className="px-3 py-1.5 font-mono text-status-available">{formatCurrency(item.revenue)}</td>
                  <td className="px-3 py-1.5 font-mono text-slate-400">{formatCurrency(item.cogs)}</td>
                  <td className="px-3 py-1.5 font-mono text-accent">{Number(item.marginPercent).toFixed(0)}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
