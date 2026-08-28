import { useReducer, useMemo } from "react";

// FR-21: these are the fallback values only, used before PosPage's fetch of the live,
// Admin-configurable rates (GET /api/settings/billing-rates) resolves, and in tests that
// don't pass rates at all - they must match schema.sql's seeded defaults.
const DEFAULT_RATES = { serviceChargeRate: 0.10, vatRate: 0.08 };

function cartReducer(state, action) {
  switch (action.type) {
    case "ADD_ITEM": {
      const existing = state.find((line) => line.menuItemId === action.item.id);
      if (existing) {
        return state.map((line) =>
          line.menuItemId === action.item.id ? { ...line, quantity: line.quantity + 1 } : line
        );
      }
      return [
        ...state,
        {
          menuItemId: action.item.id,
          name: action.item.name,
          price: action.item.price,
          quantity: 1,
          specialNotes: "",
        },
      ];
    }
    case "ADJUST_QTY": {
      return state
        .map((line) =>
          line.menuItemId === action.menuItemId
            ? { ...line, quantity: line.quantity + action.delta }
            : line
        )
        .filter((line) => line.quantity > 0);
    }
    case "SET_NOTES": {
      return state.map((line) =>
        line.menuItemId === action.menuItemId ? { ...line, specialNotes: action.notes } : line
      );
    }
    case "REMOVE_ITEM":
      return state.filter((line) => line.menuItemId !== action.menuItemId);
    case "CLEAR":
      return [];
    default:
      return state;
  }
}

/**
 * Local-only cart state - nothing here touches the backend until submitOrder
 * is called from the POS page. Totals mirror BillingService server-side logic
 * (discount before service charge and VAT) so the running total the waiter
 * sees during ordering matches the final bill the cashier produces later.
 *
 * discountPercent (Figure 3.8) is resolved by the caller from GET
 * /api/promotions/applicable once a customer is looked up - it is 0 until then,
 * matching what BillingService would compute for a walk-in with no matched promotion.
 */
export function useCart(rates = DEFAULT_RATES, discountPercent = 0) {
  const [items, dispatch] = useReducer(cartReducer, []);

  const totals = useMemo(() => {
    const subtotal = items.reduce((sum, line) => sum + line.price * line.quantity, 0);
    const discount = subtotal * (discountPercent / 100);
    const discountedSubtotal = subtotal - discount;
    const serviceCharge = discountedSubtotal * rates.serviceChargeRate;
    const vat = (discountedSubtotal + serviceCharge) * rates.vatRate;
    const total = discountedSubtotal + serviceCharge + vat;
    return { subtotal, discount, serviceCharge, vat, total };
  }, [items, rates.serviceChargeRate, rates.vatRate, discountPercent]);

  return {
    items,
    totals,
    addItem: (item) => dispatch({ type: "ADD_ITEM", item }),
    adjustQty: (menuItemId, delta) => dispatch({ type: "ADJUST_QTY", menuItemId, delta }),
    setNotes: (menuItemId, notes) => dispatch({ type: "SET_NOTES", menuItemId, notes }),
    removeItem: (menuItemId) => dispatch({ type: "REMOVE_ITEM", menuItemId }),
    clear: () => dispatch({ type: "CLEAR" }),
  };
}
