import { useState } from "react";
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell,
  LineChart, Line, Legend,
} from "recharts";
import { dailyPnl, sectorAllocation, orderHistory } from "../data/mockData";

function fmt(n: number) {
  const abs = Math.abs(n);
  const sign = n < 0 ? "-" : n > 0 ? "+" : "";
  if (abs >= 1000) return `${sign}$${(abs / 1000).toFixed(1)}K`;
  return `${sign}$${abs.toFixed(0)}`;
}

function fmtFull(n: number) {
  return n.toLocaleString("en-US", { style: "currency", currency: "USD" });
}

const volumeBySymbol = ["AAPL", "MSFT", "NVDA", "JPM", "V", "XOM", "JNJ"].map((s) => {
  const orders = orderHistory.filter((o) => o.symbol === s);
  return { symbol: s, volume: orders.reduce((a, o) => a + o.total, 0), count: orders.length };
});

const monthlyActivity = [
  { month: "Mar", buys: 12, sells: 5, volume: 142000 },
  { month: "Apr", buys: 18, sells: 9, volume: 198000 },
  { month: "May", buys: 22, sells: 14, volume: 267000 },
  { month: "Jun", buys: 15, sells: 11, volume: 183000 },
  { month: "Jul", buys: 25, sells: 18, volume: 312000 },
  { month: "Aug", buys: 9, sells: 6, volume: 147000 },
];

const MetricCard = ({ label, value, delta, sub }: { label: string; value: string; delta?: string; sub?: string }) => (
  <div className="bg-[#0d1225] border border-[#1e2d45] rounded p-4">
    <p className="text-[10px] text-[#64748b] tracking-wider font-medium mb-2">{label}</p>
    <p className="font-mono text-xl font-semibold text-[#e2e8f0]">{value}</p>
    {delta && <p className={`text-xs font-mono mt-0.5 ${delta.startsWith("+") ? "text-[#22c55e]" : "text-[#ef4444]"}`}>{delta} vs prev. period</p>}
    {sub && <p className="text-xs text-[#64748b] mt-0.5">{sub}</p>}
  </div>
);

