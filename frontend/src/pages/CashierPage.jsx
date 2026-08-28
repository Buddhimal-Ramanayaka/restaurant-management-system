import { useCallback, useEffect, useMemo, useState } from "react";
import Navbar from "../components/common/Navbar.jsx";
import ShiftBar from "../components/cashier/ShiftBar.jsx";
import OrderQueue from "../components/cashier/OrderQueue.jsx";
import BillPanel from "../components/cashier/BillPanel.jsx";
import { useStompClient } from "../hooks/useStompClient.js";
import { updateOrderStatus } from "../api/orderApi.js";
import {
  fetchAwaitingBilling,
  fetchBillPreview,
  fetchBillPdf,
  applyManualDiscount as applyManualDiscountApi,
  settleOrder as settleOrderApi,
  fetchActiveShift,
  startShift as startShiftApi,
  closeShift as closeShiftApi,
} from "../api/billingApi.js";
import { downloadBlob } from "../utils/downloadBlob.js";
import { fetchBillingRates } from "../api/settingsApi.js";

function formatCurrency(value) {
  return `LKR ${Number(value ?? 0).toLocaleString("en-LK", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const BILLABLE_STATUSES = new Set(["READY", "BILLED"]);

export default function CashierPage() {
  const [shift, setShift] = useState(null);
  const [shiftLoading, setShiftLoading] = useState(true);
  const [shiftClosed, setShiftClosed] = useState(false);
  const [orders, setOrders] = useState([]);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [bill, setBill] = useState(null);
  const [appliedDiscountPercent, setAppliedDiscountPercent] = useState(null);
  const [receipt, setReceipt] = useState(null);
  const [printTarget, setPrintTarget] = useState(null);
  const [rates, setRates] = useState({ serviceChargeRate: 0.10, vatRate: 0.08 });
  

  useEffect(() => {
    fetchActiveShift()
      .then(setShift)
      .finally(() => setShiftLoading(false));
    fetchBillingRates().then(setRates);
  }, []);

  useEffect(() => {
    if (shift) fetchAwaitingBilling().then(setOrders);
  }, [shift?.id]);

  const upsertOrder = useCallback((incoming) => {
    setOrders((prev) => {
      if (!BILLABLE_STATUSES.has(incoming.status)) {
        return prev.filter((o) => o.id !== incoming.id);
      }
      const exists = prev.some((o) => o.id === incoming.id);
      return exists ? prev.map((o) => (o.id === incoming.id ? incoming : o)) : [...prev, incoming];
    });
  }, []);

  const subscriptions = useMemo(
    () => [
      {
        destination: "/topic/kitchen",
        onMessage: (msg) => {
          if (msg.eventType === "ORDER_VOIDED") {
            setOrders((prev) => prev.filter((o) => o.id !== msg.orderId));
          } else if (msg.order) {
            upsertOrder(msg.order);
          }
        },
      },
    ],
    [upsertOrder]
  );
  const { connected } = useStompClient(shift ? subscriptions : []);

  const selectOrder = async (order) => {
    setReceipt(null);
    setAppliedDiscountPercent(null);
    let target = order;
    if (order.status === "READY") {
      target = await updateOrderStatus(order.id, "BILLED");
      upsertOrder(target);
    }
    const preview = await fetchBillPreview(target.id);
    setSelectedOrder(target);
    setBill(preview);
  };

  const handleApplyDiscount = async (percent, managerUsername, managerPassword) => {
    const updated = await applyManualDiscountApi(selectedOrder.id, managerUsername, managerPassword, percent);
    setBill(updated);
    setAppliedDiscountPercent(percent);
  };
// new
  const handleApplyField = async () => {
    const updated = await applyManualFieldApi(selectedOrder.id, percent);
    setBill(updated);
    setAppliedDiscountPercent(percent);
  };
  
// new
  const handleSettle = async ({ paymentMethod, amount }) => {
    const settled = await settleOrderApi(selectedOrder.id, {
      shiftId: shift.id,
      paymentMethod,
      amount,
      manualDiscountPercent: appliedDiscountPercent,
    });
    setOrders((prev) => prev.filter((o) => o.id !== selectedOrder.id));
    setReceipt({
      order: selectedOrder,
      bill: settled,
      paymentMethod,
      amount,
      change: paymentMethod === "CASH" ? Math.max(amount - Number(settled.total), 0) : 0,
      settledAt: new Date(),
    });
    setSelectedOrder(null);
    setBill(null);
    // Refresh shift running totals for the ShiftBar display.
    fetchActiveShift().then(setShift);
  };

  const handlePrint = () => {
    setPrintTarget({ order: selectedOrder, bill, payment: null });
    requestAnimationFrame(() => window.print());
  };

  const handlePrintReceipt = () => {
    setPrintTarget({
      order: receipt.order,
      bill: receipt.bill,
      payment: { method: receipt.paymentMethod, amount: receipt.amount, change: receipt.change },
    });
    requestAnimationFrame(() => window.print());
  };

  const handleStartShift = async () => {
    const started = await startShiftApi();
    setShift(started);
  };

  const handleCloseShift = async (declaredDrawerAmount) => {
    const closed = await closeShiftApi(shift.id, declaredDrawerAmount);
    // Deliberately does NOT clear `shift` here - ShiftBar owns its own "Shift Closed"
    // variance summary in local state, and nulling `shift` immediately would unmount
    // it before that summary ever rendered. shiftClosed instead gates the queue/bill
    // panel below; the actual reset happens once the cashier dismisses the summary.
    setShiftClosed(true);
    setOrders([]);
    setSelectedOrder(null);
    setBill(null);
    return closed;
  };

  const handleDismissClosedShift = () => {
    setShift(null);
    setShiftClosed(false);
  };

  if (shiftLoading) {
    return <div className="flex h-screen items-center justify-center bg-ink-900 text-slate-500">Loading...</div>;
  }

  if (!shift) {
    return (
      <div className="flex h-screen flex-col bg-ink-900">
        <Navbar title="Cashier - Billing Terminal" />
        <div className="flex flex-1 flex-col items-center justify-center gap-4">
          <p className="text-sm text-slate-400">No shift is currently open.</p>
          <button
            onClick={handleStartShift}
            className="min-h-11 rounded-full bg-accent px-5 text-sm font-semibold text-ink-950 hover:bg-accent-soft"
          >
            Start Shift
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-ink-900">
      <Navbar
        title="Cashier - Billing Terminal"
        right={
          <div className="flex items-center gap-1.5 text-xs">
            <span className={`h-2 w-2 rounded-full ${connected ? "bg-status-available" : "bg-status-occupied"}`} />
            <span className="text-slate-400">{connected ? "Live" : "Reconnecting..."}</span>
          </div>
        }
      />

      <div className="space-y-4 p-4 print:hidden">
        <ShiftBar shift={shift} onCloseShift={handleCloseShift} onDismissClosed={handleDismissClosedShift} />

        {!shiftClosed && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_2fr]">
          <OrderQueue orders={orders} selectedOrderId={selectedOrder?.id} onSelect={selectOrder} />

          {receipt ? (
            <div className="rounded-lg border border-ink-700 bg-ink-800 p-6 text-center">
              <div className="mb-2 text-3xl">✓</div>
              <h2 className="mb-1 font-display text-sm font-bold text-white">Payment Settled</h2>
              <p className="mb-4 text-xs text-slate-400">
                Order #{receipt.order.id} - Table {receipt.order.tableId} - {formatCurrency(receipt.bill.total)} via{" "}
                {receipt.paymentMethod}
                {receipt.paymentMethod === "CASH" && receipt.change > 0 && ` (change ${formatCurrency(receipt.change)})`}
              </p>
              <div className="flex justify-center gap-2">
                <button
                  onClick={handlePrintReceipt}
                  className="min-h-11 rounded-full border border-ink-700 px-4 text-xs font-medium text-slate-300 hover:bg-ink-700"
                >
                  Print Receipt
                </button>
                <button
                  onClick={async () => {
                    const blob = await fetchBillPdf(receipt.order.id);
                    downloadBlob(blob, `order-${receipt.order.id}-bill.pdf`);
                  }}
                  className="min-h-11 rounded-full border border-ink-700 px-4 text-xs font-medium text-slate-300 hover:bg-ink-700"
                >
                  Download PDF
                </button>
                <button
                  onClick={() => setReceipt(null)}
                  className="min-h-11 rounded-full bg-accent px-4 text-xs font-semibold text-ink-950 hover:bg-accent-soft"
                >
                  New Bill
                </button>
              </div>
            </div>
          ) : (
            <BillPanel order={selectedOrder} bill={bill} rates={rates} onApplyDiscount={handleApplyDiscount} onSettle={handleSettle} onPrint={handlePrint} />
          )}
        </div>
        )}
      </div>

      {printTarget && <Receipt target={printTarget} />}
    </div>
  );
}

/** Print-only view (Tailwind's print: variant hides this everywhere except the print stylesheet) -
 *  the "PDF-formatted output... printable via standard browser print functionality" scope note. */
function Receipt({ target }) {
  const { order, bill, payment } = target;
  return (
    <div className="hidden print:block print:bg-white print:p-8 print:text-black">
      <div className="mx-auto max-w-sm font-mono text-sm">
        <div className="text-center">
          <div className="text-lg font-bold">Daiya Food Restaurant</div>
          <div className="text-xs">Order #{order.id} - Table {order.tableId}</div>
          <div className="text-xs">{new Date().toLocaleString()}</div>
        </div>
        <div className="my-2 border-t border-dashed border-black" />
        {bill.lines.map((line, i) => (
          <div key={i} className="flex justify-between">
            <span>{line.quantity}x {line.menuItemName}</span>
            <span>{formatCurrency(line.lineTotal)}</span>
          </div>
        ))}
        <div className="my-2 border-t border-dashed border-black" />
        <div className="flex justify-between"><span>Subtotal</span><span>{formatCurrency(bill.subtotal)}</span></div>
        {Number(bill.totalDiscount) > 0 && (
          <div className="flex justify-between"><span>{bill.appliedPromotionName}</span><span>-{formatCurrency(bill.totalDiscount)}</span></div>
        )}
        <div className="flex justify-between"><span>Service Charge</span><span>{formatCurrency(bill.serviceCharge)}</span></div>
        <div className="flex justify-between"><span>VAT</span><span>{formatCurrency(bill.vat)}</span></div>
        <div className="my-2 border-t border-dashed border-black" />
        <div className="flex justify-between text-base font-bold"><span>TOTAL</span><span>{formatCurrency(bill.total)}</span></div>
        {payment && (
          <>
            <div className="my-2 border-t border-dashed border-black" />
            <div className="flex justify-between"><span>Paid via {payment.method}</span><span>{formatCurrency(payment.amount)}</span></div>
            {payment.method === "CASH" && <div className="flex justify-between"><span>Change</span><span>{formatCurrency(payment.change)}</span></div>}
          </>
        )}
        <div className="mt-4 text-center text-xs">Thank you!</div>
      </div>
    </div>
  );
}
