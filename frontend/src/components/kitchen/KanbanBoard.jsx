import KanbanLane from "./KanbanLane.jsx";

export default function KanbanBoard({ orders, onStartCooking, onMarkLineDone, onMarkAllReady }) {
  const lanes = ["PENDING", "PREPARING", "READY"];
  return (
    <div className="grid flex-1 grid-cols-1 gap-4 overflow-hidden p-4 md:grid-cols-3">
      {lanes.map((status) => (
        <KanbanLane
          key={status}
          status={status}
          orders={orders.filter((o) => o.status === status)}
          onStartCooking={onStartCooking}
          onMarkLineDone={onMarkLineDone}
          onMarkAllReady={onMarkAllReady}
        />
      ))}
    </div>
  );
}
