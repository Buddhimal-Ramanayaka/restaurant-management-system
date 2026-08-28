import { useEffect, useState } from "react";
import { fetchGrns, recordGrn } from "../../api/grnApi.js";
import { fetchSuppliers } from "../../api/supplierApi.js";
import { fetchIngredients } from "../../api/ingredientApi.js";
import { fetchActionablePurchaseOrders } from "../../api/purchaseOrderApi.js";

function todayIso() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function emptyLine() {
  return { ingredientId: "", quantityReceived: "", unitCost: "" };
}

export default function GrnReceiptsPanel() {
  const [grns, setGrns] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [ingredients, setIngredients] = useState([]);
  const [orderedPOs, setOrderedPOs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  const [supplierId, setSupplierId] = useState("");
  const [receivedDate, setReceivedDate] = useState(todayIso());
  const [purchaseOrderId, setPurchaseOrderId] = useState("");
  const [lines, setLines] = useState([emptyLine()]);
  const [formBusy, setFormBusy] = useState(false);
  const [formError, setFormError] = useState(null);

  useEffect(() => {
    Promise.all([fetchGrns(), fetchSuppliers(), fetchIngredients(), fetchActionablePurchaseOrders()]).then(
      ([grnList, supplierList, ingredientList, poList]) => {
        setGrns(grnList);
        setSuppliers(supplierList);
        setIngredients(ingredientList);
        setOrderedPOs(poList.filter((po) => po.status === "ORDERED"));
        setLoading(false);
      }
    );
  }, []);

  // A PO drafted before this panel mounted only reaches ORDERED status after the manager
  // approves it elsewhere on the same dashboard - re-fetch on open so it's linkable
  // immediately rather than only after a full page reload.
  useEffect(() => {
    if (showForm) {
      fetchActionablePurchaseOrders().then((poList) => setOrderedPOs(poList.filter((po) => po.status === "ORDERED")));
    }
  }, [showForm]);

  const updateLine = (index, field, value) => {
    setLines((prev) => prev.map((line, i) => (i === index ? { ...line, [field]: value } : line)));
  };

  const addLine = () => setLines((prev) => [...prev, emptyLine()]);
  const removeLine = (index) => setLines((prev) => prev.filter((_, i) => i !== index));

  const resetForm = () => {
    setSupplierId("");
    setReceivedDate(todayIso());
    setPurchaseOrderId("");
    setLines([emptyLine()]);
  };

  const canSubmit =
    supplierId &&
    receivedDate &&
    lines.length > 0 &&
    lines.every((l) => l.ingredientId && Number(l.quantityReceived) > 0 && Number(l.unitCost) > 0);

  const submit = async () => {
    setFormError(null);
    setFormBusy(true);
    try {
      const created = await recordGrn({
        supplierId: Number(supplierId),
        receivedDate,
        purchaseOrderId: purchaseOrderId ? Number(purchaseOrderId) : null,
        items: lines.map((l) => ({
          ingredientId: Number(l.ingredientId),
          quantityReceived: Number(l.quantityReceived),
          unitCost: Number(l.unitCost),
        })),
      });
      setGrns((prev) => [created, ...prev]);
      // A received PO is no longer ORDERED - drop it from the link dropdown's candidates.
      if (purchaseOrderId) {
        setOrderedPOs((prev) => prev.filter((po) => po.id !== Number(purchaseOrderId)));
      }
      setShowForm(false);
      resetForm();
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not record this GRN.");
    } finally {
      setFormBusy(false);
    }
  };

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-display text-sm font-bold text-white">GRN Receipts</h2>
        {!showForm && (
          <button
            onClick={() => setShowForm(true)}
            className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
          >
            + New GRN
          </button>
        )}
      </div>

      {showForm && (
        <div className="mb-4 rounded-lg border border-ink-700 bg-ink-900 p-4">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">
            Record Goods Received
          </h3>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <select
              value={supplierId}
              onChange={(e) => setSupplierId(e.target.value)}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            >
              <option value="">Select supplier</option>
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
            <input
              type="date"
              value={receivedDate}
              onChange={(e) => setReceivedDate(e.target.value)}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <select
              value={purchaseOrderId}
              onChange={(e) => setPurchaseOrderId(e.target.value)}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            >
              <option value="">No linked purchase order</option>
              {orderedPOs
                .filter((po) => !supplierId || po.supplierName === suppliers.find((s) => String(s.id) === String(supplierId))?.name)
                .map((po) => (
                  <option key={po.id} value={po.id}>PO-{po.id} - {po.supplierName}</option>
                ))}
            </select>
          </div>

          <div className="mt-3 space-y-2">
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Ingredients Received</div>
            {lines.map((line, i) => (
              <div key={i} className="grid grid-cols-1 gap-2 sm:grid-cols-[2fr_1fr_1fr_auto]">
                <select
                  value={line.ingredientId}
                  onChange={(e) => updateLine(i, "ingredientId", e.target.value)}
                  className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
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
                  placeholder="Qty received"
                  value={line.quantityReceived}
                  onChange={(e) => updateLine(i, "quantityReceived", e.target.value)}
                  className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
                />
                <input
                  type="number"
                  min="0.0001"
                  step="0.01"
                  placeholder="Unit cost"
                  value={line.unitCost}
                  onChange={(e) => updateLine(i, "unitCost", e.target.value)}
                  className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
                />
                <button
                  onClick={() => removeLine(i)}
                  disabled={lines.length === 1}
                  className="min-h-11 min-w-11 rounded-md border border-ink-700 px-2 text-xs text-slate-400 hover:border-status-occupied hover:text-status-occupied disabled:cursor-not-allowed disabled:opacity-30"
                >
                  Remove
                </button>
              </div>
            ))}
            <button onClick={addLine} className="min-h-11 px-1 text-xs font-medium text-accent hover:text-accent-soft">
              + Add another ingredient
            </button>
          </div>

          {formError && <p className="mt-2 text-xs text-status-occupied">{formError}</p>}

          <div className="mt-3 flex gap-2">
            <button
              disabled={!canSubmit || formBusy}
              onClick={submit}
              className="min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
            >
              {formBusy ? "Recording..." : "Record GRN"}
            </button>
            <button
              onClick={() => { setShowForm(false); resetForm(); }}
              className="min-h-11 rounded-full border border-ink-700 px-4 text-xs text-slate-400 hover:bg-ink-700"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {loading && <div className="py-6 text-center text-sm text-slate-500">Loading...</div>}

      {!loading && grns.length === 0 && (
        <div className="py-6 text-center text-sm text-slate-500">No goods received notes recorded yet.</div>
      )}

      <div className="space-y-2">
        {grns.map((grn) => (
          <div key={grn.id} className="rounded-lg border border-ink-700 bg-ink-900 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="font-mono text-xs text-slate-500">GRN-{grn.id}</span>
                <span className="text-sm font-semibold text-white">{grn.supplierName}</span>
                <span className="text-xs text-slate-400">{grn.receivedDate}</span>
                {grn.purchaseOrderId && (
                  <span className="rounded-full bg-status-reserved/20 px-2 py-0.5 text-[10px] font-medium text-status-reserved">
                    Linked to PO-{grn.purchaseOrderId}
                  </span>
                )}
              </div>
            </div>
            <ul className="mt-1 text-xs text-slate-400">
              {grn.ingredientSummaries.map((summary, i) => (
                <li key={i}>{summary}</li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </div>
  );
}
