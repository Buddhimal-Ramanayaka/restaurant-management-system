import { useEffect, useState } from "react";
import { fetchSuppliers, createSupplier, updateSupplier } from "../../api/supplierApi.js";

function emptyForm() {
  return { name: "", contactPhone: "", contactEmail: "" };
}

/** Admin "Manage Suppliers" use case (Figure 2.1) - previously read-only (list for GRN/PO
 *  pickers only), with no way to actually add or edit a supplier. */
export default function SupplierManagementPanel() {
  const [suppliers, setSuppliers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm());
  const [formBusy, setFormBusy] = useState(false);
  const [formError, setFormError] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState(emptyForm());
  const [rowError, setRowError] = useState(null);

  const load = () => {
    setLoading(true);
    fetchSuppliers().then(setSuppliers).finally(() => setLoading(false));
  };

  useEffect(load, []);

  const canSubmit = form.name.trim();

  const submit = async () => {
    setFormError(null);
    setFormBusy(true);
    try {
      const created = await createSupplier(form);
      setSuppliers((prev) => [...prev, created]);
      setShowForm(false);
      setForm(emptyForm());
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not create this supplier.");
    } finally {
      setFormBusy(false);
    }
  };

  const startEdit = (s) => {
    setEditingId(s.id);
    setEditForm({ name: s.name, contactPhone: s.contactPhone || "", contactEmail: s.contactEmail || "" });
    setRowError(null);
  };

  const saveEdit = async (id) => {
    setRowError(null);
    try {
      const updated = await updateSupplier(id, editForm);
      setSuppliers((prev) => prev.map((s) => (s.id === id ? updated : s)));
      setEditingId(null);
    } catch (err) {
      setRowError(err.response?.data?.message || "Could not update this supplier.");
    }
  };

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-display text-sm font-bold text-white">Suppliers</h2>
        {!showForm && (
          <button
            onClick={() => setShowForm(true)}
            className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
          >
            + New Supplier
          </button>
        )}
      </div>

      {showForm && (
        <div className="mb-4 rounded-lg border border-ink-700 bg-ink-900 p-4">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">New Supplier</h3>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <input
              type="text"
              placeholder="Supplier name"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="text"
              placeholder="Contact phone"
              value={form.contactPhone}
              onChange={(e) => setForm((f) => ({ ...f, contactPhone: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="email"
              placeholder="Contact email"
              value={form.contactEmail}
              onChange={(e) => setForm((f) => ({ ...f, contactEmail: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
          </div>
          {formError && <p className="mt-2 text-xs text-status-occupied">{formError}</p>}
          <div className="mt-3 flex gap-2">
            <button
              disabled={!canSubmit || formBusy}
              onClick={submit}
              className="min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
            >
              {formBusy ? "Creating..." : "Create Supplier"}
            </button>
            <button
              onClick={() => { setShowForm(false); setForm(emptyForm()); }}
              className="min-h-11 rounded-full border border-ink-700 px-4 text-xs text-slate-400 hover:bg-ink-700"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {rowError && <p className="mb-2 text-xs text-status-occupied">{rowError}</p>}
      {loading && <div className="py-6 text-center text-sm text-slate-500">Loading...</div>}

      {!loading && (
        <div className="overflow-hidden rounded-lg border border-ink-700">
          <table className="w-full text-left text-sm">
            <thead className="bg-ink-900 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-3 py-1.5">Name</th>
                <th className="px-3 py-1.5">Phone</th>
                <th className="px-3 py-1.5">Email</th>
                <th className="px-3 py-1.5" />
              </tr>
            </thead>
            <tbody>
              {suppliers.map((s, i) => (
                editingId === s.id ? (
                  <tr key={s.id} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                    <td className="px-3 py-1.5">
                      <input
                        value={editForm.name}
                        onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))}
                        className="min-h-11 w-full rounded-md border border-ink-700 bg-ink-900 px-2 text-sm text-white focus:border-accent focus:outline-none"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      <input
                        value={editForm.contactPhone}
                        onChange={(e) => setEditForm((f) => ({ ...f, contactPhone: e.target.value }))}
                        className="min-h-11 w-full rounded-md border border-ink-700 bg-ink-900 px-2 text-sm text-white focus:border-accent focus:outline-none"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      <input
                        value={editForm.contactEmail}
                        onChange={(e) => setEditForm((f) => ({ ...f, contactEmail: e.target.value }))}
                        className="min-h-11 w-full rounded-md border border-ink-700 bg-ink-900 px-2 text-sm text-white focus:border-accent focus:outline-none"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      <div className="flex gap-2">
                        <button
                          onClick={() => saveEdit(s.id)}
                          className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
                        >
                          Save
                        </button>
                        <button
                          onClick={() => setEditingId(null)}
                          className="min-h-11 rounded-full border border-ink-700 px-3 text-xs text-slate-400 hover:bg-ink-700"
                        >
                          Cancel
                        </button>
                      </div>
                    </td>
                  </tr>
                ) : (
                  <tr key={s.id} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                    <td className="px-3 py-1.5 text-white">{s.name}</td>
                    <td className="px-3 py-1.5 text-slate-400">{s.contactPhone || "—"}</td>
                    <td className="px-3 py-1.5 text-slate-400">{s.contactEmail || "—"}</td>
                    <td className="px-3 py-1.5">
                      <button
                        onClick={() => startEdit(s)}
                        className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-accent hover:text-accent"
                      >
                        Edit
                      </button>
                    </td>
                  </tr>
                )
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
