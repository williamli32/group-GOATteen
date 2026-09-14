import { NavLink } from "react-router-dom";

const nav = [
  { to: "/dashboard", label: "Dashboard", icon: "⬡" },
  { to: "/client", label: "Client View", icon: "◈" },
  { to: "/audit", label: "Audit Log", icon: "▣" },
  { to: "/reports", label: "Reports", icon: "◫" },
];

export default function Sidebar() {
  return (
    <aside className="w-56 shrink-0 flex flex-col bg-[#0d1225] border-r border-[#1e2d45]">
      <div className="h-14 flex items-center px-5 border-b border-[#1e2d45]">
        <span className="font-mono text-[#00d4aa] font-semibold tracking-widest text-sm">APEX</span>
        <span className="font-mono text-[#64748b] text-sm tracking-widest ml-1">TRADE</span>
      </div>
      <nav className="flex-1 py-4 space-y-0.5 px-2">
        {nav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded text-sm transition-all duration-150 ${
                isActive
                  ? "bg-[#00d4aa1a] text-[#00d4aa] font-medium"
                  : "text-[#64748b] hover:text-[#94a3b8] hover:bg-[#ffffff08]"
              }`
            }
          >
            <span className="text-base leading-none">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>
      <div className="p-4 border-t border-[#1e2d45]">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-full bg-[#00d4aa22] flex items-center justify-center text-xs font-mono text-[#00d4aa]">AH</div>
          <div className="flex-1 min-w-0">
            <p className="text-xs text-[#e2e8f0] font-medium truncate">Alex Hartmann</p>
            <p className="text-[10px] text-[#64748b] truncate">Senior Trader</p>
          </div>
        </div>
      </div>
    </aside>
  );
}
