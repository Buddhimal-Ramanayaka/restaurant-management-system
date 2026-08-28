import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useCart } from "../useCart.js";

const chickenBiryani = { id: 1, name: "Chicken Biryani", price: 450 };
const garlicNaan = { id: 2, name: "Garlic Naan", price: 80 };

describe("useCart", () => {
  it("starts empty with zeroed totals", () => {
    const { result } = renderHook(() => useCart());

    expect(result.current.items).toEqual([]);
    expect(result.current.totals.subtotal).toBe(0);
    expect(result.current.totals.total).toBe(0);
  });

  it("adds a new item to the cart with quantity 1", () => {
    const { result } = renderHook(() => useCart());

    act(() => result.current.addItem(chickenBiryani));

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0]).toMatchObject({
      menuItemId: 1,
      name: "Chicken Biryani",
      price: 450,
      quantity: 1,
    });
  });

  it("adding the same item twice increments quantity instead of duplicating the line", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem(chickenBiryani);
      result.current.addItem(chickenBiryani);
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].quantity).toBe(2);
  });

  it("adjustQty increases and decreases quantity", () => {
    const { result } = renderHook(() => useCart());

    act(() => result.current.addItem(chickenBiryani));
    act(() => result.current.adjustQty(1, 1));
    expect(result.current.items[0].quantity).toBe(2);

    act(() => result.current.adjustQty(1, -1));
    expect(result.current.items[0].quantity).toBe(1);
  });

  it("adjustQty dropping to zero removes the line entirely", () => {
    const { result } = renderHook(() => useCart());

    act(() => result.current.addItem(chickenBiryani));
    act(() => result.current.adjustQty(1, -1));

    expect(result.current.items).toHaveLength(0);
  });

  it("setNotes attaches a special instruction to the correct line only", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem(chickenBiryani);
      result.current.addItem(garlicNaan);
    });
    act(() => result.current.setNotes(1, "No spicy, extra rice"));

    expect(result.current.items.find((l) => l.menuItemId === 1).specialNotes).toBe("No spicy, extra rice");
    expect(result.current.items.find((l) => l.menuItemId === 2).specialNotes).toBe("");
  });

  it("removeItem drops the line regardless of quantity", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem(chickenBiryani);
      result.current.adjustQty(1, 4); // quantity now 5
      result.current.removeItem(1);
    });

    expect(result.current.items).toHaveLength(0);
  });

  it("clear empties the whole cart", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem(chickenBiryani);
      result.current.addItem(garlicNaan);
      result.current.clear();
    });

    expect(result.current.items).toHaveLength(0);
  });

  it("computes subtotal, service charge (10%), VAT (8% of subtotal+service), and total", () => {
    const { result } = renderHook(() => useCart());

    // 1x Biryani (450) + 3x Naan (80 each = 240) => subtotal 690
    act(() => {
      result.current.addItem(chickenBiryani);
      result.current.addItem(garlicNaan);
      result.current.adjustQty(2, 2); // naan qty: 1 -> 3
    });

    const { subtotal, serviceCharge, vat, total } = result.current.totals;

    expect(subtotal).toBeCloseTo(690, 5);
    expect(serviceCharge).toBeCloseTo(69, 5); // 10% of 690
    expect(vat).toBeCloseTo((690 + 69) * 0.08, 5); // 8% of (subtotal + service)
    expect(total).toBeCloseTo(690 + 69 + (690 + 69) * 0.08, 5);
  });

  it("applies a loyalty discount before service charge and VAT (Figure 3.8)", () => {
    const { result } = renderHook(() => useCart({ serviceChargeRate: 0.10, vatRate: 0.08 }, 10));

    act(() => result.current.addItem(chickenBiryani)); // subtotal 450

    const { subtotal, discount, serviceCharge, vat, total } = result.current.totals;
    const discountedSubtotal = 450 - 45;

    expect(subtotal).toBeCloseTo(450, 5);
    expect(discount).toBeCloseTo(45, 5); // 10% of 450
    expect(serviceCharge).toBeCloseTo(discountedSubtotal * 0.10, 5);
    expect(vat).toBeCloseTo((discountedSubtotal + discountedSubtotal * 0.10) * 0.08, 5);
    expect(total).toBeCloseTo(discountedSubtotal + serviceCharge + vat, 5);
  });
});
