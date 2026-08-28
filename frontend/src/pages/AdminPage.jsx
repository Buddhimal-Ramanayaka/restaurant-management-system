import { Link } from "react-router-dom";
import Navbar from "../components/common/Navbar.jsx";
import UserManagementPanel from "../components/admin/UserManagementPanel.jsx";
import MenuManagementPanel from "../components/admin/MenuManagementPanel.jsx";
import SupplierManagementPanel from "../components/admin/SupplierManagementPanel.jsx";

/** Figure 3.1's "Admin Panel" presentation-layer box (User Management, Recipe Config,
 *  Menu Management, System Reports) - previously Admin only ever landed on the Manager
 *  Dashboard and inherited whatever Manager could see, with no Admin-exclusive screen. */
export default function AdminPage() {
  return (
    <div className="min-h-screen bg-ink-900">
      <Navbar
        title="Admin Panel"
        right={
          <Link
            to="/manager"
            className="min-h-11 flex items-center rounded-full border border-ink-700 px-3 text-xs font-medium text-slate-300 hover:border-accent hover:text-accent"
          >
            Manager Dashboard →
          </Link>
        }
      />

      <div className="space-y-4 p-4">
        <UserManagementPanel />
        <MenuManagementPanel />
        <SupplierManagementPanel />
      </div>
    </div>
  );
}
