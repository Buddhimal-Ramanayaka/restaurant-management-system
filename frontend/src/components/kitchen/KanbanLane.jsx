import TicketCard from "./TicketCard.jsx";

const LANE_META = {
  PENDING: { label: "New", dot: "bg-status-cleaning" },
  PREPARING: { label: "Preparing", dot: "bg-status-billed" },
  READY: { label: "Ready", dot: "bg-status-available" },
};

export default function KanbanLane({ status, orders, onStartCooking, onMarkLineDone, onMarkAllReady }) {
  const meta = LANE_META[status];
  return (
    <div className="flex w-full flex-1 flex-col rounded-lg bg-ink-800/60">
      <div className="flex items-center justify-between border-b border-ink-700 px-4 py-3">
        <div className="flex items-center gap-2">
          <span className={`h-2.5 w-2.5 rounded-full ${meta.dot}`} />
          <span className="font-display text-sm font-bold text-white">{meta.label}</span>
        </div>
        <span className="rounded-full bg-ink-700 px-2 py-0.5 text-xs text-slate-300">
          {orders.length}
        </span>
      </div>
      <div className="flex-1 space-y-3 overflow-y-auto p-3">
        {orders.length === 0 && (
          <p className="pt-6 text-center text-xs text-slate-600">No tickets</p>
        )}
        {orders.map((order) => (
          <TicketCard
            key={order.id}
            order={order}
            onStartCooking={onStartCooking}
            onMarkLineDone={onMarkLineDone}
            onMarkAllReady={onMarkAllReady}
          />
        ))}
      </div>
    </div>
  );
}
