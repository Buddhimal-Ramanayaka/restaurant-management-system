import { useEffect, useState } from "react";
import {
  fetchUpcomingReservations,
  createReservation,
  checkInReservation,
  cancelReservation,
} from "../../api/reservationApi.js";

function formatDateTime(iso) {
  return new Date(iso).toLocaleString("en-LK", {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// Local datetime input needs "YYYY-MM-DDTHH:mm" with no timezone suffix - one hour from
// now, rounded, so the default in the form is never rejected as already in the past.
function defaultReservationTime() {
  const d = new Date(Date.now() + 60 * 60 * 1000);
  d.setSeconds(0, 0);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export default function ReservationsPanel({ tables, onBack }) {
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState(null);

  const [customerName, setCustomerName] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [tableId, setTableId] = useState("");
  const [reservationTime, setReservationTime] = useState(defaultReservationTime());
  const [partySize, setPartySize] = useState(2);
  const [formBusy, setFormBusy] = useState(false);
  const [formError, setFormError] = useState(null);

  const availableTables = tables.filter((t) => t.operationalStatus === "AVAILABLE");

  const load = () => {
    setLoading(true);
    fetchUpcomingReservations()
      .then(setReservations)
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const runAction = async (id, action) => {
    setBusyId(id);
    setError(null);
    try {
      await action(id);
      setReservations((prev) => prev.filter((r) => r.id !== id));
    } catch (err) {
      setError(err.response?.data?.message || "Action failed - please try again.");
    } finally {
      setBusyId(null);
    }
  };

  const submitForm = async () => {
    setFormError(null);
    setFormBusy(true);
    try {
      const created = await createReservation({
        customerName,
        customerPhone,
        tableId: Number(tableId),
        reservationTime,
        partySize: Number(partySize),
      });
      setReservations((prev) => [...prev, created].sort((a, b) => a.reservationTime.localeCompare(b.reservationTime)));
      setShowForm(false);
      setCustomerName("");
      setCustomerPhone("");
      setTableId("");
      setPartySize(2);
      setReservationTime(defaultReservationTime());
    } catch (err) {
      setFormError(err.response?.data?.message || "Could not create the reservation.");
    } finally {
      setFormBusy(false);
    }
  };

  const canSubmit = customerName && customerPhone && tableId && reservationTime && partySize > 0;

  return (
    <div className="flex-1 overflow-y-auto p-4">
      <div className="mb-3 flex items-center justify-between">
        {onBack && (
          <button onClick={onBack} className="min-h-11 px-2 text-xs text-slate-400 hover:text-white">
            ← Back to floor plan
          </button>
        )}
        {!showForm && (
          <button
            onClick={() => setShowForm(true)}
            className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
          >
            + New Reservation
          </button>
        )}
      </div>

      <h2 className="mb-3 font-display text-sm font-bold text-white">Upcoming Reservations</h2>

      {error && (
        <div className="mb-3 rounded-lg border-l-4 border-status-occupied bg-status-occupied/10 p-2 text-xs text-status-occupied">
          {error}
        </div>
      )}

      {showForm && (
        <div className="mb-4 rounded-lg border border-ink-700 bg-ink-800 p-4">
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">New Reservation</h3>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <input
              type="text"
              placeholder="Customer name"
              value={customerName}
              onChange={(e) => setCustomerName(e.target.value)}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="text"
              placeholder="Phone number"
              value={customerPhone}
              onChange={(e) => setCustomerPhone(e.target.value)}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <select
              value={tableId}
              onChange={(e) => setTableId(e.target.value)}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
            >
              <option value="">Select an available table</option>
              {availableTables.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.tableNumber} ({t.seatingCapacity} seats)
                </option>
              ))}
            </select>
            <input
              type="number"
              min="1"
              placeholder="Party size"
              value={partySize}
              onChange={(e) => setPartySize(e.target.value)}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="datetime-local"
              value={reservationTime}
              onChange={(e) => setReservationTime(e.target.value)}
              className="min-h-11 rounded-md border border-ink-700 bg-ink-900 px-3 text-sm text-white focus:border-accent focus:outline-none sm:col-span-2"
            />
          </div>
          {availableTables.length === 0 && (
            <p className="mt-2 text-xs text-status-cleaning">No tables are currently available to reserve.</p>
          )}
          {formError && <p className="mt-2 text-xs text-status-occupied">{formError}</p>}
          <div className="mt-3 flex gap-2">
            <button
              disabled={!canSubmit || formBusy}
              onClick={submitForm}
              className="min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
            >
              {formBusy ? "Booking..." : "Book Reservation"}
            </button>
            <button
              onClick={() => setShowForm(false)}
              className="min-h-11 rounded-full border border-ink-700 px-4 text-xs text-slate-400 hover:bg-ink-700"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {loading && <div className="py-6 text-center text-sm text-slate-500">Loading...</div>}

      {!loading && reservations.length === 0 && (
        <div className="py-6 text-center text-sm text-slate-500">No upcoming reservations.</div>
      )}

      <div className="space-y-2">
        {reservations.map((r) => {
          const isBusy = busyId === r.id;
          return (
            <div key={r.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-ink-700 bg-ink-800 p-3">
              <div>
                <div className="text-sm font-semibold text-white">
                  {r.customerName}{" "}
                  <span className="rounded-full bg-status-reserved/20 px-2 py-0.5 text-[10px] font-medium text-status-reserved">
                    Table {r.tableNumber}
                  </span>
                </div>
                <div className="text-xs text-slate-400">
                  {formatDateTime(r.reservationTime)} - party of {r.partySize} - {r.customerPhone}
                </div>
              </div>
              <div className="flex gap-2">
                <button
                  disabled={isBusy}
                  onClick={() => runAction(r.id, checkInReservation)}
                  className="min-h-11 rounded-full bg-status-available px-3 text-xs font-semibold text-ink-950 transition disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {isBusy ? "..." : "Check In"}
                </button>
                <button
                  disabled={isBusy}
                  onClick={() => runAction(r.id, cancelReservation)}
                  className="min-h-11 rounded-full border border-ink-700 px-3 text-xs text-slate-400 hover:border-status-occupied hover:text-status-occupied disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Cancel
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
