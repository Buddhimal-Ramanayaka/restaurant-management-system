const ACCENT_CLASSES = {
  accent: "text-accent",
  blue: "text-[#3987e5]",
  orange: "text-[#d95926]",
  slate: "text-slate-300",
};

/**
 * Stat-tile contract: label, value (already formatted by the caller), and an
 * optional colored accent - identity for the number comes from a swatch/dot
 * beside it, never from coloring the number's own text hue arbitrarily.
 */
export default function KpiCard({ label, value, accent = "accent" }) {
  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <div className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</div>
      <div className={`mt-2 font-display text-2xl font-bold ${ACCENT_CLASSES[accent] ?? ACCENT_CLASSES.accent}`}>
        {value}
      </div>
    </div>
  );
}
