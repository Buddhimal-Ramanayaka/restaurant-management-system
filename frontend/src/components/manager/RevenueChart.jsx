import { useMemo, useState } from "react";

// Validated categorical pair (see the project's dataviz check) against this
// app's dark surface (bg-ink-900 #111827): blue/orange, CVD-safe and distinct
// from the reserved table-status palette (green/red/blue/amber/purple).
const REVENUE_COLOR = "#3987e5";
const COGS_COLOR = "#d95926";

const CHART_WIDTH = 640;
const CHART_HEIGHT = 260;
const MARGIN = { top: 12, right: 12, bottom: 28, left: 56 };
const BAR_WIDTH = 20;
const BAR_GAP = 2;
const CORNER_RADIUS = 4;

function niceCeiling(value) {
  if (value <= 0) return 100;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const steps = [1, 2, 2.5, 5, 10];
  for (const step of steps) {
    const candidate = step * magnitude;
    if (candidate >= value) return candidate;
  }
  return 10 * magnitude;
}

function roundedTopRectPath(x, y, width, height, radius) {
  if (height <= 0) return "";
  const r = Math.min(radius, width / 2, height);
  const top = y;
  const bottom = y + height;
  return [
    `M${x},${bottom}`,
    `L${x},${top + r}`,
    `Q${x},${top} ${x + r},${top}`,
    `L${x + width - r},${top}`,
    `Q${x + width},${top} ${x + width},${top + r}`,
    `L${x + width},${bottom}`,
    "Z",
  ].join(" ");
}

function formatCurrency(value) {
  return `LKR ${Number(value).toLocaleString("en-LK", { maximumFractionDigits: 0 })}`;
}

function formatDayLabel(isoDate) {
  const d = new Date(`${isoDate}T00:00:00`);
  return d.toLocaleDateString("en-LK", { weekday: "short", day: "numeric" });
}

/**
 * Grouped bar chart: daily Revenue vs COGS for the trailing window (dissertation
 * Figure 3.10). Hand-rolled SVG rather than a charting dependency - two series,
 * seven points, doesn't warrant a new library on a project that must also run
 * fully offline.
 */
