import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer, AreaChart, Area, XAxis, YAxis,
} from "recharts";
import { positions, orderHistory, sectorAllocation, portfolioHistory } from "../data/mockData";

const SECTOR_COLORS = ["#00d4aa", "#06b6d4", "#818cf8", "#f59e0b", "#64748b"];

function fmt(n: number, compact = false) {
  if (compact && n >= 1000) return `$${(n / 1000).toFixed(1)}K`;
  return n.toLocaleString("en-US", { style: "currency", currency: "USD" });
}

const StatCard = ({ label, value, sub, accent }: { label: string; value: string; sub?: string; accent?: string }) => (
  <div className="bg-[#0d1225] border border-[#1e2d45] rounded p-4">
    <p className="text-[10px] text-[#64748b] tracking-wider font-medium mb-2">{label}</p>
    <p className={`font-mono text-xl font-semibold ${accent || "text-[#e2e8f0]"}`}>{value}</p>
    {sub && <p className="text-xs text-[#64748b] mt-0.5">{sub}</p>}
  </div>
);

const CustomPieTooltip = ({ active, payload }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[#0d1225] border border-[#1e2d45] rounded p-2 text-xs">
      <p className="text-[#e2e8f0] font-medium">{payload[0].name}</p>
      <p className="text-[#00d4aa] font-mono">{fmt(payload[0].value)}</p>
    </div>
  );
};

