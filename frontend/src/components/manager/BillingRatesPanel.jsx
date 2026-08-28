import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext.jsx";
import { fetchBillingRates, updateBillingRates } from "../../api/settingsApi.js";

/** FR-21 - "Tax and service percentages shall be configurable by Admin." Read-only for
 *  Manager (the backend enforces this too - PUT /api/settings/billing-rates is Admin-only,
 *  this just avoids showing an editable form that would 403 on save). */
export default function BillingRatesPanel() {
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";

  const [rates, setRates] = useState(null);
  const [serviceChargePercent, setServiceChargePercent] = useState("");
  const [vatPercent, setVatPercent] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    fetchBillingRates().then((r) => {
      setRates(r);
      setServiceChargePercent((r.serviceChargeRate * 100).toString());
      setVatPercent((r.vatRate * 100).toString());
    });
  }, []);

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await updateBillingRates(
        Number(serviceChargePercent) / 100,
        Number(vatPercent) / 100
      );
      setRates(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      setError(err.response?.data?.message || "Could not update billing rates.");
    } finally {
      setSaving(false);
    }
  }

  if (!rates) {
    return (
      <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
        <div className="py-2 text-center text-sm text-slate-500">Loading...</div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <h2 className="mb-3 font-display text-sm font-bold text-white">Billing Rates</h2>

      {!isAdmin ? (
        <div className="flex gap-6 text-sm text-slate-300">
          <div>
            Service Charge: <span className="font-mono text-white">{(rates.serviceChargeRate * 100).toFixed(1)}%</span>
          </div>
          <div>
            VAT: <span className="font-mono text-white">{(rates.vatRate * 100).toFixed(1)}%</span>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-end gap-3">
          <label className="text-xs text-slate-400">
            Service Charge %
            <input
              type="number"
              min="0"
              max="100"
              step="0.1"
              value={serviceChargePercent}
              onChange={(e) => setServiceChargePercent(e.target.value)}
              className="mt-1 min-h-11 w-28 rounded-md border border-ink-700 bg-ink-900 px-3 py-2 text-sm text-white focus:border-accent focus:outline-none"
            />
          </label>
          <label className="text-xs text-slate-400">
            VAT %
            <input
              type="number"
              min="0"
              max="100"
              step="0.1"
              value={vatPercent}
              onChange={(e) => setVatPercent(e.target.value)}
              className="mt-1 min-h-11 w-28 rounded-md border border-ink-700 bg-ink-900 px-3 py-2 text-sm text-white focus:border-accent focus:outline-none"
            />
          </label>
          <button
            disabled={saving}
            onClick={handleSave}
            className="min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
          >
            {saving ? "Saving..." : "Save"}
          </button>
          {saved && <span className="text-xs text-status-available">Saved.</span>}
        </div>
      )}

      {error && <p className="mt-2 text-xs text-status-occupied">{error}</p>}
    </div>
  );
}
