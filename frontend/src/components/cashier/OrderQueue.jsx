const STATUS_STYLES = {
  READY: { label: "Ready", classes: "bg-status-available/20 text-status-available" },
  BILLED: { label: "Bill Open", classes: "bg-status-billed/20 text-status-billed" },
};

export default function OrderQueue({ orders, selectedOrderId, onSelect }) {
  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <h2 className="mb-3 font-display text-sm font-bold text-white">Ready to Bill</h2>

      {orders.length === 0 && (
        <div className="py-6 text-center text-sm text-slate-500">No orders waiting for billing.</div>
      )}

      <div className="space-y-2">
        {orders.map((order) => {
          const style = STATUS_STYLES[order.status] ?? STATUS_STYLES.READY;
          const isSelected = order.id === selectedOrderId;
          return (
            <button
              key={order.id}
              onClick={() => onSelect(order)}
              className={`flex w-full items-center justify-between rounded-lg border p-3 text-left transition ${
                isSelected ? "border-accent bg-accent/10" : "border-ink-700 bg-ink-900 hover:border-ink-500"
              }`}
            >
              <div>
                <div className="text-sm font-semibold text-white">Table {order.tableId}</div>
                <div className="text-xs text-slate-400">
                  Order #{order.id} - {order.items.length} item{order.items.length === 1 ? "" : "s"}
                </div>
              </div>
              <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${style.classes}`}>{style.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
