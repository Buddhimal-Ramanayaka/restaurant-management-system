import { useEffect, useState } from "react";
import { fetchIngredients } from "../../api/ingredientApi.js";
import { recordWaste } from "../../api/wasteLogApi.js";

const REASON_CODES = [
  { value: "SPOILAGE", label: "Spoilage" },
  { value: "BREAKAGE", label: "Breakage" },
  { value: "EXPIRY", label: "Expiry" },
  { value: "CALIBRATION", label: "Calibration" },
];

export default function LogWasteModal({ onClose }) {
  const [ingredients, setIngredients] = useState([]);
  const [ingredientId, setIngredientId] = useState("");
  const [quantityWasted, setQuantityWasted] = useState("");
  const [reasonCode, setReasonCode] = useState(REASON_CODES[0].value);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    fetchIngredients().then(setIngredients);
  }, []);

  const selectedIngredient = ingredients.find((i) => String(i.id) === String(ingredientId));
  const canSubmit = ingredientId && Number(quantityWasted) > 0 && reasonCode;

  const submit = async () => {
    setError(null);
    setBusy(true);
    try {
      await recordWaste({ ingredientId: Number(ingredientId), quantityWasted: Number(quantityWasted), reasonCode });
      setSuccess(true);
    } catch (err) {
      setError(err.response?.data?.message || "Could not log this waste entry.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-sm rounded-lg border border-ink-700 bg-ink-800 p-4">
        {success ? (
          <div className="py-4 text-center">
            <div className="mb-2 text-3xl">✓</div>
            <h2 className="mb-1 font-display text-sm font-bold text-white">Waste Logged</h2>
            <p className="mb-4 text-xs text-slate-400">
              {quantityWasted} {selectedIngredient?.unitType} of {selectedIngredient?.name} recorded.
            </p>
            <button
              onClick={onClose}
              className="min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
            >
              Done
            </button>
          </div>
        ) : (
          <>
            <h2 className="mb-3 font-display text-sm font-bold text-white">Log Waste</h2>

            <div className="space-y-2">
              <select
                value={ingredientId}
                onChange={(e) => setIngredientId(e.target.value)}
                className="min-h-11 w-full rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
              >
                <option value="">Select ingredient</option>
                {ingredients.map((ing) => (
                  <option key={ing.id} value={ing.id}>{ing.name} ({ing.unitType})</option>
                ))}
              </select>

              <input
                type="number"
                min="0.001"
                step="0.001"
                placeholder="Quantity wasted"
                value={quantityWasted}
                onChange={(e) => setQuantityWasted(e.target.value)}
                className="min-h-11 w-full rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
              />
              {selectedIngredient && (
                <p className="text-xs text-slate-500">
                  Currently {Number(selectedIngredient.currentStock).toFixed(2)} {selectedIngredient.unitType} in stock.
                </p>
              )}

              <select
                value={reasonCode}
                onChange={(e) => setReasonCode(e.target.value)}
                className="min-h-11 w-full rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
              >
                {REASON_CODES.map((r) => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </div>

            {error && <p className="mt-2 text-xs text-status-occupied">{error}</p>}

            <div className="mt-4 flex gap-2">
              <button
                disabled={!canSubmit || busy}
                onClick={submit}
                className="min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
              >
                {busy ? "Logging..." : "Log Waste"}
              </button>
              <button
                onClick={onClose}
                className="min-h-11 rounded-full border border-ink-700 px-4 text-xs text-slate-400 hover:bg-ink-700"
              >
                Cancel
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
