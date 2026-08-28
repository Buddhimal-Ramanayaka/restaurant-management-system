import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import ProtectedRoute from "../ProtectedRoute.jsx";

const mockUseAuth = vi.fn();
vi.mock("../../../context/AuthContext.jsx", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderWithRoute(allowedRoles) {
  return render(
    <MemoryRouter initialEntries={["/pos"]}>
      <Routes>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/" element={<div>Home Page</div>} />
        <Route
          path="/pos"
          element={
            <ProtectedRoute allowedRoles={allowedRoles}>
              <div>Protected POS Content</div>
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

describe("ProtectedRoute", () => {
  it("redirects to /login when there is no authenticated user", () => {
    mockUseAuth.mockReturnValue({ user: null });

    renderWithRoute(["WAITER"]);

    expect(screen.getByText("Login Page")).toBeInTheDocument();
    expect(screen.queryByText("Protected POS Content")).not.toBeInTheDocument();
  });

  it("renders the protected content when the user role is allowed", () => {
    mockUseAuth.mockReturnValue({ user: { username: "kamal", role: "WAITER" } });

    renderWithRoute(["WAITER", "MANAGER"]);

    expect(screen.getByText("Protected POS Content")).toBeInTheDocument();
  });

  it("redirects home when the authenticated user role is not in allowedRoles", () => {
    mockUseAuth.mockReturnValue({ user: { username: "chef", role: "KITCHEN" } });

    renderWithRoute(["WAITER", "MANAGER"]);

    expect(screen.getByText("Home Page")).toBeInTheDocument();
    expect(screen.queryByText("Protected POS Content")).not.toBeInTheDocument();
  });

  it("allows access when allowedRoles is not specified", () => {
    mockUseAuth.mockReturnValue({ user: { username: "anyone", role: "CASHIER" } });

    renderWithRoute(undefined);

    expect(screen.getByText("Protected POS Content")).toBeInTheDocument();
  });
});