export default function RevenueChart({ data }) {
  const [hoveredIndex, setHoveredIndex] = useState(null);
  const [showTable, setShowTable] = useState(false);

  const plotWidth = CHART_WIDTH - MARGIN.left - MARGIN.right;
  const plotHeight = CHART_HEIGHT - MARGIN.top - MARGIN.bottom;

  const maxValue = useMemo(() => {
    const peak = data.reduce(
      (max, d) => Math.max(max, Number(d.totalRevenue), Number(d.totalCogs)),
      0
    );
    return niceCeiling(peak * 1.1);
  }, [data]);

  const groupWidth = data.length > 0 ? plotWidth / data.length : plotWidth;
  const pairWidth = BAR_WIDTH * 2 + BAR_GAP;
  const groupPadding = Math.max((groupWidth - pairWidth) / 2, 0);

  const yTicks = 4;
  const gridLines = Array.from({ length: yTicks + 1 }, (_, i) => {
    const value = (maxValue / yTicks) * i;
    const y = MARGIN.top + plotHeight - (value / maxValue) * plotHeight;
    return { value, y };
  });

  const hovered = hoveredIndex !== null ? data[hoveredIndex] : null;
  const hoveredX = hoveredIndex !== null ? MARGIN.left + groupWidth * hoveredIndex + groupWidth / 2 : 0;

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-display text-sm font-bold text-white">Revenue vs. COGS - Last 7 Days</h2>
        <div className="flex items-center gap-4 text-xs text-slate-300">
          <span className="flex items-center gap-1.5">
            <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: REVENUE_COLOR }} />
            Revenue
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: COGS_COLOR }} />
            COGS
          </span>
        </div>
      </div>

      <div className="relative">
        <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} className="w-full" role="img" aria-label="Daily revenue versus cost of goods sold, last 7 days">
          {gridLines.map(({ value, y }) => (
            <g key={value}>
              <line x1={MARGIN.left} y1={y} x2={CHART_WIDTH - MARGIN.right} y2={y} stroke="#242e42" strokeWidth={1} />
              <text x={MARGIN.left - 8} y={y + 3} textAnchor="end" className="fill-slate-500" fontSize={9}>
                {value >= 1000 ? `${Math.round(value / 1000)}k` : Math.round(value)}
              </text>
            </g>
          ))}

          {data.map((day, i) => {
            const groupX = MARGIN.left + groupWidth * i;
            const revenueHeight = (Number(day.totalRevenue) / maxValue) * plotHeight;
            const cogsHeight = (Number(day.totalCogs) / maxValue) * plotHeight;
            const baseY = MARGIN.top + plotHeight;
            const revenueX = groupX + groupPadding;
            const cogsX = revenueX + BAR_WIDTH + BAR_GAP;
            const isHovered = hoveredIndex === i;

            return (
              <g
                key={day.date}
                onMouseEnter={() => setHoveredIndex(i)}
                onMouseLeave={() => setHoveredIndex(null)}
                onFocus={() => setHoveredIndex(i)}
                onBlur={() => setHoveredIndex(null)}
                tabIndex={0}
                style={{ cursor: "pointer", outline: "none" }}
              >
                {/* Hit area covers the full plot height for the group, per the "hit target
                    bigger than the mark" rule - the bars alone are too thin a target. */}
                <rect x={groupX} y={MARGIN.top} width={groupWidth} height={plotHeight} fill="transparent" />
                <path
                  d={roundedTopRectPath(revenueX, baseY - revenueHeight, BAR_WIDTH, revenueHeight, CORNER_RADIUS)}
                  fill={REVENUE_COLOR}
                  opacity={isHovered || hoveredIndex === null ? 1 : 0.45}
                />
                <path
                  d={roundedTopRectPath(cogsX, baseY - cogsHeight, BAR_WIDTH, cogsHeight, CORNER_RADIUS)}
                  fill={COGS_COLOR}
                  opacity={isHovered || hoveredIndex === null ? 1 : 0.45}
                />
                <text
                  x={groupX + groupWidth / 2}
                  y={CHART_HEIGHT - MARGIN.bottom + 14}
                  textAnchor="middle"
                  className={isHovered ? "fill-white" : "fill-slate-500"}
                  fontSize={9}
                >
                  {formatDayLabel(day.date)}
                </text>
              </g>
            );
          })}
        </svg>

        {hovered && (
          <div
            className="pointer-events-none absolute top-0 -translate-x-1/2 rounded-md border border-ink-700 bg-ink-950 px-3 py-2 text-xs shadow-lg"
            style={{ left: `${(hoveredX / CHART_WIDTH) * 100}%` }}
          >
            <div className="mb-1 font-semibold text-white">{formatDayLabel(hovered.date)}</div>
            <div className="flex items-center gap-2">
              <span className="inline-block h-0.5 w-3" style={{ backgroundColor: REVENUE_COLOR }} />
              <span className="text-slate-300">Revenue</span>
              <span className="font-mono font-semibold text-white">{formatCurrency(hovered.totalRevenue)}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="inline-block h-0.5 w-3" style={{ backgroundColor: COGS_COLOR }} />
              <span className="text-slate-300">COGS</span>
              <span className="font-mono font-semibold text-white">{formatCurrency(hovered.totalCogs)}</span>
            </div>
          </div>
        )}
      </div>

      <button
        onClick={() => setShowTable((v) => !v)}
        className="mt-2 min-h-11 px-1 text-xs font-medium text-accent hover:text-accent-soft"
      >
        {showTable ? "Hide table" : "View as table"}
      </button>

      {showTable && (
        <div className="mt-2 overflow-hidden rounded-lg border border-ink-700">
          <table className="w-full text-left text-xs">
            <thead className="bg-ink-900 uppercase text-slate-400">
              <tr>
                <th className="px-3 py-1.5">Date</th>
                <th className="px-3 py-1.5">Revenue</th>
                <th className="px-3 py-1.5">COGS</th>
                <th className="px-3 py-1.5">Gross Profit</th>
              </tr>
            </thead>
            <tbody>
              {data.map((day, i) => (
                <tr key={day.date} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                  <td className="px-3 py-1.5 text-white">{formatDayLabel(day.date)}</td>
                  <td className="px-3 py-1.5 font-mono text-slate-300">{formatCurrency(day.totalRevenue)}</td>
                  <td className="px-3 py-1.5 font-mono text-slate-300">{formatCurrency(day.totalCogs)}</td>
                  <td className="px-3 py-1.5 font-mono text-slate-300">{formatCurrency(day.grossProfit)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
