export default function CategoryTabs({ categories, active, onSelect }) {
  return (
    <div className="flex flex-wrap gap-2 px-4 py-3">
      {categories.map((cat) => (
        <button
          key={cat}
          onClick={() => onSelect(cat)}
          className={`inline-flex min-h-11 items-center rounded-full px-4 text-sm font-medium transition ${
            active === cat
              ? "bg-accent text-ink-950"
              : "bg-ink-800 text-slate-300 hover:bg-ink-700"
          }`}
        >
          {cat}
        </button>
      ))}
    </div>
  );
}
