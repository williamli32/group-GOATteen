import { parseChatbotCommand, stockQuantity } from './chatbot-command';

describe('chatbot commands', () => {
  it.each([
    ['buy 5 AAPL', 'BUY', 'AAPL', 5],
    ['Buy AAPL 5 shares', 'BUY', 'AAPL', 5],
    ['please sell 2 shares of msft', 'SELL', 'MSFT', 2],
    ['can you buy .5 $AAPL?', 'BUY', 'AAPL', 0.5],
    ['I would like to buy 1.25 VOD.L please', 'BUY', 'VOD.L', 1.25],
    ['sell MSFT', 'SELL', 'MSFT', null],
    ['buy $AAPL', 'BUY', 'AAPL', null],
  ])('recognizes %s without placing an order', (message, side, symbol, quantity) => {
    expect(parseChatbotCommand(String(message))).toEqual({ kind: 'trade', side, symbol, quantity });
  });

  it.each([
    ['show my holdings', null],
    ['What are my holdings?', null],
    ['my portfolio', null],
    ['give me my holding details', null],
    ['show my holdings for aapl', 'AAPL'],
    ['my AAPL holdings', 'AAPL'],
    ['how many MSFT shares do I own?', 'MSFT'],
    ['what do I hold in $AAPL?', 'AAPL'],
  ])('recognizes the holdings question %s', (message, symbol) => {
    expect(parseChatbotCommand(message!)).toEqual({ kind: 'holdings', symbol });
  });

  it.each([
    'do not buy 5 AAPL',
    "don't sell MSFT",
    'buy 5 AAPL and sell 2 MSFT',
    'if AAPL drops buy 5 AAPL',
    'buy AAPL for $100',
    'buy -2 AAPL',
    'buy 0 AAPL',
    'buy 1.1234567 AAPL',
    'buy 1e3 AAPL',
    'show another account holdings',
    "show Bob's holdings",
    'execute arbitrary SQL',
  ])('does not infer an action from %s', (message) => {
    expect(parseChatbotCommand(message)).toEqual({ kind: 'unknown' });
  });

  it('recognizes confirmation, cancellation, help and a quantity follow-up', () => {
    expect(parseChatbotCommand('confirm')).toEqual({ kind: 'confirm' });
    expect(parseChatbotCommand('cancel')).toEqual({ kind: 'cancel' });
    expect(parseChatbotCommand('help')).toEqual({ kind: 'help' });
    expect(parseChatbotCommand('5')).toEqual({ kind: 'quantity', quantity: 5 });
  });

  it.each([
    '0',
    '-1',
    'NaN',
    'Infinity',
    '1e6',
    '1.0000001',
    '1000000000000',
    '999999999999.999999',
  ])('rejects unrepresentable or invalid quantity %s', (amount) =>
    expect(stockQuantity(amount)).toBeNull(),
  );

  it.each(['0.000001', '.5', '1.25', '999999999999'])('accepts precise quantity %s', (amount) => {
    expect(stockQuantity(amount)).toBe(Number(amount));
  });
});
