import { Navigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

/**
 * Route guard by role. This is a UX convenience only - the backend
 * SecurityConfig @PreAuthorize matrix is the actual enforcement boundary, so
 * a user who bypasses this component still cannot call an endpoint outside
 * their role.
 */
export default function ProtectedRoute({ allowedRoles, children }) {
  const { user } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }
  return children;
}
