import { useState } from "react";
import { auditLog } from "../data/mockData";

function fmt(n: number) {
  return n.toLocaleString("en-US", { style: "currency", currency: "USD" });
}

const STATUS_STYLE: Record<string, string> = {
  FILLED: "bg-[#22c55e1a] text-[#22c55e] border-[#22c55e30]",
  PARTIAL: "bg-[#f59e0b1a] text-[#f59e0b] border-[#f59e0b30]",
  CANCELLED: "bg-[#ef44441a] text-[#ef4444] border-[#ef444430]",
};

export default function AuditLog() {
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [expanded, setExpanded] = useState<string | null>(null);

  const filtered = auditLog.filter((r) => {
    const matchSearch =
      r.id.toLowerCase().includes(search.toLowerCase()) ||
      r.symbol.toLowerCase().includes(search.toLowerCase()) ||
      r.userName.toLowerCase().includes(search.toLowerCase());
    const matchStatus = statusFilter === "ALL" || r.status === statusFilter;
    return matchSearch && matchStatus;
  });

  return (
    <div className="flex-1 overflow-y-auto p-6 space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-[#e2e8f0]">Audit Log</h1>
          <p className="text-sm text-[#64748b] mt-0.5">Immutable, timestamped record of every order and its outcome</p>
        </div>
        <div className="flex items-center gap-2 text-xs">
          <div className="flex items-center gap-1.5 px-3 py-1.5 bg-[#0d1225] border border-[#1e2d45] rounded">
            <span className="text-[#64748b] font-mono">RECORDS</span>
            <span className="font-mono text-[#00d4aa] font-semibold">{auditLog.length}</span>
          </div>
          <button className="px-3 py-1.5 bg-[#0d1225] border border-[#1e2d45] rounded text-[#64748b] hover:text-[#94a3b8] transition-colors">
            ↓ Export CSV
          </button>
        </div>
      </div>

      {/* Notice banner */}
      <div className="flex items-center gap-3 px-4 py-3 bg-[#00d4aa08] border border-[#00d4aa20] rounded">
        <span className="text-[#00d4aa] text-sm">◈</span>
        <p className="text-xs text-[#64748b]">
          All records in this log are <span className="text-[#94a3b8]">write-protected and cryptographically hashed</span>. Any modification attempt is automatically flagged and alerted to compliance.
        </p>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3">
        <input
          type="text"
          placeholder="Search by order ID, symbol, or trader..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="flex-1 bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2 text-xs text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors font-mono"
        />
        <div className="flex gap-1">
          {["ALL", "FILLED", "PARTIAL", "CANCELLED"].map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-2.5 py-1.5 text-[10px] font-mono rounded border transition-all ${
                statusFilter === s
                  ? "bg-[#00d4aa1a] text-[#00d4aa] border-[#00d4aa40]"
                  : "border-[#1e2d45] text-[#64748b] hover:text-[#94a3b8] hover:border-[#2a3f5f]"
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="bg-[#0d1225] border border-[#1e2d45] rounded overflow-hidden">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-[#1e2d45] bg-[#080c18]">
              {["Order ID", "Executed At", "Trader", "Symbol", "Side", "Qty", "Price", "Total", "Commission", "Venue", "Status", ""].map((h) => (
                <th key={h} className="text-left px-3 py-2.5 text-[10px] text-[#64748b] font-medium tracking-wide whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.map((r) => (
              <>
                <tr
                  key={r.id}
                  className="border-b border-[#1e2d4540] hover:bg-[#ffffff03] cursor-pointer"
                  onClick={() => setExpanded(expanded === r.id ? null : r.id)}
                >
                  <td className="px-3 py-3 font-mono text-[#00d4aa]">{r.id}</td>
                  <td className="px-3 py-3 font-mono text-[#64748b] whitespace-nowrap">
                    {new Date(r.executedAt).toLocaleString("en-US", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit", second: "2-digit" })}
                  </td>
                  <td className="px-3 py-3 text-[#94a3b8]">{r.userName}</td>
                  <td className="px-3 py-3 font-mono font-semibold text-[#e2e8f0]">{r.symbol}</td>
                  <td className={`px-3 py-3 font-mono font-semibold ${r.side === "BUY" ? "text-[#22c55e]" : "text-[#ef4444]"}`}>{r.side}</td>
                  <td className="px-3 py-3 font-mono text-[#94a3b8]">{r.qty}</td>
                  <td className="px-3 py-3 font-mono text-[#94a3b8]">{fmt(r.price)}</td>
                  <td className="px-3 py-3 font-mono text-[#e2e8f0]">{fmt(r.total)}</td>
                  <td className="px-3 py-3 font-mono text-[#64748b]">{r.commission > 0 ? fmt(r.commission) : "—"}</td>
                  <td className="px-3 py-3 font-mono text-[#64748b]">{r.venue}</td>
                  <td className="px-3 py-3">
                    <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono border ${STATUS_STYLE[r.status] || ""}`}>{r.status}</span>
                  </td>
                  <td className="px-3 py-3 text-[#2a3f5f]">{expanded === r.id ? "▲" : "▼"}</td>
                </tr>
                {expanded === r.id && (
                  <tr key={`${r.id}-detail`} className="border-b border-[#1e2d45] bg-[#080c18]">
                    <td colSpan={12} className="px-6 py-3">
                      <div className="grid grid-cols-3 gap-6 text-[10px]">
                        <div className="space-y-1.5">
                          <p className="text-[#64748b] font-medium tracking-wider">EXECUTION DETAILS</p>
                          <div className="flex justify-between"><span className="text-[#64748b]">Order ID</span><span className="font-mono text-[#94a3b8]">{r.id}</span></div>
                          <div className="flex justify-between"><span className="text-[#64748b]">Execution Time</span><span className="font-mono text-[#94a3b8]">{r.executedAt}</span></div>
                          <div className="flex justify-between"><span className="text-[#64748b]">Venue</span><span className="font-mono text-[#94a3b8]">{r.venue || "—"}</span></div>
                        </div>
                        <div className="space-y-1.5">
                          <p className="text-[#64748b] font-medium tracking-wider">TRADER INFO</p>
                          <div className="flex justify-between"><span className="text-[#64748b]">Trader</span><span className="font-mono text-[#94a3b8]">{r.userName}</span></div>
                          <div className="flex justify-between"><span className="text-[#64748b]">User ID</span><span className="font-mono text-[#94a3b8]">{r.userId}</span></div>
                          <div className="flex justify-between"><span className="text-[#64748b]">IP Address</span><span className="font-mono text-[#94a3b8]">{r.ip}</span></div>
                        </div>
                        <div className="space-y-1.5">
                          <p className="text-[#64748b] font-medium tracking-wider">NOTES</p>
                          <p className="text-[#64748b]">{r.notes || "No notes — order executed as submitted."}</p>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <div className="py-12 text-center text-sm text-[#2a3f5f] font-mono">No records match your filters.</div>
        )}
      </div>

      <p className="text-[10px] text-[#1e2d45] font-mono text-center">
        HASH: SHA-256 · CHAIN: IMMUTABLE · RETENTION: 7 YEARS · COMPLIANT: SEC 17a-4, FINRA 4511
      </p>
    </div>
  );
}
