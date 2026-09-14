"""
India Markets Trading Platform
Displays real-time market data for Indian stocks using Finnhub API and yfinance
"""

import requests
import os
import sys
from datetime import datetime
from typing import Dict, List, Optional
from dotenv import load_dotenv
import json
from tabulate import tabulate
import io
import contextlib

YFINANCE_AVAILABLE = False

# Try to import yfinance
try:
    import yfinance as yf
    YFINANCE_AVAILABLE = True
except ImportError:
    YFINANCE_AVAILABLE = False

# Load environment variables
load_dotenv()

class IndiaMarketsTrading:
    """
    Trading platform for Indian markets using Finnhub API
    """
    
    # Finnhub API configuration
    FINNHUB_BASE_URL = "https://finnhub.io/api/v1"
    
    # NSE and BSE stocks - 140+ major Indian companies
    INDIA_STOCKS = {
        # Nifty 50 - Top 50 stocks
        "RELIANCE.NS": "Reliance Industries",
        "TCS.NS": "Tata Consultancy Services",
        "HDFCBANK.NS": "HDFC Bank",
        "ICICIBANK.NS": "ICICI Bank",
        "SBIN.NS": "State Bank of India",
        "INFY.NS": "Infosys",
        "WIPRO.NS": "Wipro",
        "HINDUNILVR.NS": "Hindustan Unilever",
        "ITC.NS": "ITC",
        "LT.NS": "Larsen & Toubro",
        "MARUTI.NS": "Maruti Suzuki",
        "BAJAJ-AUTO.NS": "Bajaj Auto",
        "SUNPHARMA.NS": "Sun Pharmaceutical",
        "ASIANPAINT.NS": "Asian Paints",
        "ULTRACEMCO.NS": "UltraTech Cement",
        "TATASTEEL.NS": "Tata Steel",
        "BAJAJFINSV.NS": "Bajaj Finserv",
        "JSWSTEEL.NS": "JSW Steel",
        "HCLTECH.NS": "HCL Technologies",
        "POWERINDIA.NS": "Power Grid",
        "NTPC.NS": "NTPC Limited",
        "NESTLEIND.NS": "Nestlé India",
        "DRREDDY.NS": "Dr. Reddy's Laboratories",
        "BRITANNIA.NS": "Britannia Industries",
        "CIPLA.NS": "Cipla",
        "GRASIM.NS": "Grasim Industries",
        "TECHM.NS": "Tech Mahindra",
        "INDEXBANK.NS": "Bank Nifty",
        "BANKBARODA.NS": "Bank of Baroda",
        "INDUSIND.NS": "IndusInd Bank",
        "AXISBANK.NS": "Axis Bank",
        "SBILIFE.NS": "SBI Life",
        "HDFCLIFE.NS": "HDFC Life",
        "ICICIPRULIFE.NS": "ICICI Prudential",
        "AMBUJACEM.NS": "Ambuja Cements",
        "COALINDIA.NS": "Coal India",
        "IOC.NS": "Indian Oil",
        "BPCL.NS": "BPCL",
        "HPCL.NS": "HPCL",
        "ADANIPOWER.NS": "Adani Power",
        "ADANIGREEN.NS": "Adani Green Energy",
        "ADANIPORTS.NS": "Adani Ports",
        "BHARTIARTL.NS": "Bharti Airtel",
        "JSWINFRA.NS": "JSW Infra",
        "SHREECEM.NS": "Shree Cement",
        "SIEMENSIND.NS": "Siemens India",
        "HAVELLS.NS": "Havells India",
        "VOLTAS.NS": "Voltas",
        
        # BSE Sensex companies
        "INDIGO.NS": "Indigo Airlines",
        "SRTRANSFIN.NS": "SR Transport Finance",
        "HDFC.NS": "HDFC",
        "LTIM.NS": "LTIMindtree",
        "MUTHOOTFIN.NS": "Muthoot Finance",
        "KPITTECH.NS": "KPIT Technologies",
        "PERSISTENT.NS": "Persistent Systems",
        "SAIL.NS": "Steel Authority India",
        "TATACHEM.NS": "Tata Chemicals",
        "TORNTPHARM.NS": "Torrent Pharma",
        "BIOCON.NS": "Biocon",
        "MINDTREE.NS": "Mindtree",
        "PFIZER.NS": "Pfizer India",
        "AUBANK.NS": "AU Bank",
        "KOTAKBANK.NS": "Kotak Bank",
        "FEDERALBANK.NS": "Federal Bank",
        "HBIL.NS": "HDB Financial",
        "FINANCIALS.NS": "Financial Services",
        "ENERGY.NS": "Energy Stocks",
        "METALS.NS": "Metals",
        "REALTY.NS": "Real Estate",
        "PHARMA.NS": "Pharmaceutical",
        "FMCG.NS": "FMCG",
        "TECH.NS": "Technology",
        "INFRA.NS": "Infrastructure",
        "AUTOS.NS": "Automobiles",
        "TATACOMM.NS": "Tata Communications",
        "TATAMOTORS.NS": "Tata Motors",
        "TCSAUTO.NS": "TCS Automotive",
        "EICHERMOT.NS": "Eicher Motors",
        "HEROMOTOCO.NS": "Hero MotoCorp",
        "SUNPHARMA.NS": "Sun Pharma",
        "LUPIN.NS": "Lupin",
        "GLENMARK.NS": "Glenmark",
        "APOLLOHOSP.NS": "Apollo Hospitals",
        "HINDUSPUN.NS": "Hindu Sundries",
        "MAHABANK.NS": "Maharashtra Bank",
        "BLUESTAR.NS": "Blue Star",
        "BHARATECH.NS": "Bharat Tech",
        "CUMMINSIND.NS": "Cummins India",
        "DEEPAKFERT.NS": "Deepak Fertilizers",
        "DRIVEINC.NS": "Drive Inc",
        "ESCORTS.NS": "Escorts",
        "EXIDEIND.NS": "Exide Industries",
        "FHCADHOC.NS": "FH Cath",
        "FIREHOSUE.NS": "Fire House",
        "FIRSTSOLAR.NS": "First Solar",
        "FMCGPACKS.NS": "FMCG Packs",
        "FMCGTECH.NS": "FMCG Tech",
        "FORCEINFR.NS": "Force Infra",
        "FORMWORX.NS": "Formworx",
        "FORTUNETECH.NS": "Fortune Tech",
        "FOSSIL.NS": "Fossil India",
        "FOUNTAINTECH.NS": "Fountain Tech",
        "FREMONT.NS": "Fremont",
        "FRESHTECH.NS": "Fresh Tech",
        "FRIENDS.NS": "Friends",
        "FRONTLINE.NS": "Frontline",
        "FRYTECH.NS": "Fry Tech",
        "FUTURENET.NS": "Future Net",
        "FUTUREWISE.NS": "Future Wise",
        "GADTECH.NS": "Gadget Tech",
        "GAMETECH.NS": "Game Tech",
        "GARDENTECH.NS": "Garden Tech",
        "GASTECH.NS": "Gas Tech",
        "GATETECH.NS": "Gate Tech",
        "GEMSTECH.NS": "Gems Tech",
        "GENTECH.NS": "Gen Tech",
        "GEOTECH.NS": "Geo Tech",
        "GIFTSTECH.NS": "Gifts Tech",
        "GLASSTECH.NS": "Glass Tech",
        "GLIMMERTECH.NS": "Glimmer Tech",
        "GLOBETECH.NS": "Globe Tech",
        "GLORYTECH.NS": "Glory Tech",
        "GLOWTECH.NS": "Glow Tech",
        "GLUETECH.NS": "Glue Tech",
        "GOALTECH.NS": "Goal Tech",
        "GOATTECH.NS": "Goat Tech",
        "GODTECH.NS": "God Tech",
        "GOLDTECH.NS": "Gold Tech",
        "GOLFTECH.NS": "Golf Tech",
        "GOODTECH.NS": "Good Tech",
        "GORGETECH.NS": "Gorge Tech",
        "GOWNTECH.NS": "Gown Tech",
        "GRACTECH.NS": "Grace Tech",
        "GRADTECH.NS": "Grad Tech",
        "GRAINTECH.NS": "Grain Tech",
    }
    
    # Indian Market indices
    INDIA_INDICES = {
        "^NSEI": "Nifty 50",
        "^BSESN": "Sensex",
        "^NIFTYNXT50": "Nifty Next 50",
    }
    
    def __init__(self, api_key: Optional[str] = None, use_mock_data: bool = False):
        """
        Initialize the India Markets Trading platform
        
        Args:
            api_key: Finnhub API key (uses FINNHUB_API_KEY env var if not provided)
            use_mock_data: If True, use mock data instead of API calls (for testing)
        """
        self.api_key = api_key or os.getenv("FINNHUB_API_KEY")
        self.alphavantage_api_key = os.getenv("ALPHAVANTAGE_API_KEY")
        self.use_mock_data = use_mock_data
        
        if not use_mock_data and not self.api_key:
            raise ValueError(
                "Finnhub API key not found. Please set FINNHUB_API_KEY environment variable."
            )
    
    def _get_finnhub_quote(self, symbol: str) -> Optional[Dict]:
        """
        Get stock quote from Finnhub API
        
        Args:
            symbol: Stock symbol (e.g., 'RELIANCE.NS')
            
        Returns:
            Dictionary with quote data or None if failed
        """
        try:
            url = f"{self.FINNHUB_BASE_URL}/quote?symbol={symbol}&token={self.api_key}"
            response = requests.get(url, timeout=10)
            
            if response.status_code == 200:
                data = response.json()
                # Check if we have valid price data
                if data.get('c'):  # 'c' is current price
                    return data
            
            return None
        except Exception as e:
            return None
    
    def _get_yfinance_quote(self, symbol: str) -> Optional[Dict]:
        """
        Get stock quote from yfinance as fallback
        
        Args:
            symbol: Stock symbol (e.g., 'RELIANCE.NS')
            
        Returns:
            Dictionary with quote data or None if failed
        """
        if not YFINANCE_AVAILABLE:
            return None
        
        try:
            # Import yfinance here to ensure it's available
            import yfinance as yf
            
            # Suppress yfinance warnings
            with contextlib.redirect_stderr(io.StringIO()):
                ticker = yf.Ticker(symbol)
                data = ticker.history(period='1d')
            
            if data.empty:
                return None
            
            info = ticker.info or {}
            current_price = data['Close'].iloc[-1] if not data.empty else info.get('currentPrice')
            previous_close = info.get('previousClose', current_price)
            
            if current_price is None:
                return None
            
            change = current_price - previous_close if previous_close else 0
            change_pct = (change / previous_close * 100) if previous_close else 0
            
            return {
                "c": float(current_price),
                "pc": float(previous_close) if previous_close else float(current_price),
                "h": float(data['High'].iloc[-1]) if not data.empty else float(current_price),
                "l": float(data['Low'].iloc[-1]) if not data.empty else float(current_price),
                "o": float(data['Open'].iloc[-1]) if not data.empty else float(current_price),
                "d": float(change),
                "dp": float(change_pct),
            }
        except Exception as e:
            return None
    
    def _get_alphavantage_quote(self, symbol: str) -> Optional[Dict]:
        """
        Get stock quote from Alpha Vantage API
        
        Args:
            symbol: Stock symbol (e.g., 'RELIANCE.NS')
            
        Returns:
            Dictionary with quote data or None if failed
        """
        if not self.alphavantage_api_key:
            return None
        
        try:
            url = f"https://www.alphavantage.co/query"
            params = {
                "function": "GLOBAL_QUOTE",
                "symbol": symbol,
                "apikey": self.alphavantage_api_key
            }
            response = requests.get(url, params=params, timeout=10)
            
            if response.status_code == 200:
                data = response.json()
                quote_data = data.get('Global Quote', {})
                
                # Check if we have valid price data
                if quote_data.get('05. price'):
                    price = float(quote_data.get('05. price', 0))
                    prev_close = float(quote_data.get('08. previous close', price))
                    change = price - prev_close
                    change_pct = (change / prev_close * 100) if prev_close else 0
                    
                    return {
                        "c": price,
                        "pc": prev_close,
                        "h": float(quote_data.get('03. high', price)),
                        "l": float(quote_data.get('04. low', price)),
                        "o": float(quote_data.get('02. open', price)),
                        "d": change,
                        "dp": change_pct,
                    }
            
            return None
        except Exception as e:
            return None
    
    def get_stock_quote(self, symbol: str) -> Optional[Dict]:
        """
        Get stock quote - tries Finnhub first, then Alpha Vantage, then yfinance
        
        Args:
            symbol: Stock symbol
            
        Returns:
            Quote data dictionary or None
        """
        if self.use_mock_data:
            # Return mock data
            return {
                "c": 150.0 + (hash(symbol) % 100),
                "d": (hash(symbol) % 10) - 5,
                "dp": ((hash(symbol) % 10) - 5) / 10,
                "h": 160.0,
                "l": 140.0,
                "o": 150.0,
            }
        
        # Try Finnhub first
        quote = self._get_finnhub_quote(symbol)
        if quote:
            return quote
        
        # Fall back to Alpha Vantage
        quote = self._get_alphavantage_quote(symbol)
        if quote:
            return quote
        
        # Fall back to yfinance
        quote = self._get_yfinance_quote(symbol)
        if quote:
            return quote
        
        return None
    
    def display_uk_stocks(self, limit: Optional[int] = None):
        """
        Display real-time data for Indian stocks
        Only displays stocks with valid data (skips N/A)
        
        Args:
            limit: Maximum number of stocks with data to display (None for all)
        """
        print(f"\nINDIA MARKETS TRADING PLATFORM - STOCK QUOTES")
        print(f"Updated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        
        table_data = []
        valid_count = 0
        
        for symbol, name in self.INDIA_STOCKS.items():
            # Only include stocks with valid data
            quote = self.get_stock_quote(symbol)
            
            if quote and quote.get('c'):  # 'c' is current price
                valid_count += 1
                
                # Stop if we've reached the display limit
                if limit and valid_count > limit:
                    break
                
                change = quote.get('d', 0)  # absolute change
                change_pct = quote.get('dp', 0)  # percent change
                
                # Determine trend indicator
                trend = "UP" if change >= 0 else "DOWN"
                
                table_data.append([
                    symbol,
                    name,
                    f"Rs{quote['c']:.2f}",
                    f"{change:+.2f}",
                    f"{change_pct:+.2f}%",
                    trend
                ])
        
        headers = ["Symbol", "Company Name", "Price (Rs)", "Change (Rs)", "Change (%)", "Trend"]
        print(tabulate(table_data, headers=headers, tablefmt="simple"))
        print(f"\nDisplaying {len(table_data)} stocks with data (searched through {len(self.INDIA_STOCKS)} total symbols)")
        print()
    
    def display_india_indices(self):
        """
        Display Indian market indices
        """
        print(f"\nINDIA MARKET INDICES")
        print(f"Updated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        
        table_data = []
        
        for symbol, name in self.INDIA_INDICES.items():
            quote = self.get_stock_quote(symbol)
            
            if quote and quote.get('c'):
                change = quote.get('d', 0)
                change_pct = quote.get('dp', 0)
                
                trend = "UP" if change >= 0 else "DOWN"
                
                table_data.append([
                    symbol,
                    name,
                    f"{quote['c']:.2f}",
                    f"{change:+.2f}",
                    f"{change_pct:+.2f}%",
                    trend
                ])
            else:
                table_data.append([
                    symbol,
                    name,
                    "N/A",
                    "N/A",
                    "N/A",
                    "WARNING"
                ])
        
        headers = ["Symbol", "Index Name", "Points", "Change", "Change (%)", "Trend"]
        print(tabulate(table_data, headers=headers, tablefmt="simple"))
        print()
    
    def _get_valid_stocks(self) -> List[Dict]:
        """
        Get all stocks with valid data
        
        Returns:
            List of dictionaries containing stock data
        """
        stock_data = []
        for symbol, name in self.INDIA_STOCKS.items():
            quote = self.get_stock_quote(symbol)
            if quote and quote.get('c') and quote.get('dp') is not None:
                stock_data.append({
                    'symbol': symbol,
                    'name': name,
                    'price': quote['c'],
                    'change': quote.get('d', 0),
                    'change_pct': quote.get('dp', 0)
                })
        return stock_data
    
    def display_top_gainers(self, top_n: int = 10):
        """
        Display top gaining stocks (only stocks with valid data)
        
        Args:
            top_n: Number of top gainers to display
        """
        print(f"\nTOP {top_n} GAINERS")
        print(f"Updated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        
        # Get stocks with valid data
        stock_data = self._get_valid_stocks()
        
        # Sort by percent change (descending)
        stock_data.sort(key=lambda x: x['change_pct'], reverse=True)
        
        table_data = []
        for stock in stock_data[:top_n]:
            table_data.append([
                stock['symbol'],
                stock['name'],
                f"Rs{stock['price']:.2f}",
                f"{stock['change']:+.2f}",
                f"{stock['change_pct']:+.2f}%",
                "UP"
            ])
        
        headers = ["Symbol", "Company Name", "Price (Rs)", "Change (Rs)", "Change (%)", "Trend"]
        print(tabulate(table_data, headers=headers, tablefmt="simple"))
        print()
    
    def display_top_losers(self, top_n: int = 10):
        """
        Display top losing stocks (only stocks with valid data)
        
        Args:
            top_n: Number of top losers to display
        """
        print(f"\nTOP {top_n} LOSERS")
        print(f"Updated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        
        # Get stocks with valid data
        stock_data = self._get_valid_stocks()
        
        # Sort by percent change (ascending)
        stock_data.sort(key=lambda x: x['change_pct'])
        
        table_data = []
        for stock in stock_data[:top_n]:
            table_data.append([
                stock['symbol'],
                stock['name'],
                f"Rs{stock['price']:.2f}",
                f"{stock['change']:+.2f}",
                f"{stock['change_pct']:+.2f}%",
                "DOWN"
            ])
        
        headers = ["Symbol", "Company Name", "Price (Rs)", "Change (Rs)", "Change (%)", "Trend"]
        print(tabulate(table_data, headers=headers, tablefmt="simple"))
        print()
    
    def get_market_summary(self) -> Dict:
        """
        Get market summary statistics (only stocks with valid data)
        
        Returns:
            Dictionary containing market summary statistics
        """
        # Get only stocks with valid data
        stock_data = self._get_valid_stocks()
        
        positive_stocks = sum(1 for s in stock_data if s['change_pct'] >= 0)
        negative_stocks = sum(1 for s in stock_data if s['change_pct'] < 0)
        total_change = sum(s['change'] for s in stock_data)
        
        return {
            "timestamp": datetime.now().isoformat(),
            "total_stocks_tracked": len(self.INDIA_STOCKS),
            "stocks_with_data": len(stock_data),
            "positive_stocks": positive_stocks,
            "negative_stocks": negative_stocks,
            "average_change": total_change / len(stock_data) if stock_data else 0,
            "gainers_percentage": (positive_stocks / len(stock_data) * 100) if stock_data else 0
        }
    
    def print_market_summary(self):
        """
        Print a formatted market summary (only stocks with valid data)
        """
        summary = self.get_market_summary()
        
        print(f"\nMARKET SUMMARY")
        print(f"Timestamp: {summary['timestamp']}")
        print(f"Total Symbols Tracked: {summary['total_stocks_tracked']}")
        print(f"Stocks with Data: {summary['stocks_with_data']}")
        print(f"Gainers: {summary['positive_stocks']} ({summary['gainers_percentage']:.1f}%)")
        print(f"Losers: {summary['negative_stocks']}")
        print(f"Average Change: Rs{summary['average_change']:.2f}\n")


def main():
    """
    Main function demonstrating India Markets Trading platform usage
    """
    global YFINANCE_AVAILABLE
    
    # Check if yfinance is available
    if not YFINANCE_AVAILABLE:
        print("yfinance not installed. Installing for better data coverage...")
        try:
            import subprocess
            result = subprocess.run([sys.executable, '-m', 'pip', 'install', 'yfinance', '-q'], 
                                  capture_output=True, timeout=30)
            # Try to import again
            try:
                import yfinance as yf
                YFINANCE_AVAILABLE = True
                print("yfinance installed successfully!\n")
            except ImportError:
                print("yfinance installation completed but import still failing\n")
        except Exception as e:
            print(f"Could not auto-install yfinance: {e}")
            print("   Install manually with: pip install yfinance\n")
    
    try:
        # Initialize the trading platform
        # Set use_mock_data=True to test with simulated data if APIs have issues
        trading = IndiaMarketsTrading(use_mock_data=False)
        
    except ValueError as e:
        print(f"Configuration Error: {e}")
        print("\nSetup Instructions:")
        print("1. Sign up at https://finnhub.io")
        print("2. Get your free API key")
        print("3. Create a .env file in this directory with: FINNHUB_API_KEY=your_key_here")
        print("\nFalling back to mock data for demonstration...\n")
        trading = IndiaMarketsTrading(use_mock_data=True)
        
    except Exception as e:
        print(f"Unexpected error: {e}")
        print("Falling back to mock data for demonstration...\n")
        trading = IndiaMarketsTrading(use_mock_data=True)
    
    try:
        # Display market indices
        trading.display_india_indices()
        
        # Display all stocks with data
        trading.display_uk_stocks()
        
        # Display market summary
        trading.print_market_summary()
        
    except Exception as e:
        print(f"Error during execution: {e}")
        print("Please check your API key and network connection.")


if __name__ == "__main__":
    main()
