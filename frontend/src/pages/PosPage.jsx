import { useEffect, useMemo, useState } from "react";
import Navbar from "../components/common/Navbar.jsx";
import CategoryTabs from "../components/pos/CategoryTabs.jsx";
import MenuGrid from "../components/pos/MenuGrid.jsx";
import CartPanel from "../components/pos/CartPanel.jsx";
import TableFloorPlan from "../components/tables/TableFloorPlan.jsx";
import ReservationsPanel from "../components/tables/ReservationsPanel.jsx";
import ActiveOrderPanel from "../components/tables/ActiveOrderPanel.jsx";
import { useCart } from "../hooks/useCart.js";
import { useStompClient } from "../hooks/useStompClient.js";
import { fetchAvailableMenuItems } from "../api/menuApi.js";
import { fetchTables, markTableAvailable } from "../api/tableApi.js";
import { submitOrder, fetchActiveOrders } from "../api/orderApi.js";
import { fetchBillingRates } from "../api/settingsApi.js";
import { fetchApplicablePromotion } from "../api/promotionApi.js";

export default function PosPage() {
  const [menuItems, setMenuItems] = useState([]);
  const [tables, setTables] = useState([]);
  const [selectedTable, setSelectedTable] = useState(null);
  const [viewingTable, setViewingTable] = useState(null);
  const [viewingOrder, setViewingOrder] = useState(null);
  const [showReservations, setShowReservations] = useState(false);
  const [activeCategory, setActiveCategory] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [readyAlert, setReadyAlert] = useState(null);
  // FR-21: Admin-configurable - fetched live so the waiter's running total always matches
  // what the cashier will actually bill, rather than trusting a hardcoded 10%/8%.
  const [rates, setRates] = useState({ serviceChargeRate: 0.10, vatRate: 0.08 });
  // Figure 3.8: the cart shows "any applicable loyalty discount computed in real time" -
  // resolved once the waiter looks up a customer, cleared again if they clear the search.
  const [promotion, setPromotion] = useState(null);

  const cart = useCart(rates, promotion ? Number(promotion.discountPercent) : 0);

  async function handleCustomerFound(customer) {
    if (!customer) {
      setPromotion(null);
      return;
    }
    const applicable = await fetchApplicablePromotion(customer.loyaltyTier);
    setPromotion(applicable);
  }

  useEffect(() => {
    fetchAvailableMenuItems().then((items) => {
      setMenuItems(items);
      const categories = [...new Set(items.map((i) => i.category))];
      setActiveCategory(categories[0] || null);
    });
    fetchTables().then(setTables);
    fetchBillingRates().then(setRates);
  }, []);

  // Live table status + this waiter order-ready notifications
  const subscriptions = useMemo(
    () => [
      {
        destination: "/topic/tables",
        onMessage: (msg) => {
          setTables((prev) =>
            prev.map((t) => (t.id === msg.table.id ? msg.table : t))
          );
        },
      },
      {
        destination: "/user/queue/order-ready",
        onMessage: (msg) => {
          setReadyAlert(msg);
          setTimeout(() => setReadyAlert(null), 8000);
        },
      },
      {
        // FR-09: a sale on another terminal can zero out an ingredient and auto-disable a
        // menu item mid-service - this terminal's grid must drop it immediately, not wait
        // for the waiter to navigate away and back.
        destination: "/topic/menu",
        onMessage: (item) => {
          setMenuItems((prev) =>
            item.isAvailable
              ? prev.some((mi) => mi.id === item.id) ? prev.map((mi) => (mi.id === item.id ? item : mi)) : [...prev, item]
              : prev.filter((mi) => mi.id !== item.id)
          );
        },
      },
    ],
    []
  );
  useStompClient(subscriptions);

  async function handleMarkAvailable(tableId) {
    await markTableAvailable(tableId);
    // /topic/tables broadcasts the updated table to every connected terminal, this one included.
  }

  async function handleViewOrder(table) {
    const activeOrders = await fetchActiveOrders();
    const order = activeOrders.find((o) => o.tableId === table.id);
    if (!order) {
      setError(`No active order found for ${table.tableNumber} - it may have just been billed.`);
      return;
    }
    setViewingTable(table);
    setViewingOrder(order);
  }

  function handleOrderVoided() {
    setViewingTable(null);
    setViewingOrder(null);
  }

  const categories = [...new Set(menuItems.map((i) => i.category))];
  const filteredItems = activeCategory
    ? menuItems.filter((i) => i.category === activeCategory)
    : menuItems;

  async function handleSubmit(customerPhone) {
    if (!selectedTable) {
      setError("Select a table before sending to kitchen.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await submitOrder({
        tableId: selectedTable.id,
        items: cart.items.map((line) => ({
          menuItemId: line.menuItemId,
          quantity: line.quantity,
          specialNotes: line.specialNotes || null,
        })),
        customerPhone: customerPhone || null,
      });
      cart.clear();
      setPromotion(null);
      const refreshed = await fetchTables();
      setTables(refreshed);
      setSelectedTable(null);
    } catch (err) {
      setError(err.response?.data?.message || "Could not submit order - check stock levels.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex h-screen flex-col bg-ink-900">
      <Navbar
        title="POS Terminal"
        right={
          readyAlert && (
            <div className="animate-pulse rounded-md bg-status-available/20 px-3 py-1.5 text-xs font-semibold text-status-available">
              ✓ Order #{readyAlert.orderId} ready - Table {readyAlert.tableNumber}
            </div>
          )
        }
      />

      <div className="flex flex-1 overflow-hidden">
        <div className="flex flex-1 flex-col overflow-y-auto">
          {showReservations ? (
            <ReservationsPanel tables={tables} onBack={() => setShowReservations(false)} />
          ) : viewingTable ? (
            <ActiveOrderPanel
              table={viewingTable}
              order={viewingOrder}
              onBack={() => {
                setViewingTable(null);
                setViewingOrder(null);
              }}
              onVoided={handleOrderVoided}
            />
          ) : !selectedTable ? (
            <>
              <div className="flex items-center justify-between px-4 pt-4">
                <div className="text-sm font-semibold text-slate-300">Select a table to begin an order</div>
                <button
                  onClick={() => setShowReservations(true)}
                  className="min-h-11 rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-status-reserved hover:text-status-reserved"
                >
                  Reservations
                </button>
              </div>
              <TableFloorPlan
                tables={tables}
                onSelect={setSelectedTable}
                onMarkAvailable={handleMarkAvailable}
                onViewOrder={handleViewOrder}
              />
            </>
          ) : (
            <>
              <div className="flex items-center gap-3 px-4 pt-4">
                <button
                  onClick={() => setSelectedTable(null)}
                  className="min-h-11 px-2 text-xs text-slate-400 hover:text-white"
                >
                  ← Back to floor plan
                </button>
              </div>
              <CategoryTabs
                categories={categories}
                active={activeCategory}
                onSelect={setActiveCategory}
              />
              <MenuGrid items={filteredItems} onAdd={cart.addItem} />
            </>
          )}
        </div>

        {!viewingTable && (
          <CartPanel
            tableNumber={selectedTable?.tableNumber}
            items={cart.items}
            totals={cart.totals}
            rates={rates}
            promotion={promotion}
            onCustomerFound={handleCustomerFound}
            onAdjustQty={cart.adjustQty}
            onSetNotes={cart.setNotes}
            onClear={() => {
              cart.clear();
              setPromotion(null);
            }}
            onSubmit={handleSubmit}
            submitting={submitting}
            error={error}
          />
        )}
      </div>
    </div>
  );
}
