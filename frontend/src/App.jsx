import { Routes, Route, Navigate } from "react-router-dom";
import LoginPage from "./pages/LoginPage.jsx";
import PosPage from "./pages/PosPage.jsx";
import KitchenPage from "./pages/KitchenPage.jsx";
import ManagerPage from "./pages/ManagerPage.jsx";
import CashierPage from "./pages/CashierPage.jsx";
import AdminPage from "./pages/AdminPage.jsx";
import ProtectedRoute from "./components/common/ProtectedRoute.jsx";
import { useAuth } from "./context/AuthContext.jsx";

function RoleHome() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role === "KITCHEN") return <Navigate to="/kitchen" replace />;
  // Admin gets its own panel (Figure 3.1) - it links across to /manager for shared
  // analytics/PO-approval/etc. rather than Manager and Admin sharing one landing page.
  if (user.role === "ADMIN") return <Navigate to="/admin" replace />;
  if (user.role === "MANAGER") return <Navigate to="/manager" replace />;
  if (user.role === "CASHIER") return <Navigate to="/cashier" replace />;
  return <Navigate to="/pos" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<RoleHome />} />
      <Route
        path="/pos"
        element={
          <ProtectedRoute allowedRoles={["WAITER", "MANAGER", "ADMIN"]}>
            <PosPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/kitchen"
        element={
          <ProtectedRoute allowedRoles={["KITCHEN", "MANAGER", "ADMIN"]}>
            <KitchenPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/manager"
        element={
          <ProtectedRoute allowedRoles={["MANAGER", "ADMIN"]}>
            <ManagerPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/cashier"
        element={
          <ProtectedRoute allowedRoles={["CASHIER", "MANAGER", "ADMIN"]}>
            <CashierPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin"
        element={
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <AdminPage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
