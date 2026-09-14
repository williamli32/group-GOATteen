import { useState } from "react";
import { useNavigate } from "react-router-dom";

export default function Auth() {
  const [mode, setMode] = useState<"signin" | "signup">("signin");
  const [form, setForm] = useState({ name: "", email: "", password: "", confirm: "" });
  const [error, setError] = useState("");
  const navigate = useNavigate();

  function handle(e: React.FormEvent) {
    e.preventDefault();
    if (mode === "signup" && form.password !== form.confirm) {
      setError("Passwords do not match.");
      return;
    }
    if (!form.email || !form.password) {
      setError("Please fill in all required fields.");
      return;
    }
    navigate("/dashboard");
  }

  return (
    <div className="min-h-full flex">
      {/* Left panel */}
      <div className="hidden lg:flex flex-col justify-between w-1/2 bg-[#0d1225] border-r border-[#1e2d45] p-12 relative overflow-hidden">
        <div className="absolute inset-0 pointer-events-none">
          {/* Grid lines */}
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="absolute top-0 bottom-0 border-r border-[#1e2d4520]" style={{ left: `${(i + 1) * 12.5}%` }} />
          ))}
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="absolute left-0 right-0 border-b border-[#1e2d4520]" style={{ top: `${(i + 1) * 12.5}%` }} />
          ))}
        </div>
        <div className="relative">
          <span className="font-mono text-[#00d4aa] font-semibold tracking-widest text-lg">APEX</span>
          <span className="font-mono text-[#64748b] text-lg tracking-widest ml-1">TRADE</span>
        </div>
        <div className="relative space-y-6">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded border border-[#00d4aa30] bg-[#00d4aa0a]">
            <div className="w-1.5 h-1.5 rounded-full bg-[#00d4aa] animate-pulse" />
            <span className="font-mono text-xs text-[#00d4aa] tracking-wide">MARKETS OPEN</span>
          </div>
          <h1 className="text-4xl font-semibold text-[#e2e8f0] leading-tight">
            Institutional-grade<br />
            <span className="text-[#00d4aa]">execution</span> at<br />
            your fingertips.
          </h1>
          <p className="text-[#64748b] text-sm leading-relaxed max-w-xs">
            Real-time market data, sub-millisecond order routing, and full audit trails — built for professionals.
          </p>
          <div className="grid grid-cols-3 gap-4 pt-4">
            {[
              { label: "Avg Fill Time", val: "4.2ms" },
              { label: "Uptime SLA", val: "99.99%" },
              { label: "Markets", val: "140+" },
            ].map((s) => (
              <div key={s.label} className="border border-[#1e2d45] rounded p-3">
                <p className="font-mono text-lg font-semibold text-[#00d4aa]">{s.val}</p>
                <p className="text-[10px] text-[#64748b] mt-0.5">{s.label}</p>
              </div>
            ))}
          </div>
        </div>
        <p className="relative text-[10px] text-[#64748b]">© 2026 ApexTrade Securities LLC. All rights reserved.</p>
      </div>

      {/* Right panel */}
      <div className="flex-1 flex items-center justify-center p-8 bg-[#080c18]">
        <div className="w-full max-w-sm">
          <div className="mb-8">
            <div className="lg:hidden mb-6">
              <span className="font-mono text-[#00d4aa] font-semibold tracking-widest text-lg">APEX</span>
              <span className="font-mono text-[#64748b] text-lg tracking-widest ml-1">TRADE</span>
            </div>
            <h2 className="text-2xl font-semibold text-[#e2e8f0]">
              {mode === "signin" ? "Welcome back" : "Create account"}
            </h2>
            <p className="text-sm text-[#64748b] mt-1">
              {mode === "signin" ? "Sign in to your trading terminal" : "Get started with ApexTrade"}
            </p>
          </div>

          {/* Tab toggle */}
          <div className="flex rounded border border-[#1e2d45] p-0.5 mb-6 bg-[#0d1225]">
            {(["signin", "signup"] as const).map((m) => (
              <button
                key={m}
                onClick={() => { setMode(m); setError(""); }}
                className={`flex-1 py-2 text-sm rounded transition-all duration-150 ${
                  mode === m
                    ? "bg-[#00d4aa] text-[#080c18] font-semibold"
                    : "text-[#64748b] hover:text-[#94a3b8]"
                }`}
              >
                {m === "signin" ? "Sign In" : "Sign Up"}
              </button>
            ))}
          </div>

          <form onSubmit={handle} className="space-y-4">
            {mode === "signup" && (
              <div>
                <label className="block text-xs text-[#64748b] mb-1.5 font-medium tracking-wide">Full Name</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="Alex Hartmann"
                  className="w-full bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2.5 text-sm text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors"
                />
              </div>
            )}
            <div>
              <label className="block text-xs text-[#64748b] mb-1.5 font-medium tracking-wide">Email</label>
              <input
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                placeholder="you@firm.com"
                className="w-full bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2.5 text-sm text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors"
              />
            </div>
            <div>
              <label className="block text-xs text-[#64748b] mb-1.5 font-medium tracking-wide">Password</label>
              <input
                type="password"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                placeholder="••••••••"
                className="w-full bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2.5 text-sm text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors"
              />
            </div>
            {mode === "signup" && (
              <div>
                <label className="block text-xs text-[#64748b] mb-1.5 font-medium tracking-wide">Confirm Password</label>
                <input
                  type="password"
                  value={form.confirm}
                  onChange={(e) => setForm({ ...form, confirm: e.target.value })}
                  placeholder="••••••••"
                  className="w-full bg-[#0d1225] border border-[#1e2d45] rounded px-3 py-2.5 text-sm text-[#e2e8f0] placeholder-[#2a3f5f] focus:outline-none focus:border-[#00d4aa] transition-colors"
                />
              </div>
            )}
            {error && <p className="text-xs text-[#ef4444] bg-[#ef444418] px-3 py-2 rounded border border-[#ef444430]">{error}</p>}
            <button
              type="submit"
              className="w-full bg-[#00d4aa] hover:bg-[#00bfa0] text-[#080c18] font-semibold py-2.5 rounded text-sm transition-colors mt-2"
            >
              {mode === "signin" ? "Access Terminal" : "Create Account"}
            </button>
          </form>

          {mode === "signin" && (
            <div className="mt-4 text-center">
              <a href="#" className="text-xs text-[#64748b] hover:text-[#94a3b8] transition-colors">Forgot password?</a>
            </div>
          )}

          <p className="mt-6 text-center text-[10px] text-[#2a3f5f]">
            Protected by 256-bit TLS encryption · FINRA / SEC compliant
          </p>
        </div>
      </div>
    </div>
  );
}
