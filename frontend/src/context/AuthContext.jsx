import { createContext, useContext, useState, useCallback } from "react";
import { login as loginApi } from "../api/authApi";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem("rms_user");
    return stored ? JSON.parse(stored) : null;
  });

  const login = useCallback(async (username, password) => {
    const data = await loginApi(username, password);
    const userInfo = { userId: data.userId, username: data.username, role: data.role };
    localStorage.setItem("rms_token", data.token);
    localStorage.setItem("rms_user", JSON.stringify(userInfo));
    setUser(userInfo);
    return userInfo;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem("rms_token");
    localStorage.removeItem("rms_user");
    setUser(null);
  }, []);

  const token = localStorage.getItem("rms_token");

  return (
    <AuthContext.Provider value={{ user, token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
