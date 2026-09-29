export const priceHistory = Array.from({ length: 60 }, (_, i) => {
  const base = 182.4;
  const noise = () => (Math.random() - 0.48) * 4;
  let price = base;
  for (let j = 0; j < i; j++) price += noise();
  const open = price;
  const close = price + noise();
  const high = Math.max(open, close) + Math.random() * 2;
  const low = Math.min(open, close) - Math.random() * 2;
  const date = new Date(2026, 5, 1);
  date.setHours(9, 30 + i * 6, 0, 0);
  return {
    time: date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
    open: +open.toFixed(2),
    close: +close.toFixed(2),
    high: +high.toFixed(2),
    low: +low.toFixed(2),
    volume: Math.floor(Math.random() * 800000 + 200000),
    price: +close.toFixed(2),
  };
});

export const portfolioHistory = Array.from({ length: 30 }, (_, i) => {
  const base = 248500;
  let val = base;
  for (let j = 0; j < i; j++) val += (Math.random() - 0.44) * 3000;
  const d = new Date(2026, 7, 1);
  d.setDate(d.getDate() + i);
  return {
    date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    value: +val.toFixed(0),
  };
});

export const positions = [
  {
    symbol: 'AAPL',
    name: 'Apple Inc.',
    shares: 150,
    avgCost: 168.42,
    currentPrice: 189.73,
    sector: 'Technology',
  },
  {
    symbol: 'MSFT',
    name: 'Microsoft Corp.',
    shares: 80,
    avgCost: 378.1,
    currentPrice: 412.55,
    sector: 'Technology',
  },
  {
    symbol: 'NVDA',
    name: 'NVIDIA Corp.',
    shares: 60,
    avgCost: 690.0,
    currentPrice: 875.4,
    sector: 'Technology',
  },
  {
    symbol: 'JPM',
    name: 'JPMorgan Chase',
    shares: 120,
    avgCost: 192.3,
    currentPrice: 214.8,
    sector: 'Financials',
  },
  {
    symbol: 'V',
    name: 'Visa Inc.',
    shares: 200,
    avgCost: 255.6,
    currentPrice: 278.9,
    sector: 'Financials',
  },
  {
    symbol: 'XOM',
    name: 'Exxon Mobil',
    shares: 90,
    avgCost: 118.75,
    currentPrice: 108.4,
    sector: 'Energy',
  },
  {
    symbol: 'JNJ',
    name: 'Johnson & Johnson',
    shares: 110,
    avgCost: 155.2,
    currentPrice: 161.3,
    sector: 'Healthcare',
  },
];

export const orderHistory = [
  {
    id: 'ORD-78412',
    symbol: 'AAPL',
    side: 'BUY',
    qty: 50,
    price: 188.9,
    status: 'FILLED',
    time: '09:32:14',
    date: '2026-08-28',
    total: 9445.0,
  },
  {
    id: 'ORD-78411',
    symbol: 'MSFT',
    side: 'SELL',
    qty: 20,
    price: 415.2,
    status: 'FILLED',
    time: '09:28:41',
    date: '2026-08-28',
    total: 8304.0,
  },
  {
    id: 'ORD-78410',
    symbol: 'NVDA',
    side: 'BUY',
    qty: 10,
    price: 871.5,
    status: 'FILLED',
    time: '09:15:03',
    date: '2026-08-28',
    total: 8715.0,
  },
  {
    id: 'ORD-78409',
    symbol: 'V',
    side: 'BUY',
    qty: 30,
    price: 277.4,
    status: 'PARTIAL',
    time: '09:10:22',
    date: '2026-08-28',
    total: 5548.0,
  },
  {
    id: 'ORD-78408',
    symbol: 'JPM',
    side: 'SELL',
    qty: 15,
    price: 213.6,
    status: 'FILLED',
    time: '15:52:11',
    date: '2026-08-27',
    total: 3204.0,
  },
  {
    id: 'ORD-78407',
    symbol: 'XOM',
    side: 'BUY',
    qty: 90,
    price: 118.75,
    status: 'FILLED',
    time: '14:30:00',
    date: '2026-08-27',
    total: 10687.5,
  },
  {
    id: 'ORD-78406',
    symbol: 'AAPL',
    side: 'BUY',
    qty: 100,
    price: 167.8,
    status: 'CANCELLED',
    time: '11:05:44',
    date: '2026-08-27',
    total: 16780.0,
  },
  {
    id: 'ORD-78405',
    symbol: 'JNJ',
    side: 'BUY',
    qty: 110,
    price: 155.2,
    status: 'FILLED',
    time: '10:22:18',
    date: '2026-08-26',
    total: 17072.0,
  },
  {
    id: 'ORD-78404',
    symbol: 'MSFT',
    side: 'BUY',
    qty: 80,
    price: 378.1,
    status: 'FILLED',
    time: '09:45:02',
    date: '2026-08-26',
    total: 30248.0,
  },
  {
    id: 'ORD-78403',
    symbol: 'NVDA',
    side: 'SELL',
    qty: 25,
    price: 901.2,
    status: 'FILLED',
    time: '15:58:33',
    date: '2026-08-25',
    total: 22530.0,
  },
];

export const auditLog = [
  ...orderHistory.map((o) => ({
    ...o,
    userId: 'trader_001',
    userName: 'Alex Hartmann',
    ip: '192.168.1.42',
    executedAt: `${o.date}T${o.time}Z`,
    venue: o.status === 'CANCELLED' ? '—' : 'NASDAQ',
    commission: o.status === 'CANCELLED' ? 0 : +(o.total * 0.001).toFixed(2),
    notes: o.status === 'PARTIAL' ? 'Partial fill — insufficient liquidity at limit' : '',
  })),
  {
    id: 'ORD-78402',
    symbol: 'V',
    side: 'BUY',
    qty: 170,
    price: 255.6,
    status: 'FILLED',
    time: '14:02:55',
    date: '2026-08-25',
    total: 43452.0,
    userId: 'trader_002',
    userName: 'Alex Hartmann',
    ip: '10.0.0.15',
    executedAt: '2026-08-25T14:02:55Z',
    venue: 'NYSE',
    commission: 43.45,
    notes: '',
  },
];

export const sectorAllocation = [
  { sector: 'Technology', value: 118047, pct: 47.2 },
  { sector: 'Financials', value: 68532, pct: 27.4 },
  { sector: 'Healthcare', value: 17743, pct: 7.1 },
  { sector: 'Energy', value: 9756, pct: 3.9 },
  { sector: 'Cash', value: 36447, pct: 14.4 },
];

export const dailyPnl = Array.from({ length: 14 }, (_, i) => {
  const d = new Date(2026, 7, 15);
  d.setDate(d.getDate() + i);
  const pnl = Math.random() * 8000 - 2000;
  return {
    date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    pnl: +pnl.toFixed(2),
  };
});
