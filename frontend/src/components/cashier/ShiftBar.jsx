import { useState } from "react";

function formatCurrency(value) {
  return `LKR ${Number(value ?? 0).toLocaleString("en-LK", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export default function ShiftBar({ shift, onCloseShift, onDismissClosed }) {
  const [declaring, setDeclaring] = useState(false);
  const [drawerAmount, setDrawerAmount] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const systemCashTotal = Number(shift.systemCashTotal ?? 0);

  const submit = async () => {
    setError(null);
    try {
      const closed = await onCloseShift(Number(drawerAmount));
      setResult(closed);
    } catch (err) {
      setError(err.response?.data?.message || "Could not close shift.");
    }
  };

  if (result) {
    const varianceOk = Math.abs(Number(result.variance)) < 0.005;
    return (
      <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
        <h2 className="mb-2 font-display text-sm font-bold text-white">Shift Closed</h2>
        <div className="grid grid-cols-2 gap-2 text-sm text-slate-300 sm:grid-cols-4">
          <div>System Cash: <span className="font-mono text-white">{formatCurrency(result.systemCashTotal)}</span></div>
          <div>Declared: <span className="font-mono text-white">{formatCurrency(result.declaredDrawerAmount)}</span></div>
          <div>
            Variance:{" "}
            <span className={`font-mono font-semibold ${varianceOk ? "text-status-available" : "text-status-occupied"}`}>
              {Number(result.variance) >= 0 ? "+" : ""}
              {formatCurrency(result.variance)}
            </span>
          </div>
          <div className="text-slate-500">Submitted for manager review.</div>
        </div>
        <button
          onClick={onDismissClosed}
          className="mt-3 min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
        >
          Start New Shift
        </button>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-4 text-sm text-slate-300">
          <span>Cash: <span className="font-mono text-white">{formatCurrency(shift.systemCashTotal)}</span></span>
          <span>Card: <span className="font-mono text-white">{formatCurrency(shift.systemCardTotal)}</span></span>
          <span>Digital: <span className="font-mono text-white">{formatCurrency(shift.systemDigitalTotal)}</span></span>
        </div>
        {!declaring && (
          <button
            onClick={() => setDeclaring(true)}
            className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-status-occupied hover:text-status-occupied"
          >
            End Shift
          </button>
        )}
      </div>

      {declaring && (
        <div className="mt-3 border-t border-ink-700 pt-3">
          <p className="mb-2 text-xs text-slate-400">
            Count your drawer and enter the total cash on hand. This is compared against the
            system-recorded cash total ({formatCurrency(systemCashTotal)}).
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="number"
              step="0.01"
              min="0"
              value={drawerAmount}
              onChange={(e) => setDrawerAmount(e.target.value)}
              placeholder="Declared drawer amount"
              className="min-h-11 w-48 rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <button
              disabled={!drawerAmount}
              onClick={submit}
              className="min-h-11 rounded-full bg-status-occupied px-3 text-xs font-semibold text-white transition disabled:cursor-not-allowed disabled:opacity-50"
            >
              Submit &amp; Close Shift
            </button>
            <button
              onClick={() => setDeclaring(false)}
              className="min-h-11 rounded-full border border-ink-700 px-3 text-xs text-slate-400 hover:bg-ink-700"
            >
              Cancel
            </button>
          </div>
          {error && <div className="mt-2 text-xs text-status-occupied">{error}</div>}
        </div>
      )}
    </div>
  );
}
