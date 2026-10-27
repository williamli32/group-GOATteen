import type { OrderSide } from '../core/services/order';

export type ChatbotCommand =
  | { kind: 'trade'; side: OrderSide; symbol: string; quantity: number | null }
  | { kind: 'holdings'; symbol: string | null }
  | { kind: 'quantity'; quantity: number }
  | { kind: 'confirm' | 'cancel' | 'help' | 'unknown' };

/** Match the database's NUMERIC(18,6) without silently rounding the user's instruction. */
export function stockQuantity(text: string): number | null {
  const normalized = text.startsWith('.') ? `0${text}` : text;
  if (!/^\d{1,12}(?:\.\d{1,6})?$/.test(normalized)) return null;
  const value = Number(normalized);
  const [whole, fraction = ''] = normalized.split('.');
  return value > 0 && value.toFixed(6) === `${BigInt(whole)}.${fraction.padEnd(6, '0')}`
    ? value
    : null;
}

const SYMBOL = '\\$?([a-z][a-z0-9]*(?:[.-][a-z0-9]+)*)';
const QUANTITY = '(\\d+(?:\\.\\d+)?|\\.\\d+)';
const UNITS = '(?:shares?|stocks?|units?)';

/** Deliberately match whole commands: negated, conditional and compound trades are never executed. */
export function parseChatbotCommand(message: string): ChatbotCommand {
  const text = message
    .trim()
    .replace(/[?!]+$/, '')
    .replace(/\.$/, '')
    .trim();
  if (/^(?:yes(?: please)?|confirm(?: (?:order|buy|sell))?|go ahead)$/i.test(text))
    return { kind: 'confirm' };
  if (/^(?:cancel(?: (?:order|that))?|never mind|nevermind|no)$/i.test(text))
    return { kind: 'cancel' };
  if (/^(?:help|hi|hello|what can you do)$/i.test(text)) return { kind: 'help' };

  const quantity = stockQuantity(text);
  if (quantity !== null) return { kind: 'quantity', quantity };

  const trade = text.match(
    /^(?:please\s+)?(?:(?:can|could|would) you\s+|i (?:want|would like) to\s+)?(buy|sell)\s+(.+?)(?:\s+please)?$/i,
  );
  if (trade) {
    const body = trade[2];
    const quantityFirst = body.match(
      new RegExp(`^${QUANTITY}\\s+(?:${UNITS}\\s+)?(?:of\\s+)?${SYMBOL}$`, 'i'),
    );
    const symbolFirst = body.match(
      new RegExp(`^${SYMBOL}(?:\\s+${QUANTITY}(?:\\s+${UNITS})?)?$`, 'i'),
    );
    const symbol = quantityFirst?.[2] ?? symbolFirst?.[1];
    const amount = quantityFirst?.[1] ?? symbolFirst?.[2];
    if (symbol && symbol.length <= 30) {
      const parsed = amount === undefined ? null : stockQuantity(amount);
      if (amount === undefined || parsed !== null)
        return {
          kind: 'trade',
          side: trade[1].toUpperCase() as OrderSide,
          symbol: symbol.toUpperCase(),
          quantity: parsed,
        };
    }
    return { kind: 'unknown' };
  }

  const holdings = '(?:holdings?|positions?|portfolio)(?:\\s+(?:details|summary))?';
  const prefix = '(?:(?:please\\s+)?(?:show|list|give|tell)(?:\\s+me)?\\s+|what (?:are|is)\\s+)?';
  const allOrFiltered = text.match(
    new RegExp(`^${prefix}(?:my\\s+)?${holdings}(?:\\s+(?:for|of|in)\\s+${SYMBOL})?$`, 'i'),
  );
  if (allOrFiltered) return { kind: 'holdings', symbol: allOrFiltered[1]?.toUpperCase() ?? null };

  const symbolFirstHoldings = text.match(
    new RegExp(`^${prefix}(?:my\\s+)?${SYMBOL}\\s+${holdings}$`, 'i'),
  );
  const sharesOwned = text.match(
    new RegExp(`^how many\\s+${SYMBOL}(?:\\s+shares?)?\\s+do i (?:own|have|hold)$`, 'i'),
  );
  const owned = text.match(
    new RegExp(`^what do i (?:own|have|hold)\\s+(?:of\\s+|in\\s+)?${SYMBOL}$`, 'i'),
  );
  const symbol = symbolFirstHoldings?.[1] ?? sharesOwned?.[1] ?? owned?.[1];
  return symbol ? { kind: 'holdings', symbol: symbol.toUpperCase() } : { kind: 'unknown' };
}
