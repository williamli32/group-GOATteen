import { useState } from "react";
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine,
} from "recharts";
import { priceHistory, positions, orderHistory } from "../data/mockData";

const SYMBOLS = ["AAPL", "MSFT", "NVDA", "JPM", "V", "XOM"];

function fmt(n: number) {
  return n.toLocaleString("en-US", { style: "currency", currency: "USD" });
}

function pct(curr: number, avg: number) {
  return (((curr - avg) / avg) * 100).toFixed(2);
}

const CustomTooltip = ({ active, payload }: any) => {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div className="bg-[#0d1225] border border-[#1e2d45] rounded p-2.5 text-xs font-mono">
      <p className="text-[#64748b] mb-1">{d.time}</p>
      <p className="text-[#e2e8f0]">Price: <span className="text-[#00d4aa]">{fmt(d.price)}</span></p>
      <p className="text-[#94a3b8]">Vol: {(d.volume / 1000).toFixed(0)}K</p>
    </div>
  );
};

export default function Dashboard() {
  const [ticker, setTicker] = useState("AAPL");
  const [orderType, setOrderType] = useState<"MARKET" | "LIMIT" | "STOP">("MARKET");
  const [side, setSide] = useState<"BUY" | "SELL">("BUY");
  const [qty, setQty] = useState("100");
  const [limitPrice, setLimitPrice] = useState("189.73");
  const [submitted, setSubmitted] = useState(false);

  const lastPrice = priceHistory[priceHistory.length - 1].price;
  const firstPrice = priceHistory[0].price;
  const change = lastPrice - firstPrice;
  const changePct = ((change / firstPrice) * 100).toFixed(2);
  const isPos = change >= 0;

  function submitOrder(e: React.FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    setTimeout(() => setSubmitted(false), 2500);
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Topbar */}
      <div className="h-14 shrink-0 border-b border-[#1e2d45] bg-[#0d1225] flex items-center px-4 gap-4">
        <div className="flex gap-1">
          {SYMBOLS.map((s) => (
            <button
              key={s}
              onClick={() => setTicker(s)}
              className={`px-2.5 py-1 text-xs font-mono rounded transition-all ${
                ticker === s
                  ? "bg-[#00d4aa1a] text-[#00d4aa] border border-[#00d4aa40]"
                  : "text-[#64748b] hover:text-[#94a3b8] hover:bg-[#ffffff06]"
              }`}
            >
              {s}
            </button>
          ))}
        </div>
        <div className="ml-auto flex items-center gap-4 text-xs font-mono">
          <span className="text-[#64748b]">Last</span>
          <span className="text-[#e2e8f0] font-semibold">{fmt(lastPrice)}</span>
          <span className={isPos ? "text-[#22c55e]" : "text-[#ef4444]"}>
            {isPos ? "+" : ""}{change.toFixed(2)} ({isPos ? "+" : ""}{changePct}%)
          </span>
          <div className="w-px h-4 bg-[#1e2d45]" />
          <span className="text-[#64748b]">VOL</span>
          <span className="text-[#94a3b8]">18.4M</span>
          <div className="flex items-center gap-1.5 ml-2">
            <div className="w-1.5 h-1.5 rounded-full bg-[#22c55e] animate-pulse" />
            <span className="text-[#22c55e] text-[10px] tracking-wider">LIVE</span>
          </div>
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Main area */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Chart */}
          <div className="flex-1 p-4 overflow-hidden flex flex-col">
            <div className="flex items-baseline gap-3 mb-3">
              <span className="font-mono text-2xl font-semibold text-[#e2e8f0]">{ticker}</span>
              <span className="font-mono text-sm text-[#64748b]">NASDAQ · USD</span>
            </div>
            <div className="flex-1 min-h-0">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={priceHistory} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
                  <defs>
                    <linearGradient id="priceGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#00d4aa" stopOpacity={0.2} />
                      <stop offset="100%" stopColor="#00d4aa" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <XAxis dataKey="time" tick={{ fontSize: 10, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} interval={9} />
                  <YAxis domain={["auto", "auto"]} tick={{ fontSize: 10, fill: "#64748b", fontFamily: "JetBrains Mono" }} tickLine={false} axisLine={false} tickFormatter={(v) => `$${v.toFixed(0)}`} width={50} />
                  <Tooltip content={<CustomTooltip />} />
                  <ReferenceLine y={firstPrice} stroke="#1e2d45" strokeDasharray="3 3" />
                  <Area type="monotone" dataKey="price" stroke="#00d4aa" strokeWidth={1.5} fill="url(#priceGrad)" dot={false} activeDot={{ r: 3, fill: "#00d4aa", strokeWidth: 0 }} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Positions table */}
          <div className="border-t border-[#1e2d45] p-4">
            <p className="text-xs font-medium text-[#64748b] tracking-wider mb-3">POSITIONS</p>
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-[#64748b] font-mono">
                    {["Symbol", "Shares", "Avg Cost", "Last", "P&L", "P&L %"].map((h) => (
                      <th key={h} className="text-left pb-2 font-medium pr-6">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {positions.map((p) => {
                    const pl = (p.currentPrice - p.avgCost) * p.shares;
                    const plPct = pct(p.currentPrice, p.avgCost);
                    const pos = pl >= 0;
                    return (
                      <tr key={p.symbol} className="border-t border-[#1e2d4540] hover:bg-[#ffffff04] cursor-pointer" onClick={() => setTicker(p.symbol)}>
                        <td className="py-2 pr-6 font-mono font-semibold text-[#e2e8f0]">{p.symbol}</td>
                        <td className="py-2 pr-6 font-mono text-[#94a3b8]">{p.shares}</td>
                        <td className="py-2 pr-6 font-mono text-[#94a3b8]">{fmt(p.avgCost)}</td>
                        <td className="py-2 pr-6 font-mono text-[#e2e8f0]">{fmt(p.currentPrice)}</td>
                        <td className={`py-2 pr-6 font-mono font-semibold ${pos ? "text-[#22c55e]" : "text-[#ef4444]"}`}>{pos ? "+" : ""}{fmt(pl)}</td>
                        <td className={`py-2 font-mono ${pos ? "text-[#22c55e]" : "text-[#ef4444]"}`}>{pos ? "+" : ""}{plPct}%</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Right rail */}
        <div className="w-72 shrink-0 border-l border-[#1e2d45] flex flex-col overflow-hidden">
          {/* Order panel */}
          <div className="p-4 border-b border-[#1e2d45]">
            <p className="text-xs font-medium text-[#64748b] tracking-wider mb-3">ORDER TICKET · {ticker}</p>

            <div className="flex rounded border border-[#1e2d45] mb-3 bg-[#0d1225] overflow-hidden">
              {(["BUY", "SELL"] as const).map((s) => (
                <button
                  key={s}
                  onClick={() => setSide(s)}
                  className={`flex-1 py-2 text-xs font-semibold transition-all ${
                    side === s
                      ? s === "BUY" ? "bg-[#22c55e] text-[#080c18]" : "bg-[#ef4444] text-white"
                      : "text-[#64748b] hover:text-[#94a3b8]"
                  }`}
                >
                  {s}
                </button>
              ))}
            </div>

            <form onSubmit={submitOrder} className="space-y-3">
              <div>
                <label className="block text-[10px] text-[#64748b] mb-1 tracking-wide">ORDER TYPE</label>
                <select
                  value={orderType}
                  onChange={(e) => setOrderType(e.target.value as any)}
                  className="w-full bg-[#080c18] border border-[#1e2d45] rounded px-2.5 py-2 text-xs text-[#e2e8f0] focus:outline-none focus:border-[#00d4aa]"
                >
                  <option>MARKET</option>
                  <option>LIMIT</option>
                  <option>STOP</option>
                </select>
              </div>
              <div>
                <label className="block text-[10px] text-[#64748b] mb-1 tracking-wide">QUANTITY</label>
                <input
                  type="number"
                  value={qty}
                  onChange={(e) => setQty(e.target.value)}
                  className="w-full bg-[#080c18] border border-[#1e2d45] rounded px-2.5 py-2 text-xs font-mono text-[#e2e8f0] focus:outline-none focus:border-[#00d4aa]"
                />
              </div>
              {orderType !== "MARKET" && (
                <div>
                  <label className="block text-[10px] text-[#64748b] mb-1 tracking-wide">{orderType} PRICE</label>
                  <input
                    type="number"
                    step="0.01"
                    value={limitPrice}
                    onChange={(e) => setLimitPrice(e.target.value)}
                    className="w-full bg-[#080c18] border border-[#1e2d45] rounded px-2.5 py-2 text-xs font-mono text-[#e2e8f0] focus:outline-none focus:border-[#00d4aa]"
                  />
                </div>
              )}
              <div className="bg-[#0d1225] rounded p-2.5 border border-[#1e2d45]">
                <div className="flex justify-between text-[10px] text-[#64748b] mb-1">
                  <span>Estimated Value</span>
                  <span className="font-mono text-[#94a3b8]">{fmt(lastPrice * Number(qty || 0))}</span>
                </div>
                <div className="flex justify-between text-[10px] text-[#64748b]">
                  <span>Commission</span>
                  <span className="font-mono text-[#94a3b8]">{fmt(lastPrice * Number(qty || 0) * 0.001)}</span>
                </div>
              </div>
              <button
                type="submit"
                className={`w-full py-2.5 rounded text-xs font-semibold transition-all ${
                  submitted
                    ? "bg-[#22c55e] text-[#080c18]"
                    : side === "BUY"
                      ? "bg-[#22c55e] hover:bg-[#16a34a] text-[#080c18]"
                      : "bg-[#ef4444] hover:bg-[#dc2626] text-white"
                }`}
              >
                {submitted ? "✓ ORDER SUBMITTED" : `PLACE ${side} ORDER`}
              </button>
            </form>
          </div>

          {/* Recent orders */}
          <div className="flex-1 p-4 overflow-y-auto">
            <p className="text-xs font-medium text-[#64748b] tracking-wider mb-3">RECENT ORDERS</p>
            <div className="space-y-2">
              {orderHistory.slice(0, 6).map((o) => (
                <div key={o.id} className="bg-[#0d1225] rounded border border-[#1e2d45] p-2.5">
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-mono text-xs font-semibold text-[#e2e8f0]">{o.symbol}</span>
                    <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${
                      o.status === "FILLED" ? "bg-[#22c55e1a] text-[#22c55e]"
                      : o.status === "PARTIAL" ? "bg-[#f59e0b1a] text-[#f59e0b]"
                      : "bg-[#ef44441a] text-[#ef4444]"
                    }`}>{o.status}</span>
                  </div>
                  <div className="flex justify-between text-[10px] font-mono text-[#64748b]">
                    <span className={o.side === "BUY" ? "text-[#22c55e]" : "text-[#ef4444]"}>{o.side}</span>
                    <span>{o.qty} @ {fmt(o.price)}</span>
                  </div>
                  <p className="text-[10px] text-[#2a3f5f] mt-0.5">{o.time}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
