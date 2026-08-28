import { useEffect, useState } from "react";
import {
  fetchAllMenuItems,
  fetchMenuItemDetail,
  createMenuItem,
  updateMenuItem,
  setMenuItemAvailability,
} from "../../api/menuApi.js";
import { fetchIngredients } from "../../api/ingredientApi.js";

function emptyLine() {
  return { ingredientId: "", quantityRequired: "" };
}

function emptyForm() {
  return { name: "", price: "", category: "", imageUrl: "", recipeLines: [emptyLine()] };
}

/** Admin "Configure Menu Items" + "Define Recipe Mappings" use cases (Figure 2.1) - both
 *  were already fully backed by MenuController/MenuService, but no screen anywhere in the
 *  app ever called create/update - the only way a menu item's recipe could change was
 *  editing schema.sql directly. */
export default function MenuManagementPanel() {
  const [items, setItems] = useState([]);
  const [ingredients, setIngredients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm());
  const [formBusy, setFormBusy] = useState(false);
  const [formError, setFormError] = useState(null);
  const [rowBusyId, setRowBusyId] = useState(null);

  const load = () => {
    setLoading(true);
    Promise.all([fetchAllMenuItems(), fetchIngredients()]).then(([menuItems, ings]) => {
      setItems(menuItems);
      setIngredients(ings);
      setLoading(false);
    });
  };

  useEffect(load, []);

  const updateLine = (index, field, value) => {
    setForm((f) => ({
      ...f,
      recipeLines: f.recipeLines.map((line, i) => (i === index ? { ...line, [field]: value } : line)),
    }));
  };
  const addLine = () => setForm((f) => ({ ...f, recipeLines: [...f.recipeLines, emptyLine()] }));
  const removeLine = (index) =>
    setForm((f) => ({ ...f, recipeLines: f.recipeLines.filter((_, i) => i !== index) }));

  const startCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setFormError(null);
    setShowForm(true);
  };

  const startEdit = async (id) => {
    setFormError(null);
    const detail = await fetchMenuItemDetail(id);
    setEditingId(id);
    setForm({
      name: detail.name,
      price: String(detail.price),
      category: detail.category,
      imageUrl: detail.imageUrl || "",
      recipeLines: detail.recipeLines.length > 0
        ? detail.recipeLines.map((l) => ({ ingredientId: String(l.ingredientId), quantityRequired: String(l.quantityRequired) }))
        : [emptyLine()],
    });
    setShowForm(true);
  };

  const canSubmit =
    form.name.trim() &&
    Number(form.price) >= 0 &&
    form.category.trim() &&
    form.recipeLines.every((l) => l.ingredientId && Number(l.quantityRequired) > 0);

  const submit = async () => {
    setFormError(null);
    setFormBusy(true);
    const payload = {
      name: form.name,
      price: Number(form.price),
      category: form.category,
      imageUrl: form.imageUrl || null,
      recipeLines: form.recipeLines.map((l) => ({
        ingredientId: Number(l.ingredientId),
        quantityRequired: Number(l.quantityRequired),
      })),
    };
    try {
      if (editingId) {
        const updated = await updateMenuItem(editingId, payload);
        setItems((prev) => prev.map((i) => (i.id === editingId ? updated : i)));
      } else {
        const created = await createMenuItem(payload);
        setItems((prev) => [...prev, created]);
      }
      setShowForm(false);
      setForm(emptyForm());
      setEditingId(null);
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not save this menu item.");
    } finally {
      setFormBusy(false);
    }
  };

  const toggleAvailability = async (item) => {
    setRowBusyId(item.id);
    try {
      const updated = await setMenuItemAvailability(item.id, !item.isAvailable);
      setItems((prev) => prev.map((i) => (i.id === item.id ? updated : i)));
    } finally {
      setRowBusyId(null);
    }
  };

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-display text-sm font-bold text-white">Menu &amp; Recipes</h2>
        {!showForm && (
          <button
            onClick={startCreate}
            className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
          >
            + New Menu Item
          </button>
        )}
      </div>

      {showForm && (
        <div className="mb-4 rounded-lg border border-ink-700 bg-ink-900 p-4">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">
            {editingId ? "Edit Menu Item" : "New Menu Item"}
          </h3>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <input
              type="text"
              placeholder="Item name"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="number"
              min="0"
              step="0.01"
              placeholder="Price (LKR)"
              value={form.price}
              onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="text"
              placeholder="Category (e.g. Kottu, Dessert)"
              value={form.category}
              onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="text"
              placeholder="Image URL (optional)"
              value={form.imageUrl}
              onChange={(e) => setForm((f) => ({ ...f, imageUrl: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
          </div>

          <div className="mt-3 space-y-2">
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Recipe Mapping</div>
            {form.recipeLines.map((line, i) => (
              <div key={i} className="grid grid-cols-1 gap-2 sm:grid-cols-[2fr_1fr_auto]">
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
                  placeholder="Qty per unit sold"
                  value={line.quantityRequired}
                  onChange={(e) => updateLine(i, "quantityRequired", e.target.value)}
                  className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
                />
                <button
                  onClick={() => removeLine(i)}
                  disabled={form.recipeLines.length === 1}
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
              {formBusy ? "Saving..." : editingId ? "Save Changes" : "Create Menu Item"}
            </button>
            <button
              onClick={() => { setShowForm(false); setEditingId(null); setForm(emptyForm()); }}
              className="min-h-11 rounded-full border border-ink-700 px-4 text-xs text-slate-400 hover:bg-ink-700"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {loading && <div className="py-6 text-center text-sm text-slate-500">Loading...</div>}

      {!loading && (
        <div className="overflow-hidden rounded-lg border border-ink-700">
          <table className="w-full text-left text-sm">
            <thead className="bg-ink-900 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-3 py-1.5">Name</th>
                <th className="px-3 py-1.5">Category</th>
                <th className="px-3 py-1.5">Price</th>
                <th className="px-3 py-1.5">Availability</th>
                <th className="px-3 py-1.5" />
              </tr>
            </thead>
            <tbody>
              {items.map((item, i) => (
                <tr key={item.id} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                  <td className="px-3 py-1.5 text-white">{item.name}</td>
                  <td className="px-3 py-1.5 text-slate-400">{item.category}</td>
                  <td className="px-3 py-1.5 font-mono text-slate-300">LKR {Number(item.price).toFixed(2)}</td>
                  <td className="px-3 py-1.5">
                    <button
                      disabled={rowBusyId === item.id}
                      onClick={() => toggleAvailability(item)}
                      className={`min-h-11 rounded-full px-3 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-50 ${
                        item.isAvailable
                          ? "bg-status-available/20 text-status-available"
                          : "bg-status-occupied/20 text-status-occupied"
                      }`}
                    >
                      {item.isAvailable ? "Available" : "Unavailable"}
                    </button>
                  </td>
                  <td className="px-3 py-1.5">
                    <button
                      onClick={() => startEdit(item.id)}
                      className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-accent hover:text-accent"
                    >
                      Edit
                    </button>
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
