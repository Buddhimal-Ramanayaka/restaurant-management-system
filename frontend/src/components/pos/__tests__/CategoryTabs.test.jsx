import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import CategoryTabs from "../CategoryTabs.jsx";

describe("CategoryTabs", () => {
  const categories = ["Main Course", "Beverages", "Desserts"];

  it("renders one button per category", () => {
    render(<CategoryTabs categories={categories} active="Main Course" onSelect={() => {}} />);

    categories.forEach((cat) => {
      expect(screen.getByRole("button", { name: cat })).toBeInTheDocument();
    });
  });

  it("calls onSelect with the tapped category", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<CategoryTabs categories={categories} active="Main Course" onSelect={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Desserts" }));

    expect(onSelect).toHaveBeenCalledWith("Desserts");
  });

  it("applies the active accent class only to the currently selected category", () => {
    render(<CategoryTabs categories={categories} active="Beverages" onSelect={() => {}} />);

    const activeTab = screen.getByRole("button", { name: "Beverages" });
    const inactiveTab = screen.getByRole("button", { name: "Main Course" });

    expect(activeTab.className).toContain("bg-accent");
    expect(inactiveTab.className).not.toContain("bg-accent");
  });
});
