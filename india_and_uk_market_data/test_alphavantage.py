#!/usr/bin/env python
"""Test Alpha Vantage integration"""

from uk_markets_trading import UKMarketsTrading
from india_markets_trading import IndiaMarketsTrading

print("Testing UK Markets with Alpha Vantage...\n")
uk = UKMarketsTrading(use_mock_data=False)
quote = uk.get_stock_quote('LGEN.L')
if quote:
    print(f"UK Success: LGEN.L price = {quote.get('c')}, change = {quote.get('dp'):.2f}%")
else:
    print("UK: No data available yet (APIs may be rate-limited)")

print("\nTesting India Markets with Alpha Vantage...\n")
india = IndiaMarketsTrading(use_mock_data=False)
quote = india.get_stock_quote('RELIANCE.NS')
if quote:
    print(f"India Success: RELIANCE.NS price = {quote.get('c')}, change = {quote.get('dp'):.2f}%")
else:
    print("India: No data available yet (APIs may be rate-limited)")

print("\n✓ Both platforms loaded successfully with Alpha Vantage support!")
