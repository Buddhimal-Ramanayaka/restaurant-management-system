import { useAuth } from "../../context/AuthContext";

export default function Navbar({ title, right }) {
  const { user, logout } = useAuth();

  return (
    <header className="flex items-center justify-between border-b border-ink-700 bg-ink-800 px-4 py-3">
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-md bg-accent font-display font-bold text-ink-950">
          R
        </div>
        <div>
          <div className="font-display text-lg font-bold text-white leading-none">RMS</div>
          <div className="text-xs text-slate-400 leading-none mt-0.5">{title}</div>
        </div>
      </div>

      <div className="flex items-center gap-4">
        {right}
        {user && (
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-ink-700 text-xs font-semibold text-slate-200">
              {user.username?.slice(0, 2).toUpperCase()}
            </div>
            <div className="text-sm text-slate-300 hidden sm:block">
              {user.username} <span className="text-slate-500">({user.role})</span>
            </div>
            <button
              onClick={logout}
              className="min-h-11 rounded-md border border-ink-700 px-3 text-xs text-slate-300 hover:bg-ink-700"
            >
              Log out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
