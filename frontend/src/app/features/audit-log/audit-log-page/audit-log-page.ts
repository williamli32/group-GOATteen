import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface AuditLogRecord {
  id: string;
  executedAt: string;
  userName: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  qty: number;
  price: number;
  total: number;
  commission: number;
  venue: string;
  status: 'FILLED' | 'PARTIAL' | 'CANCELLED';
  userId: string;
  ip: string;
  notes?: string;
}

const MOCK_AUDIT_LOG: AuditLogRecord[] = [
  {
    id: 'ORD-7€412',
    executedAt: '2024-08-28 09:23:14 AM',
    userName: 'Alex Hartmann',
    symbol: 'AAPL',
    side: 'BUY',
    qty: 25,
    price: 180.50,
    total: 4512.50,
    commission: 4.50,
    venue: 'NASDAQ',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1',
    notes: 'Market order executed at open.'
  },
  {
    id: 'ORD-7€411',
    executedAt: 'Aug 28, 09:28:41 AM',
    userName: 'Alex Hartmann',
    symbol: 'MSFT',
    side: 'SELL',
    qty: 20,
    price: 415.20,
    total: 8304.00,
    commission: 8.30,
    venue: 'NASDAQ',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1'
  },
  {
    id: 'ORD-7€410',
    executedAt: 'Aug 28, 09:15:03 AM',
    userName: 'Alex Hartmann',
    symbol: 'NVDA',
    side: 'BUY',
    qty: 10,
    price: 871.50,
    total: 8715.00,
    commission: 8.71,
    venue: 'NASDAQ',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1'
  },
  {
    id: 'ORD-7€409',
    executedAt: 'Aug 28, 09:10:22 AM',
    userName: 'Alex Hartmann',
    symbol: 'V',
    side: 'BUY',
    qty: 30,
    price: 277.60,
    total: 8328.00,
    commission: 8.33,
    venue: 'NASDAQ',
    status: 'PARTIAL',
    userId: 'USR-001',
    ip: '192.168.1.1',
    notes: 'Partially filled at market price.'
  },
  {
    id: 'ORD-7€408',
    executedAt: 'Aug 27, 03:22:11 PM',
    userName: 'Alex Hartmann',
    symbol: 'JPM',
    side: 'SELL',
    qty: 15,
    price: 213.60,
    total: 3204.00,
    commission: 3.20,
    venue: 'NASDAQ',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1'
  },
  {
    id: 'ORD-7€407',
    executedAt: 'Aug 27, 02:10:00 PM',
    userName: 'Alex Hartmann',
    symbol: 'XOM',
    side: 'BUY',
    qty: 50,
    price: 118.75,
    total: 5937.50,
    commission: 5.94,
    venue: 'NASDAQ',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1'
  },
  {
    id: 'ORD-7€406',
    executedAt: 'Aug 27, 11:05:44 AM',
    userName: 'Alex Hartmann',
    symbol: 'AAPL',
    side: 'BUY',
    qty: 100,
    price: 167.00,
    total: 16700.00,
    commission: 0,
    venue: '—',
    status: 'CANCELLED',
    userId: 'USR-001',
    ip: '192.168.1.1',
    notes: 'Order cancelled by user.'
  },
  {
    id: 'ORD-7€405',
    executedAt: 'Aug 26, 10:12:18 AM',
    userName: 'Alex Hartmann',
    symbol: 'JNJ',
    side: 'BUY',
    qty: 110,
    price: 155.20,
    total: 17072.00,
    commission: 17.07,
    venue: 'NASDAQ',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1'
  },
  {
    id: 'ORD-7€404',
    executedAt: 'Aug 26, 09:45:02 AM',
    userName: 'Alex Hartmann',
    symbol: 'MSFT',
    side: 'BUY',
    qty: 80,
    price: 378.10,
    total: 30248.00,
    commission: 30.25,
    venue: 'NASDAQ',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1'
  },
  {
    id: 'ORD-7€403',
    executedAt: 'Aug 25, 03:58:33 PM',
    userName: 'Alex Hartmann',
    symbol: 'NVDA',
    side: 'SELL',
    qty: 25,
    price: 901.20,
    total: 22530.00,
    commission: 22.53,
    venue: 'NASDAQ',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1'
  },
  {
    id: 'ORD-7€402',
    executedAt: 'Aug 25, 02:02:55 PM',
    userName: 'Alex Hartmann',
    symbol: 'V',
    side: 'BUY',
    qty: 170,
    price: 255.60,
    total: 43452.00,
    commission: 43.45,
    venue: 'NYSE',
    status: 'FILLED',
    userId: 'USR-001',
    ip: '192.168.1.1'
  }
];

@Component({
  imports: [CommonModule, FormsModule],
  selector: 'app-audit-log-page',
  styleUrl: './audit-log-page.scss',
  templateUrl: './audit-log-page.html',
})
export class AuditLogPage {
  auditLog: AuditLogRecord[] = MOCK_AUDIT_LOG;
  search: string = '';
  statusFilter: string = 'ALL';
  expandedId: string | null = null;

  get filteredRecords(): AuditLogRecord[] {
    return this.auditLog.filter((record) => {
      const matchSearch =
        record.id.toLowerCase().includes(this.search.toLowerCase()) ||
        record.symbol.toLowerCase().includes(this.search.toLowerCase()) ||
        record.userName.toLowerCase().includes(this.search.toLowerCase());
      const matchStatus = this.statusFilter === 'ALL' || record.status === this.statusFilter;
      return matchSearch && matchStatus;
    });
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(value);
  }

  formatDateTime(dateStr: string): string {
    try {
      const date = new Date(dateStr);
      return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return dateStr;
    }
  }

  toggleExpand(id: string): void {
    this.expandedId = this.expandedId === id ? null : id;
  }

  getStatusStyle(status: string): string {
    const styles: Record<string, string> = {
      FILLED: 'status-filled',
      PARTIAL: 'status-partial',
      CANCELLED: 'status-cancelled',
    };
    return styles[status] || '';
  }
}
