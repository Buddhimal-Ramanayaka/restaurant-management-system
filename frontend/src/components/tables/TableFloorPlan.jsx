const STATUS_COLORS = {
  AVAILABLE: "border-status-available/60 bg-status-available/10 text-status-available",
  OCCUPIED: "border-status-occupied/60 bg-status-occupied/10 text-status-occupied",
  BILLED: "border-status-billed/60 bg-status-billed/10 text-status-billed",
  CLEANING: "border-status-cleaning/60 bg-status-cleaning/10 text-status-cleaning",
  RESERVED: "border-status-reserved/60 bg-status-reserved/10 text-status-reserved",
};

export default function TableFloorPlan({ tables, onSelect, onMarkAvailable, onViewOrder }) {
  return (
    <div className="grid grid-cols-3 gap-3 p-4 sm:grid-cols-4 md:grid-cols-5">
      {tables.map((table) => {
        const isCleaning = table.operationalStatus === "CLEANING";
        const isAvailable = table.operationalStatus === "AVAILABLE";
        const isOccupied = table.operationalStatus === "OCCUPIED";
        const isActionable = isAvailable || isCleaning || isOccupied;
        return (
          <button
            key={table.id}
            onClick={() => {
              if (isAvailable) onSelect(table);
              else if (isCleaning) onMarkAvailable(table.id);
              else if (isOccupied) onViewOrder(table);
            }}
            disabled={!isActionable}
            className={`flex flex-col items-center justify-center gap-1 rounded-xl border-2 py-6 transition ${
              STATUS_COLORS[table.operationalStatus]
            } ${isActionable ? "hover:scale-[1.03] cursor-pointer" : "cursor-not-allowed"}`}
          >
            <span className="font-display text-lg font-bold">{table.tableNumber}</span>
            <span className="text-[10px] uppercase tracking-wide">{table.operationalStatus}</span>
            <span className="text-[10px] text-slate-400">
              {isCleaning ? "Tap when ready" : isOccupied ? "Tap to view order" : `${table.seatingCapacity} seats`}
            </span>
          </button>
        );
      })}
    </div>
  );
}
