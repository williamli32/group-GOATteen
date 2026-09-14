import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Auth from "./pages/Auth";
import Dashboard from "./pages/Dashboard";
import ClientView from "./pages/ClientView";
import AuditLog from "./pages/AuditLog";
import Reports from "./pages/Reports";
import Sidebar from "./components/Sidebar";

function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-full bg-[#080c18]">
      <Sidebar />
      <main className="flex-1 overflow-hidden flex flex-col">
        {children}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/auth" replace />} />
        <Route path="/auth" element={<Auth />} />
        <Route
          path="/dashboard"
          element={<AppShell><Dashboard /></AppShell>}
        />
        <Route
          path="/client"
          element={<AppShell><ClientView /></AppShell>}
        />
        <Route
          path="/audit"
          element={<AppShell><AuditLog /></AppShell>}
        />
        <Route
          path="/reports"
          element={<AppShell><Reports /></AppShell>}
        />
      </Routes>
    </BrowserRouter>
  );
}
