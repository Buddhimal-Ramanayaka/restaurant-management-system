import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext.jsx";
import { fetchUsers, createUser, updateUser } from "../../api/userApi.js";

const ROLES = ["ADMIN", "MANAGER", "WAITER", "KITCHEN", "CASHIER"];

function emptyForm() {
  return { username: "", password: "", fullName: "", role: "WAITER" };
}

/** Admin "Manage Users & Roles" use case (Figure 2.1) - previously had no UI or backend at all. */
export default function UserManagementPanel() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm());
  const [formBusy, setFormBusy] = useState(false);
  const [formError, setFormError] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [rowError, setRowError] = useState(null);

  const load = () => {
    setLoading(true);
    fetchUsers().then(setUsers).finally(() => setLoading(false));
  };

  useEffect(load, []);

  const canSubmit = form.username.trim() && form.password.trim() && form.fullName.trim() && form.role;

  const submit = async () => {
    setFormError(null);
    setFormBusy(true);
    try {
      const created = await createUser(form);
      setUsers((prev) => [...prev, created]);
      setShowForm(false);
      setForm(emptyForm());
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not create this user.");
    } finally {
      setFormBusy(false);
    }
  };

  const startEdit = (u) => {
    setEditingId(u.id);
    setRowError(null);
  };

  const saveEdit = async (u, role, isActive) => {
    setRowError(null);
    try {
      const updated = await updateUser(u.id, { fullName: u.fullName, role, isActive });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? updated : x)));
      setEditingId(null);
    } catch (err) {
      setRowError(err.response?.data?.message || "Could not update this user.");
    }
  };

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-display text-sm font-bold text-white">Users &amp; Roles</h2>
        {!showForm && (
          <button
            onClick={() => setShowForm(true)}
            className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
          >
            + New User
          </button>
        )}
      </div>

      {showForm && (
        <div className="mb-4 rounded-lg border border-ink-700 bg-ink-900 p-4">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">New User</h3>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <input
              type="text"
              placeholder="Username"
              value={form.username}
              onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="password"
              placeholder="Password"
              value={form.password}
              onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="text"
              placeholder="Full name"
              value={form.fullName}
              onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <select
              value={form.role}
              onChange={(e) => setForm((f) => ({ ...f, role: e.target.value }))}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-800 px-3 text-sm text-white focus:border-accent focus:outline-none"
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          </div>

          {formError && <p className="mt-2 text-xs text-status-occupied">{formError}</p>}
          <div className="mt-3 flex gap-2">

            <button
              disabled={!canSubmit || formBusy}
              onClick={submit}
              className="min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
            >
              {formBusy ? "Creating..." : "Create User"}
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
                <th className="px-3 py-1.5">Username</th>
                <th className="px-3 py-1.5">Full Name</th>
                <th className="px-3 py-1.5">Role</th>
                <th className="px-3 py-1.5">Status</th>
                <th className="px-3 py-1.5" />
              </tr>
            </thead>
            <tbody>
              {users.map((u, i) => {
                const isSelf = u.id === currentUser.userId;
                const isEditing = editingId === u.id;
                return (
                  <UserRow
                    key={u.id}
                    user={u}
                    striped={i % 2 === 0}
                    isSelf={isSelf}
                    isEditing={isEditing}
                    onEdit={() => startEdit(u)}
                    onCancel={() => setEditingId(null)}
                    onSave={(role, isActive) => saveEdit(u, role, isActive)}
                  />
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function UserRow({ user, striped, isSelf, isEditing, onEdit, onCancel, onSave }) {
  const [role, setRole] = useState(user.role);
  const [isActive, setIsActive] = useState(user.isActive);

  if (isEditing) {
    return (
      <tr className={striped ? "bg-ink-900" : "bg-ink-800/50"}>
        <td className="px-3 py-1.5 text-white">{user.username}</td>
        <td className="px-3 py-1.5 text-slate-300">{user.fullName}</td>
        <td className="px-3 py-1.5">
          <select
            value={role}
            onChange={(e) => setRole(e.target.value)}
            className="min-h-11 rounded-md border border-ink-700 bg-ink-900 px-2 text-xs text-white focus:border-accent focus:outline-none"
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>
        </td>
        <td className="px-3 py-1.5">
          <label className="flex items-center gap-1.5 text-xs text-slate-300">
            <input type="checkbox" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />
            Active
          </label>
        </td>
        <td className="px-3 py-1.5">
          <div className="flex gap-2">
            <button
              onClick={() => onSave(role, isActive)}
              className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
            >
              Save
            </button>
            <button
              onClick={onCancel}
              className="min-h-11 rounded-full border border-ink-700 px-3 text-xs text-slate-400 hover:bg-ink-700"
            >
              Cancel
            </button>
          </div>
        </td>
      </tr>
    );
  }

  return (
    <tr className={striped ? "bg-ink-900" : "bg-ink-800/50"}>
      <td className="px-3 py-1.5 text-white">{user.username}</td>
      <td className="px-3 py-1.5 text-slate-300">{user.fullName}</td>
      <td className="px-3 py-1.5 text-slate-300">{user.role}</td>
      <td className="px-3 py-1.5">
        <span className={user.isActive ? "text-status-available" : "text-status-occupied"}>
          {user.isActive ? "Active" : "Inactive"}
        </span>
      </td>
      <td className="px-3 py-1.5">
        {isSelf ? (
          <span className="text-xs text-slate-600">(you)</span>
        ) : (
          <button
            onClick={onEdit}
            className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-accent hover:text-accent"
          >
            Edit
          </button>
        )}
      </td>
    </tr>
  );
}
