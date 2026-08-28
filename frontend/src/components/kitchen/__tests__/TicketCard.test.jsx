import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import TicketCard from "../TicketCard.jsx";

function orderAt(minutesAgo, overrides = {}) {
  return {
    id: 1039,
    tableId: 3,
    status: "PENDING",
    createdAt: new Date(Date.now() - minutesAgo * 60000).toISOString(),
    items: [
      { id: 1, menuItemName: "Chicken Biryani", quantity: 2, lineStatus: "PENDING", specialNotes: "No spicy" },
    ],
    ...overrides,
  };
}

describe("TicketCard", () => {

  it("shows normal waiting time styling for a ticket under 15 minutes old", () => {
    render(<TicketCard order={orderAt(5)} onStartCooking={() => {}} onMarkLineDone={() => {}} onMarkAllReady={() => {}} />);

    expect(screen.getByText(/waiting 5 min/i)).toBeInTheDocument();
    expect(screen.queryByText(/urgent/i)).not.toBeInTheDocument();
  });

  it("flags a PENDING ticket older than 15 minutes as URGENT", () => {
    render(<TicketCard order={orderAt(17)} onStartCooking={() => {}} onMarkLineDone={() => {}} onMarkAllReady={() => {}} />);

    expect(screen.getByText(/urgent/i)).toBeInTheDocument();
  });

  it("does not flag urgency for a PREPARING ticket even past 15 minutes (only PENDING counts)", () => {
    render(
      <TicketCard
        order={orderAt(20, { status: "PREPARING" })}
        onStartCooking={() => {}} onMarkLineDone={() => {}} onMarkAllReady={() => {}}
      />
    );

    expect(screen.queryByText(/urgent/i)).not.toBeInTheDocument();
  });

  it("shows Start Cooking for a PENDING ticket and calls onStartCooking with the order id", async () => {
    const onStartCooking = vi.fn();
    const user = userEvent.setup();
    render(<TicketCard order={orderAt(2)} onStartCooking={onStartCooking} onMarkLineDone={() => {}} onMarkAllReady={() => {}} />);

    await user.click(screen.getByRole("button", { name: /start cooking/i }));

    expect(onStartCooking).toHaveBeenCalledWith(1039);
  });

  it("shows per-line Mark Done buttons once PREPARING, and disables All Ready until every line is done", () => {
    const order = orderAt(3, {
      status: "PREPARING",
      items: [
        { id: 1, menuItemName: "Chicken Biryani", quantity: 1, lineStatus: "READY", specialNotes: null },
        { id: 2, menuItemName: "Garlic Naan", quantity: 2, lineStatus: "PENDING", specialNotes: null },
      ],
    });

    render(<TicketCard order={order} onStartCooking={() => {}} onMarkLineDone={() => {}} onMarkAllReady={() => {}} />);

    expect(screen.getByRole("button", { name: /all ready/i })).toBeDisabled();
  });

  it("enables All Ready once every line is marked READY, and calls onMarkAllReady with the order id", async () => {
    const onMarkAllReady = vi.fn();
    const user = userEvent.setup();
    const order = orderAt(3, {
      status: "PREPARING",
      items: [
        { id: 1, menuItemName: "Chicken Biryani", quantity: 1, lineStatus: "READY", specialNotes: null },
      ],
    });

    render(<TicketCard order={order} onStartCooking={() => {}} onMarkLineDone={() => {}} onMarkAllReady={onMarkAllReady} />);

    const button = screen.getByRole("button", { name: /all ready/i });
    expect(button).toBeEnabled();
    await user.click(button);

    expect(onMarkAllReady).toHaveBeenCalledWith(1039);
  });

  it("shows a waiter-notified confirmation for a READY ticket with no action buttons", () => {
    render(
      <TicketCard
        order={orderAt(1, { status: "READY", items: [{ id: 1, menuItemName: "Naan", quantity: 1, lineStatus: "READY" }] })}
        onStartCooking={() => {}} onMarkLineDone={() => {}} onMarkAllReady={() => {}}
      />
    );

    expect(screen.getByText(/waiter notified/i)).toBeInTheDocument();
  });
});
