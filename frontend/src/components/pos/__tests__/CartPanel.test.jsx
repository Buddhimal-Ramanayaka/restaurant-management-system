import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import CartPanel from "../CartPanel.jsx";

const sampleItems = [
  { menuItemId: 1, name: "Chicken Biryani", price: 450, quantity: 2, specialNotes: "" },
];
const sampleTotals = { subtotal: 900, serviceCharge: 90, vat: 79.2, total: 1069.2 };

describe("CartPanel", () => {
  it("shows an empty-cart message when there are no items", () => {
    render(
      <CartPanel
        tableNumber="T-05" items={[]} totals={{ subtotal: 0, serviceCharge: 0, vat: 0, total: 0 }}
        onAdjustQty={() => {}} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={false} error={null}
      />
    );

    expect(screen.getByText(/cart is empty/i)).toBeInTheDocument();
  });

  it("renders cart lines with quantity and computed line total", () => {
    render(
      <CartPanel
        tableNumber="T-05" items={sampleItems} totals={sampleTotals}
        onAdjustQty={() => {}} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={false} error={null}
      />
    );

    expect(screen.getByText("Chicken Biryani")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    // The line total (450 * 2) and the cart subtotal happen to coincide at 900.00
    // in this fixture, so there are legitimately two matching nodes - assert on
    // the count instead of a single unique match.
    expect(screen.getAllByText("LKR 900.00").length).toBe(2);
  });

  it("renders the totals breakdown", () => {
    render(
      <CartPanel
        tableNumber="T-05" items={sampleItems} totals={sampleTotals}
        onAdjustQty={() => {}} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={false} error={null}
      />
    );

    expect(screen.getByText("LKR 1069.20")).toBeInTheDocument();
  });

  it("calls onAdjustQty with +1/-1 when the quantity buttons are tapped", async () => {
    const onAdjustQty = vi.fn();
    const user = userEvent.setup();
    render(
      <CartPanel
        tableNumber="T-05" items={sampleItems} totals={sampleTotals}
        onAdjustQty={onAdjustQty} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={false} error={null}
      />
    );

    await user.click(screen.getByText("+"));
    expect(onAdjustQty).toHaveBeenCalledWith(1, 1);

    await user.click(screen.getByText("−"));
    expect(onAdjustQty).toHaveBeenCalledWith(1, -1);
  });

  it("disables Clear Cart and Send to Kitchen when the cart is empty", () => {
    render(
      <CartPanel
        tableNumber="T-05" items={[]} totals={{ subtotal: 0, serviceCharge: 0, vat: 0, total: 0 }}
        onAdjustQty={() => {}} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={false} error={null}
      />
    );

    expect(screen.getByRole("button", { name: /clear cart/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /send to kitchen/i })).toBeDisabled();
  });

  it("shows the error message when submission fails", () => {
    render(
      <CartPanel
        tableNumber="T-05" items={sampleItems} totals={sampleTotals}
        onAdjustQty={() => {}} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={false} error="Insufficient stock for Chicken Biryani"
      />
    );

    expect(screen.getByText("Insufficient stock for Chicken Biryani")).toBeInTheDocument();
  });

  it("shows the loyalty discount line when totals.discount is positive (Figure 3.8)", () => {
    const discountedTotals = { subtotal: 900, discount: 90, serviceCharge: 81, vat: 78.48, total: 969.48 };
    render(
      <CartPanel
        tableNumber="T-05" items={sampleItems} totals={discountedTotals}
        promotion={{ name: "Gold Tier 10% Off", discountPercent: 10 }}
        onAdjustQty={() => {}} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={false} error={null}
      />
    );

    expect(screen.getByText("Gold Tier 10% Off")).toBeInTheDocument();
    expect(screen.getByText("-LKR 90.00")).toBeInTheDocument();
  });

  it("does not show a discount line when totals.discount is zero or absent", () => {
    render(
      <CartPanel
        tableNumber="T-05" items={sampleItems} totals={sampleTotals}
        onAdjustQty={() => {}} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={false} error={null}
      />
    );

    expect(screen.queryByText(/loyalty discount/i)).not.toBeInTheDocument();
  });

  it("shows Sending... and disables the submit button while submitting", () => {
    render(
      <CartPanel
        tableNumber="T-05" items={sampleItems} totals={sampleTotals}
        onAdjustQty={() => {}} onSetNotes={() => {}} onClear={() => {}} onSubmit={() => {}}
        submitting={true} error={null}
      />
    );

    expect(screen.getByRole("button", { name: /sending/i })).toBeDisabled();
  });
});
