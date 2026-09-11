"""
Live Market Data POC for GOATeen Trading Platform
Fetches comprehensive market data using Finnhub /quote API
Provides real-time quotes, market analysis, and trading signals
"""

import requests
import json
import csv
from datetime import datetime
from typing import List, Dict, Any, Optional
import time
from dotenv import load_dotenv
import os


class FinnhubMarketFetcher:
    """Fetches comprehensive market data from Finnhub API"""

    QUOTE_URL = "https://finnhub.io/api/v1/quote"
    SYMBOLS_URL = "https://finnhub.io/api/v1/stock/symbol"

    def __init__(self, api_key: str):
        """
        Initialize fetcher with Finnhub API key
        Get free API key at: https://finnhub.io/
        """
        self.api_key = api_key
        self.quotes: Dict[str, Dict[str, Any]] = {}
        self.rate_limit_delay = 1.0  # Finnhub free tier: 60 requests/minute = 1 request per second

    def fetch_quote(self, symbol: str) -> Optional[Dict[str, Any]]:
        """Fetch live real-time quote for a single symbol using Finnhub /quote endpoint"""
        try:
            params = {
                'symbol': symbol,
                'token': self.api_key,
            }

            response = requests.get(self.QUOTE_URL, params=params)
            data = response.json()

            # Check for error responses
            if 'error' in data:
                print(f"❌ {symbol:6} | Error: {data['error']}")
                time.sleep(self.rate_limit_delay)
                return None

            # Finnhub /quote returns: c (current), h (high), l (low), o (open), pc (previous close), t (timestamp)
            if 'c' not in data or data.get('c') == 0:
                print(f"⚠️  {symbol:6} | No data available")
                time.sleep(self.rate_limit_delay)
                return None

            current_price = float(data['c'])
            previous_close = float(data.get('pc', current_price))
            change = current_price - previous_close
            change_pct = (change / previous_close * 100) if previous_close > 0 else 0

            parsed_quote = {
                'symbol': symbol,
                'price': round(current_price, 2),
                'change': round(change, 2),
                'changePercent': round(change_pct, 2),
                'open': round(float(data.get('o', 0)), 2),
                'high': round(float(data.get('h', 0)), 2),
                'low': round(float(data.get('l', 0)), 2),
                'previousClose': round(previous_close, 2),
                'timestamp': datetime.fromtimestamp(data.get('t', 0)).isoformat() if data.get('t') else 'N/A',
                'volume': data.get('v', 0),
            }

            self.quotes[symbol] = parsed_quote
            time.sleep(self.rate_limit_delay)
            return parsed_quote

        except Exception as e:
            print(f"❌ {symbol:6} | Error: {str(e)}")
            return None

    def fetch_all_us_symbols(self) -> List[str]:
        """Fetch all US stock symbols from Finnhub"""
        print("📡 Fetching all US stock symbols from Finnhub...")
        symbols = []
        
        try:
            params = {
                'exchange': 'US',
                'token': self.api_key,
            }
            
            response = requests.get(self.SYMBOLS_URL, params=params)
            data = response.json()
            
            if isinstance(data, list):
                # Filter for stocks only (exclude ETFs, mutual funds, etc.)
                for item in data:
                    if item.get('type') == 'Common Stock':
                        symbol = item.get('symbol')
                        if symbol:
                            symbols.append(symbol)
            
            print(f"✅ Found {len(symbols)} US stocks")
            return sorted(symbols)
        
        except Exception as e:
            print(f"❌ Error fetching symbols: {str(e)}")
            return []

    def fetch_market_data(self, symbols: List[str]) -> Dict[str, Dict[str, Any]]:
        """Fetch quotes for multiple symbols"""
        print(f"\n📊 Fetching LIVE real-time market data for {len(symbols)} stocks...\n")
        print("Note: Finnhub free tier: 60 requests/minute\n")

        for symbol in symbols:
            quote = self.fetch_quote(symbol)
            if quote:
                change_arrow = '📈' if quote['change'] >= 0 else '📉'
                print(f"{change_arrow} {symbol:6} | ${quote['price']:8.2f} | "
                      f"Change: {quote['change']:+7.2f} ({quote['changePercent']:+6.2f}%) | "
                      f"Vol: {quote['volume']:>12,}")
            else:
                print(f"⏳ {symbol:6} | Waiting (rate limit)...")

        return self.quotes

    def get_top_performers(self, limit: int = 5) -> tuple:
        """Get top gainers and losers"""
        if not self.quotes:
            return [], []

        sorted_quotes = sorted(
            self.quotes.values(),
            key=lambda x: x['changePercent'],
            reverse=True
        )

        gainers = sorted_quotes[:limit]
        losers = sorted_quotes[-limit:]

        return gainers, losers

    def get_sector_analysis(self, sector_symbols: Dict[str, List[str]]) -> Dict[str, Dict[str, Any]]:
        """Analyze performance by sector"""
        sector_stats: Dict[str, Dict[str, Any]] = {}

        for sector, symbols in sector_symbols.items():
            sector_quotes = [self.quotes[s] for s in symbols if s in self.quotes]

            if sector_quotes:
                avg_change = sum(q['change'] for q in sector_quotes) / len(sector_quotes)
                avg_change_pct = sum(q['changePercent'] for q in sector_quotes) / len(sector_quotes)
                total_volume = sum(q['volume'] for q in sector_quotes)

                sector_stats[sector] = {
                    'avgChange': round(avg_change, 2),
                    'avgChangePercent': round(avg_change_pct, 2),
                    'totalVolume': total_volume,
                    'symbolCount': len(sector_quotes),
                }

        return sector_stats

    def print_market_summary(self) -> None:
        """Print comprehensive market summary"""
        if not self.quotes:
            print("No quotes available")
            return

        gainers, losers = self.get_top_performers(5)

        print("\n\n" + "=" * 90)
        print("📈 TOP GAINERS:")
        print("=" * 90)
        for i, quote in enumerate(gainers, 1):
            print(f"{i}. {quote['symbol']:6} | ${quote['price']:8.2f} | "
                  f"+{quote['changePercent']:.2f}% | Vol: {quote['volume']:,}")

        print("\n" + "=" * 90)
        print("📉 TOP LOSERS:")
        print("=" * 90)
        for i, quote in enumerate(reversed(losers), 1):
            print(f"{i}. {quote['symbol']:6} | ${quote['price']:8.2f} | "
                  f"{quote['changePercent']:.2f}% | Vol: {quote['volume']:,}")

        # Market breadth
        gainers_count = len([q for q in self.quotes.values() if q['change'] > 0])
        losers_count = len([q for q in self.quotes.values() if q['change'] < 0])
        unchanged_count = len([q for q in self.quotes.values() if q['change'] == 0])

        print("\n" + "=" * 90)
        print("📊 MARKET BREADTH:")
        print("=" * 90)
        print(f"Gainers:   {gainers_count:3} | Losers: {losers_count:3} | Unchanged: {unchanged_count:3}")

        total_volume = sum(q['volume'] for q in self.quotes.values())
        print(f"Total Volume: {total_volume:,}")
        print("=" * 90)

    def print_sector_summary(self, sector_symbols: Dict[str, List[str]]) -> None:
        """Print sector analysis"""
        sector_stats = self.get_sector_analysis(sector_symbols)

        print("\n" + "=" * 90)
        print("🏢 SECTOR ANALYSIS:")
        print("=" * 90)

        for sector, stats in sorted(sector_stats.items(), 
                                   key=lambda x: x[1]['avgChangePercent'],
                                   reverse=True):
            direction = "📈" if stats['avgChangePercent'] >= 0 else "📉"
            print(f"{direction} {sector:15} | Avg Change: {stats['avgChangePercent']:+6.2f}% | "
                  f"Symbols: {stats['symbolCount']:2} | Vol: {stats['totalVolume']:,}")
        print("=" * 90)

    def export_json(self, filename: str = 'market_data.json') -> None:
        """Export market data to JSON"""
        with open(filename, 'w') as f:
            json.dump(self.quotes, f, indent=2)
        print(f"\n✅ Market data exported to {filename}")

    def export_csv(self, filename: str = 'market_data.csv') -> None:
        """Export market data to CSV"""
        if not self.quotes:
            print("No quotes to export")
            return

        fieldnames = ['symbol', 'price', 'change', 'changePercent', 'volume', 
                      'timestamp', 'open', 'high', 'low', 'previousClose']

        with open(filename, 'w', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for symbol in sorted(self.quotes.keys()):
                quote = self.quotes[symbol]
                writer.writerow({k: quote.get(k) for k in fieldnames})

        print(f"✅ Market data exported to {filename}")


def get_api_key() -> str:
    """Get API key from .env file or environment variable"""
    # Load .env file from current directory
    load_dotenv()

    # Try to get from environment variable (set by .env file)
    api_key = os.getenv('FINNHUB_API_KEY')

    if api_key:
        return api_key
    
    raise ValueError("FINNHUB_API_KEY not found in .env file or environment variables")



def main():
    """Main POC execution"""
    print("🚀 GOATeen Trading Platform - Market Data POC")
    print("=" * 90)
    print(f"📡 Using Finnhub /quote endpoint (live real-time market data)")
    print("=" * 90)

    # Get API key
    api_key = get_api_key()

    # Create fetcher
    fetcher = FinnhubMarketFetcher(api_key)

    # Fetch all US stock symbols
    all_symbols = fetcher.fetch_all_us_symbols()
    
    if not all_symbols:
        print("❌ Failed to fetch US stock symbols")
        return

    # Optional: Limit number of stocks to fetch (useful for testing or API limits)
    all_symbols = all_symbols[:100]  # Fetch first 100 stocks
    
    print(f"\n⏱️  Estimated time: {len(all_symbols) * fetcher.rate_limit_delay / 60:.1f} minutes")
    print(f"📊 Fetching live quotes for {len(all_symbols)} stocks...")
    print("=" * 90 + "\n")

    # Fetch market data
    quotes = fetcher.fetch_market_data(all_symbols)

    if quotes:
        # Print market analysis
        fetcher.print_market_summary()
        fetcher.print_sector_summary({})

        # Export data
        fetcher.export_json('market_data.json')
        fetcher.export_csv('market_data.csv')
        print("\n✨ Market data POC completed successfully!")
    else:
        print("\n❌ Failed to fetch market data")
        print("\nPossible causes:")
        print("- API rate limit exceeded (free tier: 60 requests/minute)")
        print("- Invalid API key")
        print("- Network connection issue")
        print("\nTip: Get your own free API key at https://finnhub.io/")
        print("Then set FINNHUB_API_KEY environment variable or update .env file")


if __name__ == '__main__':
    main()



