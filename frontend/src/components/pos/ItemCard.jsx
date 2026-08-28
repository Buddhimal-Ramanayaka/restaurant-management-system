const STATUS_STYLES = {
  AVAILABLE: { label: "Available", classes: "bg-status-available/20 text-status-available" },
  LOW_STOCK: { label: "Low Stock", classes: "bg-status-cleaning/20 text-status-cleaning" },
  UNAVAILABLE: { label: "Unavailable", classes: "bg-ink-700 text-slate-500" },
};

export default function ItemCard({ item, onAdd }) {
  const isAvailable = item.isAvailable;
  const status = isAvailable ? "AVAILABLE" : "UNAVAILABLE";
  const style = STATUS_STYLES[status];

  return (
    <div
      className={`flex flex-col overflow-hidden rounded-xl border border-ink-700 bg-ink-800 transition ${
        !isAvailable ? "opacity-50" : "hover:border-accent/60"
      }`}
    >
      <div className="flex h-24 items-center justify-center bg-ink-700 text-4xl">🍽️</div>
      <div className="flex flex-1 flex-col gap-1 p-3">
        <div className="text-sm font-semibold text-white">{item.name}</div>
        <div className="text-xs text-slate-400">{item.category}</div>
        <div className="mt-1 flex items-center justify-between">
          <span className="font-mono text-sm font-semibold text-accent">
            LKR {Number(item.price).toFixed(2)}
          </span>
          <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${style.classes}`}>
            {style.label}
          </span>
        </div>
        <button
          disabled={!isAvailable}
          onClick={() => onAdd(item)}
          className="mt-2 min-h-11 rounded-full bg-accent text-xs font-semibold text-ink-950 transition hover:bg-accent-soft disabled:cursor-not-allowed disabled:bg-ink-700 disabled:text-slate-500"
        >
          {isAvailable ? "Add to Cart" : "Unavailable"}
        </button>
      </div>
    </div>
  );
}
