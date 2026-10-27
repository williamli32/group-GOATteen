import { CommonModule } from '@angular/common';
import {
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, Subscription } from 'rxjs';
import {
  AccountResponse,
  AccountService,
  HoldingsResponse,
  OrderResponse,
} from '../core/services/account';
import { MarketDataService } from '../core/services/market-data';
import { OrderService, PlaceOrderRequest } from '../core/services/order';
import { ChatbotCommand, parseChatbotCommand, stockQuantity } from './chatbot-command';

type TradeCommand = Extract<ChatbotCommand, { kind: 'trade' }>;
interface TradePreview {
  accountId: number;
  request: PlaceOrderRequest;
  symbol: string;
  name: string;
  exchange: string;
  currency: string;
  unitPrice: number;
  quotedAt: string;
}
interface PendingTrade extends TradePreview {
  key: string;
}
interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  holdings?: HoldingsResponse;
  fetchedAt?: string;
}

const HELP =
  'I can buy or sell stocks and show your holdings. Try “buy 5 AAPL”, “sell MSFT 2”, ' +
  '“show my holdings”, or “how many AAPL shares do I own?”. Give a share quantity, not a cash amount. ' +
  'You will review each trade before it is submitted.';

@Component({
  selector: 'app-trading-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './trading-chatbot.component.html',
  styleUrl: './trading-chatbot.component.scss',
})
export class TradingChatbotComponent {
  readonly account = input.required<AccountResponse>();
  readonly orderChanged = output<OrderResponse>();
  readonly isOpen = signal(false);
  readonly busy = signal(false);
  readonly messages = signal<ChatMessage[]>([]);
  readonly preview = signal<TradePreview | null>(null);
  readonly pending = signal<PendingTrade | null>(null);
  readonly storageError = signal(false);
  message = '';
  private awaitingQuantity: TradeCommand | null = null;
  private activeAccountId: number | null = null;
  private activeRequest?: Subscription;
  private messageId = 0;
  private readonly accounts = inject(AccountService);
  private readonly market = inject(MarketDataService);
  private readonly orders = inject(OrderService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly conversation = viewChild<ElementRef<HTMLElement>>('conversation');

  constructor() {
    effect(() => {
      this.messages();
      const log = this.conversation();
      if (log)
        queueMicrotask(() => {
          log.nativeElement.scrollTop = log.nativeElement.scrollHeight;
        });
    });
    effect(() => {
      const accountId = this.account().accountId;
      if (this.activeAccountId === accountId) return;
      untracked(() => {
        this.activeRequest?.unsubscribe();
        this.activeAccountId = accountId;
        this.busy.set(false);
        this.preview.set(null);
        this.pending.set(null);
        this.awaitingQuantity = null;
        this.message = '';
        this.messages.set([]);
        this.storageError.set(false);
        this.reply(HELP);
        this.restorePending(accountId);
      });
    });
  }

  sendExample(message: string): void {
    this.message = message;
    this.send();
  }

  send(): void {
    const text = this.message.trim();
    if (!text || this.busy()) return;
    this.message = '';
    if (text.length > 500) {
      this.reply('Keep your message to 500 characters or fewer.');
      return;
    }
    this.append({ role: 'user', text });
    const command = parseChatbotCommand(text);
    switch (command.kind) {
      case 'confirm':
        this.confirmOrder();
        return;
      case 'cancel':
        this.cancel();
        return;
      case 'help':
        this.reply(HELP);
        return;
      case 'holdings':
        this.loadHoldings(command.symbol);
        return;
      case 'quantity':
        if (this.awaitingQuantity)
          this.prepareTrade({ ...this.awaitingQuantity, quantity: command.quantity });
        else
          this.reply(
            'Start with a side and ticker, for example “buy AAPL”, then tell me how many shares.',
          );
        return;
      case 'trade':
        this.prepareTrade(command);
        return;
      default:
        this.reply('I could not understand that instruction. ' + HELP);
    }
  }

  cancel(): void {
    if (this.busy()) return;
    if (this.pending()) {
      this.reply(
        'This order has already been submitted. Choose “Check previous order” to confirm its outcome. ' +
          'It cannot be cancelled from this chat.',
      );
      return;
    }
    this.preview.set(null);
    this.awaitingQuantity = null;
    this.reply('Trade draft cancelled. No order was submitted.');
  }

  private prepareTrade(command: TradeCommand): void {
    if (this.pending() || this.storageError()) {
      this.reply(
        'Resolve the previous order before starting another trade. You can still ask about your holdings.',
      );
      return;
    }
    this.preview.set(null);
    this.awaitingQuantity = command;
    if (command.quantity === null) {
      this.reply(
        `How many shares of ${command.symbol} would you like to ${command.side.toLowerCase()}?`,
      );
      return;
    }
    this.busy.set(true);
    this.activeRequest = this.market
      .getInstruments()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (instruments) => {
          const matches = instruments.filter(
            (instrument) =>
              instrument.tradable &&
              instrument.instrumentClass === 'EQUITY' &&
              instrument.symbol.toUpperCase() === command.symbol,
          );
          if (matches.length !== 1) {
            this.reply(
              matches.length
                ? 'That ticker identifies more than one stock. Use the market order ticket to select its exchange.'
                : `I could not find a tradable stock with ticker ${command.symbol}. Use an exact ticker from Market prices.`,
            );
            return;
          }
          const instrument = matches[0];
          const quote = instrument.latestQuote;
          const price = Number(command.side === 'BUY' ? quote?.askPrice : quote?.bidPrice);
          if (!quote || !Number.isFinite(price) || price <= 0 || !this.freshQuote(quote.quotedAt)) {
            this.reply(
              'A current price is unavailable for this stock. Please try your trade again after prices refresh.',
            );
            return;
          }
          this.awaitingQuantity = null;
          this.preview.set({
            accountId: this.account().accountId,
            request: {
              instrumentId: instrument.instrumentId,
              side: command.side,
              quantity: command.quantity!,
            },
            symbol: instrument.symbol,
            name: instrument.name,
            exchange: instrument.exchange,
            currency: instrument.currency,
            unitPrice: price,
            quotedAt: quote.quotedAt,
          });
          this.reply(
            `Review your ${command.side} order for ${command.quantity} shares of ${instrument.symbol} below. ` +
              'Choose “Confirm order” or type “confirm” to submit it.',
          );
        },
        error: (failure) => this.reply(this.readError(failure.status, 'load stock prices')),
      });
  }

  confirmOrder(): void {
    if (this.busy() || this.storageError()) return;
    let pending = this.pending();
    if (!pending) {
      const preview = this.preview();
      if (!preview) {
        this.reply('There is no trade to confirm. Start with, for example, “buy 5 AAPL”.');
        return;
      }
      if (!this.freshQuote(preview.quotedAt)) {
        this.reply(
          'That price preview expired. I will load a fresh preview for you to review and confirm again.',
        );
        this.prepareTrade({
          kind: 'trade',
          side: preview.request.side,
          symbol: preview.symbol,
          quantity: preview.request.quantity,
        });
        return;
      }
      try {
        pending = { ...preview, key: crypto.randomUUID() };
        // Save the immutable payload BEFORE sending. Retries and reloads must use this exact key.
        sessionStorage.setItem(this.storageKey(preview.accountId), JSON.stringify(pending));
      } catch {
        this.reply(
          'Your browser could not save this order request. Enable session storage before confirming a trade.',
        );
        return;
      }
      this.pending.set(pending);
      this.preview.set(null);
    }
    if (pending.accountId !== this.account().accountId) return;
    const request = pending;
    this.busy.set(true);
    this.activeRequest = this.orders
      .placeOrder({ ...request.request }, request.key)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (order) => this.receiveOrder(order, request),
        error: (failure) => {
          const order = failure.error as OrderResponse | undefined;
          if (
            failure.status === 400 &&
            order?.status === 'REJECTED' &&
            typeof order.id === 'number'
          ) {
            this.receiveOrder(order, request);
          } else {
            // A lost response, a 5xx, or an in-progress conflict does not prove that nothing executed.
            this.reply(
              failure.status === 401 || failure.status === 403
                ? 'Your session cannot place this order. Sign in to the same account, then check the previous order.'
                : 'The order outcome is not confirmed yet. Choose “Check previous order” to retry the same request safely. ' +
                    'You can also review your order history.',
            );
          }
        },
      });
  }

  private receiveOrder(order: OrderResponse, request: PendingTrade): void {
    if (
      typeof order?.id !== 'number' ||
      order.side !== request.request.side ||
      Number(order.quantity) !== request.request.quantity
    ) {
      this.reply(
        'The order result could not be verified. Check the previous order again or review your order history.',
      );
      return;
    }
    if (order.status === 'FILLED' || order.status === 'REJECTED') {
      try {
        sessionStorage.removeItem(this.storageKey(request.accountId));
      } catch {
        /* The response remains authoritative. */
      }
      this.pending.set(null);
      this.awaitingQuantity = null;
      if (order.status === 'FILLED') {
        const price =
          order.fillPrice !== null && Number.isFinite(Number(order.fillPrice))
            ? ` at ${this.number(Number(order.fillPrice))} ${order.fillCurrency} per share`
            : '';
        const settlement =
          order.settlementAmount !== null && Number.isFinite(Number(order.settlementAmount))
            ? ` Settlement: ${this.number(Math.abs(Number(order.settlementAmount)))} ${order.accountCurrency}.`
            : '';
        this.reply(
          `Order #${order.id} filled: ${order.side === 'BUY' ? 'bought' : 'sold'} ` +
            `${order.quantity} shares of ${request.symbol}${price}.${settlement}`,
        );
      } else
        this.reply(
          `Order #${order.id} was rejected. ${this.rejectionMessage(order.rejectionReason)}`,
        );
      this.orderChanged.emit(order);
    } else {
      this.reply(
        `Order #${order.id} is still processing. Choose “Check previous order” shortly to confirm its outcome.`,
      );
    }
  }

  private loadHoldings(symbol: string | null): void {
    this.busy.set(true);
    this.activeRequest = this.accounts
      .getHoldings()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (holdings) => {
          if (holdings.accountId !== this.account().accountId) {
            this.reply(
              'Your holdings could not be verified for this account. Please reload the dashboard.',
            );
            return;
          }
          const positions = holdings.positions.filter(
            (position) =>
              Number(position.quantity) > 0 &&
              (!symbol || position.symbol.toUpperCase() === symbol),
          );
          this.append({
            role: 'assistant',
            text: positions.length
              ? symbol
                ? `Here are your ${symbol} holdings.`
                : 'Here are your current holdings.'
              : symbol
                ? `You have no holdings in ${symbol}.`
                : 'You do not currently hold any instruments.',
            holdings: { ...holdings, positions },
            fetchedAt: new Date().toISOString(),
          });
        },
        error: (failure) => this.reply(this.readError(failure.status, 'load your holdings')),
      });
  }

  private restorePending(accountId: number): void {
    try {
      const stored = sessionStorage.getItem(this.storageKey(accountId));
      if (!stored) return;
      const value: PendingTrade = JSON.parse(stored);
      if (
        value.accountId !== accountId ||
        typeof value.key !== 'string' ||
        !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value.key) ||
        !['BUY', 'SELL'].includes(value.request?.side) ||
        !Number.isSafeInteger(value.request.instrumentId) ||
        value.request.instrumentId <= 0 ||
        typeof value.request.quantity !== 'number' ||
        stockQuantity(String(value.request.quantity)) === null ||
        typeof value.symbol !== 'string' ||
        typeof value.currency !== 'string' ||
        typeof value.name !== 'string' ||
        typeof value.exchange !== 'string' ||
        typeof value.quotedAt !== 'string' ||
        !Number.isFinite(value.unitPrice) ||
        value.unitPrice <= 0
      )
        throw new Error('Invalid pending trade');
      this.pending.set(value);
      this.isOpen.set(true);
      this.reply(
        'A previously submitted order still needs confirmation. Choose “Check previous order” to retrieve its outcome.',
      );
    } catch {
      this.storageError.set(true);
      this.isOpen.set(true);
      this.reply(
        'Your previous order request could not be read. Review your order history and restore browser session storage before trading here.',
      );
    }
  }

  private freshQuote(quotedAt: string): boolean {
    const age = Date.now() - Date.parse(quotedAt);
    return Number.isFinite(age) && age >= -5_000 && age <= 10_000;
  }
  private storageKey(accountId: number): string {
    return `leap_pending_chat_order_${accountId}`;
  }
  private number(value: number): string {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 6 }).format(value);
  }
  private readError(status: number, action: string): string {
    return status === 401 || status === 403
      ? 'Your session cannot access this account. Sign in again to continue.'
      : `I could not ${action}. Please ask again in a moment.`;
  }
  private rejectionMessage(reason: string | null): string {
    const message = reason?.toLowerCase() ?? '';
    if (message.includes('insufficient cash'))
      return 'You do not have enough available cash for this purchase.';
    if (message.includes('insufficient holding') || message.includes('insufficient position'))
      return 'You do not hold enough shares for this sale.';
    if (message.includes('fx') || message.includes('currency conversion'))
      return 'Currency conversion is temporarily unavailable.';
    if (message.includes('quote')) return 'A market price is currently unavailable.';
    return 'Review the ticker, quantity and available balance before trying again.';
  }
  private reply(text: string): void {
    this.append({ role: 'assistant', text });
  }
  private append(message: Omit<ChatMessage, 'id'>): void {
    this.messages.update((messages) => [
      ...messages.slice(-39),
      { ...message, id: ++this.messageId },
    ]);
  }
}