export default function Reports() {
  const [period, setPeriod] = useState<"7D" | "30D" | "MTD" | "YTD">("30D");

  const totalPnl = dailyPnl.reduce((a, d) => a + d.pnl, 0);
  const winDays = dailyPnl.filter((d) => d.pnl > 0).length;
  const bestDay = Math.max(...dailyPnl.map((d) => d.pnl));
  const worstDay = Math.min(...dailyPnl.map((d) => d.pnl));
  const filled = orderHistory.filter((o) => o.status === "FILLED").length;
  const fillRate = ((filled / orderHistory.length) * 100).toFixed(1);

  return (
    <div className="flex-1 overflow-y-auto p-6 space-y-5">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-[#e2e8f0]">Internal Reports</h1>
          <p className="text-sm text-[#64748b] mt-0.5">Trading activity analytics · Internal use only</p>
        </div>
        <div className="flex gap-1">
          {(["7D", "30D", "MTD", "YTD"] as const).map((p) => (
            <button
              key={p}
              onClick={() => setPeriod(p)}
              className={`px-3 py-1.5 text-xs font-mono rounded border transition-all ${
                period === p
                  ? "bg-[#00d4aa1a] text-[#00d4aa] border-[#00d4aa40]"
                  : "border-[#1e2d45] text-[#64748b] hover:text-[#94a3b8]"
              }`}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <MetricCard label="TOTAL P&L" value={fmt(totalPnl)} delta={totalPnl >= 0 ? `+${((totalPnl / 248500) * 100).toFixed(2)}%` : `${((totalPnl / 248500) * 100).toFixed(2)}%`} />
        <MetricCard label="WIN RATE (DAYS)" value={`${winDays}/${dailyPnl.length}`} sub={`${((winDays / dailyPnl.length) * 100).toFixed(0)}% profitable days`} />
        <MetricCard label="BEST DAY" value={fmt(bestDay)} sub={dailyPnl.find((d) => d.pnl === bestDay)?.date} />
        <MetricCard label="ORDER FILL RATE" value={`${fillRate}%`} sub={`${filled} of ${orderHistory.length} orders filled`} />
      </div>

      {/* Daily P&L chart */}
      <div className="bg-[#0d1225] border border-[#1e2d45] rounded p-4">
        <div className="flex items-center justify-between mb-4">
          <p className="text-[10px] text-[#64748b] tracking-wider font-medium">DAILY P&L · LAST 14 TRADING DAYS</p>
          <div className="flex gap-4 text-[10px] font-mono">
            <span className="text-[#64748b]">Best: <span className="text-[#22c55e]">{fmt(bestDay)}</span></span>
            <span className="text-[#64748b]">Worst: <span className="text-[#ef4444]">{fmt(worstDay)}</span></span>
          </div>
        </div>
        <div className="h-52">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={dailyPnl} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
              <XAxis dataKey="date" tick={{ fontSize: 9, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 9, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} tickFormatter={fmt} width={48} />
              <Tooltip
                formatter={(v: any) => [fmtFull(v as number), "P&L"]}
                contentStyle={{ background: "#0d1225", border: "1px solid #1e2d45", borderRadius: 4, fontSize: 11 }}
                labelStyle={{ color: "#64748b" }}
                itemStyle={{ color: "#e2e8f0" }}
              />
              <Bar dataKey="pnl" radius={[2, 2, 0, 0]}>
                {dailyPnl.map((d, i) => (
                  <Cell key={i} fill={d.pnl >= 0 ? "#22c55e" : "#ef4444"} fillOpacity={0.8} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Volume by symbol */}
        <div className="bg-[#0d1225] border border-[#1e2d45] rounded p-4">
          <p className="text-[10px] text-[#64748b] tracking-wider font-medium mb-4">TRADED VOLUME BY SYMBOL</p>
          <div className="h-44">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={volumeBySymbol} layout="vertical" margin={{ top: 0, right: 16, bottom: 0, left: 0 }}>
                <XAxis type="number" tick={{ fontSize: 9, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} tickFormatter={(v) => `$${(v / 1000).toFixed(0)}K`} />
                <YAxis type="category" dataKey="symbol" tick={{ fontSize: 10, fill: "#94a3b8", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} width={36} />
                <Tooltip formatter={(v: any) => [fmtFull(v as number), "Volume"]} contentStyle={{ background: "#0d1225", border: "1px solid #1e2d45", borderRadius: 4, fontSize: 11 }} labelStyle={{ color: "#64748b" }} itemStyle={{ color: "#00d4aa" }} />
                <Bar dataKey="volume" fill="#00d4aa" fillOpacity={0.7} radius={[0, 2, 2, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Monthly buys vs sells */}
        <div className="bg-[#0d1225] border border-[#1e2d45] rounded p-4">
          <p className="text-[10px] text-[#64748b] tracking-wider font-medium mb-4">MONTHLY ORDER COUNT · BUYS VS SELLS</p>
          <div className="h-44">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={monthlyActivity}>
                <XAxis dataKey="month" tick={{ fontSize: 9, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} />
                <YAxis tick={{ fontSize: 9, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} width={24} />
                <Tooltip contentStyle={{ background: "#0d1225", border: "1px solid #1e2d45", borderRadius: 4, fontSize: 11 }} labelStyle={{ color: "#64748b" }} />
                <Legend wrapperStyle={{ fontSize: 10, color: "#64748b" }} />
                <Line type="monotone" dataKey="buys" stroke="#22c55e" strokeWidth={1.5} dot={{ r: 2, fill: "#22c55e", strokeWidth: 0 }} name="Buys" />
                <Line type="monotone" dataKey="sells" stroke="#ef4444" strokeWidth={1.5} dot={{ r: 2, fill: "#ef4444", strokeWidth: 0 }} name="Sells" />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Sector activity table */}
      <div className="bg-[#0d1225] border border-[#1e2d45] rounded">
        <div className="px-4 py-3 border-b border-[#1e2d45] flex items-center justify-between">
          <p className="text-[10px] text-[#64748b] tracking-wider font-medium">SECTOR EXPOSURE BREAKDOWN</p>
          <span className="text-[10px] font-mono text-[#2a3f5f]">Current period</span>
        </div>
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-[#1e2d45] bg-[#080c18]">
              {["Sector", "Allocation %", "Market Value", "P&L Contribution", "Weight Bar"].map((h) => (
                <th key={h} className="text-left px-4 py-2.5 text-[10px] text-[#64748b] font-medium tracking-wide">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sectorAllocation.map((s) => {
              const plContrib = s.value * (Math.random() * 0.14 - 0.04);
              const pos = plContrib >= 0;
              return (
                <tr key={s.sector} className="border-b border-[#1e2d4540] hover:bg-[#ffffff04]">
                  <td className="px-4 py-3 text-[#94a3b8] font-medium">{s.sector}</td>
                  <td className="px-4 py-3 font-mono text-[#e2e8f0]">{s.pct}%</td>
                  <td className="px-4 py-3 font-mono text-[#e2e8f0]">{fmtFull(s.value)}</td>
                  <td className={`px-4 py-3 font-mono font-semibold ${pos ? "text-[#22c55e]" : "text-[#ef4444]"}`}>
                    {pos ? "+" : ""}{fmtFull(plContrib)}
                  </td>
                  <td className="px-4 py-3">
                    <div className="w-full bg-[#1e2d45] rounded-full h-1.5 overflow-hidden">
                      <div className="h-full rounded-full bg-[#00d4aa]" style={{ width: `${s.pct}%` }} />
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="flex items-center gap-3 px-4 py-3 bg-[#0d1225] border border-[#1e2d45] rounded">
        <span className="text-[#f59e0b] text-sm">▲</span>
        <p className="text-xs text-[#64748b]">
          Internal reports are refreshed every <span className="text-[#94a3b8]">15 minutes</span> during market hours. Data is for internal use only and not for client distribution.
        </p>
      </div>
    </div>
  );
}
