import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, act, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AuthProvider, useAuth } from "../AuthContext.jsx";

const mockLogin = vi.fn();
vi.mock("../../api/authApi.js", () => ({
  login: (...args) => mockLogin(...args),
}));

function LoginProbe() {
  const { user, login, logout } = useAuth();
  return (
    <div>
      <div data-testid="user-state">{user ? `${user.username}:${user.role}` : "no-user"}</div>
      <button onClick={() => login("kamal", "password123")}>Do Login</button>
      <button onClick={logout}>Do Logout</button>
    </div>
  );
}

describe("AuthContext", () => {
  beforeEach(() => {
    localStorage.clear();
    mockLogin.mockReset();
  });

  it("starts with no user when localStorage is empty", () => {
    render(
      <AuthProvider>
        <LoginProbe />
      </AuthProvider>
    );

    expect(screen.getByTestId("user-state")).toHaveTextContent("no-user");
  });

  it("login stores the token/user in localStorage and updates context state", async () => {
    mockLogin.mockResolvedValue({ token: "fake.jwt.token", username: "kamal", role: "WAITER", userId: 1 });
    const user = userEvent.setup();

    render(
      <AuthProvider>
        <LoginProbe />
      </AuthProvider>
    );

    await user.click(screen.getByText("Do Login"));

    await waitFor(() => {
      expect(screen.getByTestId("user-state")).toHaveTextContent("kamal:WAITER");
    });
    expect(localStorage.getItem("rms_token")).toBe("fake.jwt.token");
    expect(JSON.parse(localStorage.getItem("rms_user"))).toMatchObject({ username: "kamal", role: "WAITER" });
  });

  it("logout clears localStorage and resets context state", async () => {
    mockLogin.mockResolvedValue({ token: "fake.jwt.token", username: "kamal", role: "WAITER", userId: 1 });
    const user = userEvent.setup();

    render(
      <AuthProvider>
        <LoginProbe />
      </AuthProvider>
    );

    await user.click(screen.getByText("Do Login"));
    await waitFor(() => expect(screen.getByTestId("user-state")).toHaveTextContent("kamal:WAITER"));

    await user.click(screen.getByText("Do Logout"));

    expect(screen.getByTestId("user-state")).toHaveTextContent("no-user");
    expect(localStorage.getItem("rms_token")).toBeNull();
    expect(localStorage.getItem("rms_user")).toBeNull();
  });

  it("restores an existing session from localStorage on mount", () => {
    localStorage.setItem("rms_user", JSON.stringify({ userId: 2, username: "dilshan", role: "MANAGER" }));
    localStorage.setItem("rms_token", "existing.token");

    render(
      <AuthProvider>
        <LoginProbe />
      </AuthProvider>
    );

    expect(screen.getByTestId("user-state")).toHaveTextContent("dilshan:MANAGER");
  });
});
