import { useEffect, useState } from "react";
import { fetchIngredients, correctIngredientStock } from "../../api/ingredientApi.js";

/** Manager "Approve Stock Adjustments" use case (Figure 2.1) - Module 2.9 physical
 *  cycle-count correction. IngredientController already exposed PATCH .../stock-correction
 *  (Admin/Manager-gated), but no screen anywhere ever called it. */
export default function StockAdjustmentPanel() {
  const [ingredients, setIngredients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [adjustingId, setAdjustingId] = useState(null);
  const [newCount, setNewCount] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [lastResult, setLastResult] = useState(null);

  const load = () => {
    setLoading(true);
    fetchIngredients().then(setIngredients).finally(() => setLoading(false));
  };

  useEffect(load, []);

  const startAdjust = (ing) => {
    setAdjustingId(ing.id);
    setNewCount(String(ing.currentStock));
    setError(null);
    setLastResult(null);
  };

  const submit = async (ing) => {
    setBusy(true);
    setError(null);
    try {
      const updated = await correctIngredientStock(ing.id, Number(newCount));
      setIngredients((prev) => prev.map((i) => (i.id === ing.id ? updated : i)));
      setLastResult({ name: ing.name, before: ing.currentStock, after: updated.currentStock });
      setAdjustingId(null);
    } catch (err) {
      setError(err.response?.data?.message || "Could not apply this stock correction.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <h2 className="mb-3 font-display text-sm font-bold text-white">Stock Adjustments</h2>
      <p className="mb-3 text-xs text-slate-400">
        Record the result of a physical cycle count - the difference is posted to the inventory
        ledger as a MANUAL_ADJUSTMENT / STOCK_TAKE_CORRECTION entry.
      </p>

      {lastResult && (
        <div className="mb-3 rounded-md border border-status-available/40 bg-status-available/10 px-3 py-2 text-xs text-status-available">
          {lastResult.name}: {Number(lastResult.before).toFixed(2)} → {Number(lastResult.after).toFixed(2)}
        </div>
      )}
      {error && <p className="mb-2 text-xs text-status-occupied">{error}</p>}

      {loading && <div className="py-6 text-center text-sm text-slate-500">Loading...</div>}

      {!loading && (
        <div className="max-h-96 overflow-y-auto rounded-lg border border-ink-700">
          <table className="w-full text-left text-sm">
            <thead className="sticky top-0 bg-ink-900 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-3 py-1.5">Ingredient</th>
                <th className="px-3 py-1.5">System Stock</th>
                <th className="px-3 py-1.5">Unit</th>
                <th className="px-3 py-1.5" />
              </tr>
            </thead>
            <tbody>
              {ingredients.map((ing, i) => (
                <tr key={ing.id} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                  <td className="px-3 py-1.5 text-white">{ing.name}</td>
                  <td className="px-3 py-1.5 font-mono text-slate-300">{Number(ing.currentStock).toFixed(2)}</td>
                  <td className="px-3 py-1.5 text-slate-400">{ing.unitType}</td>
                  <td className="px-3 py-1.5">
                    {adjustingId === ing.id ? (
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min="0"
                          step="0.001"
                          value={newCount}
                          onChange={(e) => setNewCount(e.target.value)}
                          className="min-h-11 w-28 rounded-md border border-ink-700 bg-ink-900 px-2 text-sm text-white focus:border-accent focus:outline-none"
                        />
                        <button
                          disabled={busy || newCount === ""}
                          onClick={() => submit(ing)}
                          className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {busy ? "Saving..." : "Save"}
                        </button>
                        <button
                          onClick={() => setAdjustingId(null)}
                          className="min-h-11 rounded-full border border-ink-700 px-3 text-xs text-slate-400 hover:bg-ink-700"
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => startAdjust(ing)}
                        className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-accent hover:text-accent"
                      >
                        Correct
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
