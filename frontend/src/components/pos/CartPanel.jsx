import { useState } from "react";
import { lookupCustomer, registerCustomer } from "../../api/customerApi.js";

export default function CartPanel({
  tableNumber,
  items,
  totals,
  rates,
  promotion,
  onCustomerFound,
  onAdjustQty,
  onSetNotes,
  onClear,
  onSubmit,
  submitting,
  error,
}) {
  const [customerPhone, setCustomerPhone] = useState("");
  const [foundCustomer, setFoundCustomer] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [registerName, setRegisterName] = useState("");
  const [busy, setBusy] = useState(false);
  const [lookupError, setLookupError] = useState(null);

  async function handleSearch() {
    if (!customerPhone.trim()) return;
    setBusy(true);
    setLookupError(null);
    setFoundCustomer(null);
    setNotFound(false);
    try {
      const customer = await lookupCustomer(customerPhone.trim());
      if (customer) {
        setFoundCustomer(customer);
        onCustomerFound?.(customer);
      } else {
        setNotFound(true);
        onCustomerFound?.(null);
      }
    } catch (err) {
      setLookupError(err.response?.data?.message || "Could not look up this customer.");
    } finally {
      setBusy(false);
    }
  }

  async function handleRegister() {
    if (!registerName.trim()) return;
    setBusy(true);
    setLookupError(null);
    try {
      const customer = await registerCustomer(registerName.trim(), customerPhone.trim());
      setFoundCustomer(customer);
      setNotFound(false);
      onCustomerFound?.(customer);
    } catch (err) {
      setLookupError(err.response?.data?.message || "Could not register this customer.");
    } finally {
      setBusy(false);
    }
  }

  function handlePhoneChange(value) {
    setCustomerPhone(value);
    setFoundCustomer(null);
    setNotFound(false);
    setLookupError(null);
    onCustomerFound?.(null);
  }

  return (
    <aside className="flex w-full max-w-md flex-col border-l border-ink-700 bg-ink-800">
      <div className="flex items-center justify-between border-b border-ink-700 px-4 py-3">
        <h2 className="font-display text-base font-bold text-white">Order Cart</h2>
        {tableNumber && (
          <span className="rounded-full bg-ink-700 px-3 py-1 text-xs text-accent">
            Table: {tableNumber}
          </span>
        )}
      </div>

      <div className="flex-1 space-y-3 overflow-y-auto p-4">
        {items.length === 0 && (
          <p className="pt-8 text-center text-sm text-slate-500">Cart is empty - tap an item to add it.</p>
        )}
        {items.map((line) => (
          <div key={line.menuItemId} className="rounded-lg bg-ink-700 p-3">
            <div className="flex items-start justify-between">
              <div>
                <div className="text-sm font-semibold text-white">{line.name}</div>
                <div className="text-xs text-slate-400">LKR {line.price.toFixed(2)} each</div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => onAdjustQty(line.menuItemId, -1)}
                  className="h-11 w-11 rounded bg-ink-900 text-white hover:bg-ink-950"
                >
                  −
                </button>
                <span className="w-6 text-center font-mono text-sm font-bold text-white">
                  {line.quantity}
                </span>
                <button
                  onClick={() => onAdjustQty(line.menuItemId, 1)}
                  className="h-11 w-11 rounded bg-ink-900 text-white hover:bg-ink-950"
                >
                  +
                </button>
              </div>
            </div>
            <input
              type="text"
              placeholder="Special note: no spicy, extra rice..."
              value={line.specialNotes}
              onChange={(e) => onSetNotes(line.menuItemId, e.target.value)}
              className="mt-2 min-h-11 w-full rounded bg-ink-900 px-2 text-xs text-slate-300 outline-none placeholder:text-slate-600"
            />
            <div className="mt-1 text-right font-mono text-xs font-semibold text-accent">
              LKR {(line.price * line.quantity).toFixed(2)}
            </div>
          </div>
        ))}
      </div>

      <div className="border-t border-ink-700 p-4">
        <div className="mb-2 flex gap-2">
          <input
            type="text"
            placeholder="Customer phone (optional CRM lookup)"
            value={customerPhone}
            onChange={(e) => handlePhoneChange(e.target.value)}
            className="min-h-11 flex-1 rounded-md border border-ink-700 bg-ink-900 px-3 py-2 text-xs text-slate-200 outline-none focus:border-accent"
          />
          <button
            onClick={handleSearch}
            disabled={!customerPhone.trim() || busy}
            className="min-h-11 min-w-11 rounded-md border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-accent hover:text-accent disabled:cursor-not-allowed disabled:opacity-40"
          >
            {busy ? "..." : "Search"}
          </button>
        </div>

        {lookupError && <p className="mb-2 text-xs text-status-occupied">{lookupError}</p>}

        {foundCustomer && (
          <div className="mb-3 flex items-center justify-between rounded-md border border-status-available/40 bg-status-available/10 px-3 py-2 text-xs">
            <span className="text-white">{foundCustomer.name}</span>
            <span className="rounded-full bg-status-reserved/20 px-2 py-0.5 font-semibold text-status-reserved">
              {foundCustomer.loyaltyTier} · {foundCustomer.visitCount} visits
            </span>
          </div>
        )}

        {notFound && (
          <div className="mb-3 rounded-md border border-ink-700 bg-ink-900 p-2">
            <p className="mb-2 text-xs text-slate-400">No customer found for this number.</p>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Name to register"
                value={registerName}
                onChange={(e) => setRegisterName(e.target.value)}
                className="min-h-11 flex-1 rounded-md border border-ink-700 bg-ink-800 px-3 py-2 text-xs text-slate-200 outline-none focus:border-accent"
              />
              <button
                onClick={handleRegister}
                disabled={!registerName.trim() || busy}
                className="min-h-11 rounded-md bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-40"
              >
                Register
              </button>
            </div>
          </div>
        )}

        <div className="space-y-1 text-sm text-slate-300">
          <div className="flex justify-between">
            <span>Subtotal</span>
            <span className="font-mono">LKR {totals.subtotal.toFixed(2)}</span>
          </div>
          {totals.discount > 0 && (
            <div className="flex justify-between text-status-available">
              <span>{promotion?.name ?? "Loyalty Discount"}</span>
              <span className="font-mono">-LKR {totals.discount.toFixed(2)}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span>Service Charge{rates ? ` (${(rates.serviceChargeRate * 100).toFixed(0)}%)` : ""}</span>
            <span className="font-mono">LKR {totals.serviceCharge.toFixed(2)}</span>
          </div>
          <div className="flex justify-between">
            <span>VAT{rates ? ` (${(rates.vatRate * 100).toFixed(0)}%)` : ""}</span>
            <span className="font-mono">LKR {totals.vat.toFixed(2)}</span>
          </div>
          <div className="mt-2 flex justify-between border-t border-ink-700 pt-2 text-base font-bold text-white">
            <span>Total</span>
            <span className="font-mono text-accent">LKR {totals.total.toFixed(2)}</span>
          </div>
        </div>

        {error && <p className="mt-2 text-xs text-status-occupied">{error}</p>}

        <div className="mt-4 flex gap-2">
          <button
            onClick={onClear}
            disabled={items.length === 0}
            className="min-h-11 flex-1 rounded-md border border-ink-700 text-sm text-status-occupied disabled:opacity-30"
          >
            Clear Cart
          </button>
          <button
            onClick={() => onSubmit(customerPhone)}
            disabled={items.length === 0 || submitting}
            className="min-h-11 flex-[2] rounded-md bg-accent text-sm font-semibold text-ink-950 hover:bg-accent-soft disabled:opacity-40"
          >
            {submitting ? "Sending..." : "Send to Kitchen"}
          </button>
        </div>
      </div>
    </aside>
  );
}
