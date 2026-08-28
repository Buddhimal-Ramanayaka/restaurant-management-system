import { useEffect, useState } from "react";
import { fetchBillPdf } from "../../api/billingApi.js";
import { downloadBlob } from "../../utils/downloadBlob.js";

function formatCurrency(value) {
  return `LKR ${Number(value ?? 0).toLocaleString("en-LK", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const PAYMENT_METHODS = ["CASH", "CARD", "DIGITAL"];

export default function BillPanel({ order, bill, rates, onApplyDiscount, onSettle, onPrint }) {
  const [showDiscountForm, setShowDiscountForm] = useState(false);
  const [discountPercent, setDiscountPercent] = useState("");
  const [managerUsername, setManagerUsername] = useState("");
  const [managerPassword, setManagerPassword] = useState("");
  const [discountBusy, setDiscountBusy] = useState(false);
  const [discountError, setDiscountError] = useState(null);
  const [pdfBusy, setPdfBusy] = useState(false);

  const [paymentMethod, setPaymentMethod] = useState("CASH");
  const [tenderedAmount, setTenderedAmount] = useState("");
  const [settleBusy, setSettleBusy] = useState(false);
  const [settleError, setSettleError] = useState(null);

  // Reset the payment form whenever a fresh bill is shown - a discount just changed the
  // total, or the cashier switched to a different order entirely.
  useEffect(() => {
    setTenderedAmount(bill ? Number(bill.total).toFixed(2) : "");
    setSettleError(null);
  }, [bill?.total, order?.id]);

  if (!order || !bill) {
    return (
      <div className="flex h-full items-center justify-center rounded-lg border border-ink-700 bg-ink-800 p-8 text-sm text-slate-500">
        Select an order from the queue to open its bill.
      </div>
    );
  }

  const change = paymentMethod === "CASH" ? Number(tenderedAmount || 0) - Number(bill.total) : 0;
  const canSettle = paymentMethod !== "CASH" || Number(tenderedAmount || 0) >= Number(bill.total);

  const submitDiscount = async () => {
    setDiscountError(null);
    setDiscountBusy(true);
    try {
      await onApplyDiscount(Number(discountPercent), managerUsername, managerPassword);
      setShowDiscountForm(false);
      setManagerPassword("");
    } catch (err) {
      setDiscountError(err.response?.data?.message || "Could not verify manager credentials.");
    } finally {
      setDiscountBusy(false);
    }
  };

  const submitSettle = async () => {
    setSettleError(null);
    setSettleBusy(true);
    try {
      await onSettle({ paymentMethod, amount: Number(tenderedAmount) });
    } catch (err) {
      setSettleError(err.response?.data?.message || "Payment could not be settled.");
    } finally {
      setSettleBusy(false);
    }
  };

  return (
    <div className="rounded-lg border border-ink-700 bg-ink-800 p-4">
      <div className="mb-3 flex items-center justify-between">
        <div>
          <h2 className="font-display text-sm font-bold text-white">Order #{order.id} - Table {order.tableId}</h2>
        </div>
        <div className="flex gap-3">
          <button onClick={onPrint} className="min-h-11 px-2 text-xs font-medium text-accent hover:text-accent-soft">
            Print Bill
          </button>
          <button
            disabled={pdfBusy}
            onClick={async () => {
              setPdfBusy(true);
              try {
                const blob = await fetchBillPdf(order.id);
                downloadBlob(blob, `order-${order.id}-bill.pdf`);
              } finally {
                setPdfBusy(false);
              }
            }}
            className="min-h-11 px-2 text-xs font-medium text-accent hover:text-accent-soft disabled:opacity-50"
          >
            {pdfBusy ? "Preparing..." : "Download PDF"}
          </button>
        </div>
      </div>

      <div className="overflow-hidden rounded-lg border border-ink-700">
        <table className="w-full text-left text-sm">
          <thead className="bg-ink-900 text-xs uppercase text-slate-400">
            <tr>
              <th className="px-3 py-1.5">Item</th>
              <th className="px-3 py-1.5">Qty</th>
              <th className="px-3 py-1.5">Unit Price</th>
              <th className="px-3 py-1.5">Line Total</th>
            </tr>
          </thead>
          <tbody>
            {bill.lines.map((line, i) => (
              <tr key={i} className={i % 2 === 0 ? "bg-ink-900" : "bg-ink-800/50"}>
                <td className="px-3 py-1.5 text-white">{line.menuItemName}</td>
                <td className="px-3 py-1.5 font-mono text-slate-300">{line.quantity}</td>
                <td className="px-3 py-1.5 font-mono text-slate-300">{formatCurrency(line.unitPrice)}</td>
                <td className="px-3 py-1.5 font-mono text-slate-300">{formatCurrency(line.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-3 space-y-1 text-sm">
        <div className="flex justify-between text-slate-400">
          <span>Subtotal</span>
          <span className="font-mono text-white">{formatCurrency(bill.subtotal)}</span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>{bill.appliedPromotionName ?? "Discount"}</span>
          <span className="font-mono text-status-available">-{formatCurrency(bill.totalDiscount)}</span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>Service Charge{rates ? ` (${(rates.serviceChargeRate * 100).toFixed(0)}%)` : ""}</span>
          <span className="font-mono text-white">{formatCurrency(bill.serviceCharge)}</span>
        </div>
        <div className="flex justify-between text-slate-400">
          <span>VAT{rates ? ` (${(rates.vatRate * 100).toFixed(0)}%)` : ""}</span>
          <span className="font-mono text-white">{formatCurrency(bill.vat)}</span>
        </div>
        <div className="flex justify-between border-t border-ink-700 pt-1 text-base font-semibold">
          <span className="text-white">Total</span>
          <span className="font-mono text-accent">{formatCurrency(bill.total)}</span>
        </div>
      </div>

      {!showDiscountForm ? (
        <button
          onClick={() => setShowDiscountForm(true)}
          className="mt-3 min-h-11 px-2 text-xs font-medium text-accent hover:text-accent-soft"
        >
          + Add Manual Discount
        </button>
      ) : (
        <div className="mt-3 rounded-lg border border-ink-700 bg-ink-900 p-3">
          <p className="mb-2 text-xs text-slate-400">
            FR-22: a manual discount requires a Manager or Admin to authorize it here.
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="number"
              min="0.01"
              max="100"
              step="0.01"
              placeholder="Discount %"
              value={discountPercent}
              onChange={(e) => setDiscountPercent(e.target.value)}
              className="min-h-11 w-28 rounded-md border border-ink-700 bg-ink-800 px-2 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="text"
              placeholder="Manager username"
              value={managerUsername}
              onChange={(e) => setManagerUsername(e.target.value)}
              className="min-h-11 w-40 rounded-md border border-ink-700 bg-ink-800 px-2 text-sm text-white focus:border-accent focus:outline-none"
            />
            <input
              type="password"
              placeholder="Manager password"
              value={managerPassword}
              onChange={(e) => setManagerPassword(e.target.value)}
              className="min-h-11 w-40 rounded-md border border-ink-700 bg-ink-800 px-2 text-sm text-white focus:border-accent focus:outline-none"
            />
            <button
              disabled={discountBusy || !discountPercent || !managerUsername || !managerPassword}
              onClick={submitDiscount}
              className="min-h-11 rounded-full bg-accent px-3 text-xs font-semibold text-ink-950 hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
            >
              {discountBusy ? "Verifying..." : "Apply"}
            </button>
            <button
              onClick={() => setShowDiscountForm(false)}
              className="min-h-11 rounded-full border border-ink-700 px-3 text-xs text-slate-400 hover:bg-ink-700"
            >
              Cancel
            </button>
          </div>
          {discountError && <div className="mt-2 text-xs text-status-occupied">{discountError}</div>}
        </div>
      )}

      <div className="mt-4 border-t border-ink-700 pt-3">
        <div className="mb-2 flex gap-2">
          {PAYMENT_METHODS.map((method) => (
            <button
              key={method}
              onClick={() => setPaymentMethod(method)}
              className={`min-h-11 rounded-full px-3 text-xs font-medium transition ${
                paymentMethod === method
                  ? "bg-accent text-ink-950"
                  : "border border-ink-700 text-slate-400 hover:bg-ink-700"
              }`}
            >
              {method.charAt(0) + method.slice(1).toLowerCase()}
            </button>
          ))}
        </div>

        {paymentMethod === "CASH" && (
          <div className="mb-2 flex items-center gap-2 text-sm">
            <label className="text-slate-400">Tendered</label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={tenderedAmount}
              onChange={(e) => setTenderedAmount(e.target.value)}
              className="min-h-11 w-32 rounded-md border border-ink-700 bg-ink-900 px-2 font-mono text-white focus:border-accent focus:outline-none"
            />
            <span className="text-slate-400">
              Change: <span className="font-mono text-white">{formatCurrency(Math.max(change, 0))}</span>
            </span>
          </div>
        )}

        <button
          disabled={settleBusy || !canSettle}
          onClick={submitSettle}
          className="min-h-11 w-full rounded-full bg-accent text-sm font-semibold text-ink-950 transition hover:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
        >
          {settleBusy ? "Settling..." : `Settle Payment - ${formatCurrency(bill.total)}`}
        </button>
        {!canSettle && (
          <div className="mt-1 text-xs text-status-cleaning">Tendered amount must cover the total.</div>
        )}
        {settleError && <div className="mt-2 text-xs text-status-occupied">{settleError}</div>}
      </div>
    </div>
  );
}
