import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ItemCard from "../ItemCard.jsx";

const availableItem = { id: 1, name: "Chicken Biryani", price: 450, category: "Main Course", isAvailable: true };
const unavailableItem = { id: 2, name: "Prawn Masala", price: 780, category: "Main Course", isAvailable: false };

describe("ItemCard", () => {
  it("renders the item name, category, and formatted price", () => {
    render(<ItemCard item={availableItem} onAdd={() => {}} />);

    expect(screen.getByText("Chicken Biryani")).toBeInTheDocument();
    expect(screen.getByText("Main Course")).toBeInTheDocument();
    expect(screen.getByText("LKR 450.00")).toBeInTheDocument();
  });

  it("shows an Available badge and enabled Add to Cart button for an available item", () => {
    render(<ItemCard item={availableItem} onAdd={() => {}} />);

    expect(screen.getByText("Available")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add to Cart" })).toBeEnabled();
  });

  it("shows an Unavailable badge and disables the button for an unavailable item", () => {
    render(<ItemCard item={unavailableItem} onAdd={() => {}} />);

    expect(screen.getAllByText("Unavailable").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Unavailable" })).toBeDisabled();
  });

  it("calls onAdd with the item when Add to Cart is clicked", async () => {
    const onAdd = vi.fn();
    const user = userEvent.setup();
    render(<ItemCard item={availableItem} onAdd={onAdd} />);

    await user.click(screen.getByRole("button", { name: "Add to Cart" }));

    expect(onAdd).toHaveBeenCalledTimes(1);
    expect(onAdd).toHaveBeenCalledWith(availableItem);
  });

  it("does not call onAdd when the item is unavailable (button is disabled)", async () => {
    const onAdd = vi.fn();
    const user = userEvent.setup();
    render(<ItemCard item={unavailableItem} onAdd={onAdd} />);

    await user.click(screen.getByRole("button", { name: "Unavailable" }));

    expect(onAdd).not.toHaveBeenCalled();
  });
});