export default function ClientView() {
  const totalEquity = positions.reduce((a, p) => a + p.currentPrice * p.shares, 0);
  const totalCost = positions.reduce((a, p) => a + p.avgCost * p.shares, 0);
  const totalPl = totalEquity - totalCost;
  const cashBalance = 36447.20;
  const portfolioValue = totalEquity + cashBalance;

  return (
    <div className="flex-1 overflow-y-auto p-6 space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-[#e2e8f0]">Portfolio Summary</h1>
          <p className="text-sm text-[#64748b] mt-0.5">Alex Hartmann · Account #AT-00812 · As of Aug 28, 2026 09:45 ET</p>
        </div>
        <div className="flex items-center gap-2 px-3 py-1.5 rounded border border-[#1e2d45] bg-[#0d1225]">
          <div className="w-1.5 h-1.5 rounded-full bg-[#22c55e] animate-pulse" />
          <span className="text-xs text-[#64748b] font-mono">Live Pricing</span>
        </div>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatCard label="TOTAL PORTFOLIO VALUE" value={fmt(portfolioValue)} sub="Equity + Cash" />
        <StatCard label="CASH BALANCE" value={fmt(cashBalance)} sub="Available to trade" accent="text-[#00d4aa]" />
        <StatCard
          label="TOTAL P&L"
          value={(totalPl >= 0 ? "+" : "") + fmt(totalPl)}
          sub={`${((totalPl / totalCost) * 100).toFixed(2)}% return`}
          accent={totalPl >= 0 ? "text-[#22c55e]" : "text-[#ef4444]"}
        />
        <StatCard label="OPEN POSITIONS" value={positions.length.toString()} sub={`${positions.length} securities`} />
      </div>

      {/* Chart + Allocation */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 bg-[#0d1225] border border-[#1e2d45] rounded p-4">
          <p className="text-[10px] text-[#64748b] tracking-wider font-medium mb-4">PORTFOLIO VALUE · 30 DAY</p>
          <div className="h-48">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={portfolioHistory}>
                <defs>
                  <linearGradient id="portGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#00d4aa" stopOpacity={0.15} />
                    <stop offset="100%" stopColor="#00d4aa" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="date" tick={{ fontSize: 9, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} interval={6} />
                <YAxis tick={{ fontSize: 9, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} tickFormatter={(v) => `$${(v / 1000).toFixed(0)}K`} width={40} />
                <Tooltip formatter={(v: any) => [fmt(v as number), "Portfolio Value"]} contentStyle={{ background: "#0d1225", border: "1px solid #1e2d45", borderRadius: 4, fontSize: 11 }} labelStyle={{ color: "#64748b" }} itemStyle={{ color: "#00d4aa" }} />
                <Area type="monotone" dataKey="value" stroke="#00d4aa" strokeWidth={1.5} fill="url(#portGrad)" dot={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-[#0d1225] border border-[#1e2d45] rounded p-4">
          <p className="text-[10px] text-[#64748b] tracking-wider font-medium mb-3">SECTOR ALLOCATION</p>
          <div className="h-36">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={sectorAllocation} dataKey="value" nameKey="sector" cx="50%" cy="50%" innerRadius={40} outerRadius={60} strokeWidth={0}>
                  {sectorAllocation.map((_, i) => (
                    <Cell key={i} fill={SECTOR_COLORS[i % SECTOR_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip content={<CustomPieTooltip />} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="space-y-1.5 mt-2">
            {sectorAllocation.map((s, i) => (
              <div key={s.sector} className="flex items-center justify-between text-[10px]">
                <div className="flex items-center gap-1.5">
                  <div className="w-2 h-2 rounded-sm" style={{ background: SECTOR_COLORS[i] }} />
                  <span className="text-[#94a3b8]">{s.sector}</span>
                </div>
                <span className="font-mono text-[#64748b]">{s.pct}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Holdings table */}
      <div className="bg-[#0d1225] border border-[#1e2d45] rounded">
        <div className="px-4 py-3 border-b border-[#1e2d45]">
          <p className="text-[10px] text-[#64748b] tracking-wider font-medium">CURRENT HOLDINGS</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-[#1e2d45]">
                {["Symbol", "Name", "Sector", "Shares", "Avg Cost", "Current Price", "Market Value", "P&L", "Return"].map((h) => (
                  <th key={h} className="text-left px-4 py-2.5 text-[10px] text-[#64748b] font-medium tracking-wide">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {positions.map((p) => {
                const mv = p.currentPrice * p.shares;
                const pl = (p.currentPrice - p.avgCost) * p.shares;
                const ret = ((p.currentPrice - p.avgCost) / p.avgCost) * 100;
                const pos = pl >= 0;
                return (
                  <tr key={p.symbol} className="border-b border-[#1e2d4540] hover:bg-[#ffffff04]">
                    <td className="px-4 py-3 font-mono font-semibold text-[#e2e8f0]">{p.symbol}</td>
                    <td className="px-4 py-3 text-[#94a3b8]">{p.name}</td>
                    <td className="px-4 py-3">
                      <span className="px-1.5 py-0.5 rounded bg-[#1e2d45] text-[#64748b] text-[10px] font-mono">{p.sector}</span>
                    </td>
                    <td className="px-4 py-3 font-mono text-[#94a3b8]">{p.shares.toLocaleString()}</td>
                    <td className="px-4 py-3 font-mono text-[#94a3b8]">{fmt(p.avgCost)}</td>
                    <td className="px-4 py-3 font-mono text-[#e2e8f0]">{fmt(p.currentPrice)}</td>
                    <td className="px-4 py-3 font-mono text-[#e2e8f0]">{fmt(mv)}</td>
                    <td className={`px-4 py-3 font-mono font-semibold ${pos ? "text-[#22c55e]" : "text-[#ef4444]"}`}>
                      {pos ? "+" : ""}{fmt(pl)}
                    </td>
                    <td className={`px-4 py-3 font-mono ${pos ? "text-[#22c55e]" : "text-[#ef4444]"}`}>
                      {pos ? "+" : ""}{ret.toFixed(2)}%
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Order history */}
      <div className="bg-[#0d1225] border border-[#1e2d45] rounded">
        <div className="px-4 py-3 border-b border-[#1e2d45]">
          <p className="text-[10px] text-[#64748b] tracking-wider font-medium">ORDER HISTORY</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-[#1e2d45]">
                {["Order ID", "Date", "Time", "Symbol", "Side", "Qty", "Price", "Total", "Status"].map((h) => (
                  <th key={h} className="text-left px-4 py-2.5 text-[10px] text-[#64748b] font-medium tracking-wide">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {orderHistory.map((o) => (
                <tr key={o.id} className="border-b border-[#1e2d4540] hover:bg-[#ffffff04]">
                  <td className="px-4 py-3 font-mono text-[#64748b]">{o.id}</td>
                  <td className="px-4 py-3 font-mono text-[#94a3b8]">{o.date}</td>
                  <td className="px-4 py-3 font-mono text-[#94a3b8]">{o.time}</td>
                  <td className="px-4 py-3 font-mono font-semibold text-[#e2e8f0]">{o.symbol}</td>
                  <td className={`px-4 py-3 font-mono font-semibold ${o.side === "BUY" ? "text-[#22c55e]" : "text-[#ef4444]"}`}>{o.side}</td>
                  <td className="px-4 py-3 font-mono text-[#94a3b8]">{o.qty}</td>
                  <td className="px-4 py-3 font-mono text-[#94a3b8]">{fmt(o.price)}</td>
                  <td className="px-4 py-3 font-mono text-[#e2e8f0]">{fmt(o.total)}</td>
                  <td className="px-4 py-3">
                    <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
                      o.status === "FILLED" ? "bg-[#22c55e1a] text-[#22c55e]"
                      : o.status === "PARTIAL" ? "bg-[#f59e0b1a] text-[#f59e0b]"
                      : "bg-[#ef44441a] text-[#ef4444]"
                    }`}>{o.status}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
