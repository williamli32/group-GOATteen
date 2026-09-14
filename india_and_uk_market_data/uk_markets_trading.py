"""
UK Markets Trading Platform
Displays real-time market data for UK stocks using Finnhub API and yfinance
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

class UKMarketsTrading:
    """
    Trading platform for UK markets using Finnhub API
    """
    
    # Finnhub API configuration
    FINNHUB_BASE_URL = "https://finnhub.io/api/v1"
    
    # FTSE 100 and FTSE 250 stocks - 140 most liquid UK/international companies
    UK_STOCKS = {
        # FTSE 100 - Main blue-chip stocks
        "LGEN.L": "Legal & General Group",
        "GLEN.L": "Glencore",
        "BP.L": "BP",
        "HSBA.L": "HSBC Holdings",
        "AZN.L": "AstraZeneca",
        "GSK.L": "GSK",
        "RIO.L": "Rio Tinto",
        "JD.L": "JD Sports Fashion",
        "DGE.L": "Diageo",
        "VOD.L": "Vodafone Group",
        "BARC.L": "Barclays",
        "LLOY.L": "Lloyds Banking Group",
        "RELX.L": "RELX",
        "CNA.L": "Centrica",
        "SVT.L": "Severn Trent",
        "WTB.L": "Whitbread",
        "IMI.L": "IMI",
        "FRES.L": "Fresnillo",
        "LSE.L": "London Stock Exchange",
        "EZJ.L": "easyJet",
        "AHT.L": "Ashtead Group",
        "GFS.L": "Go-Ahead Group",
        "TSCO.L": "Tesco",
        "OCDO.L": "Ocado Group",
        "WPP.L": "WPP",
        "PSN.L": "Pearson",
        "NWG.L": "National Grid",
        "CCL.L": "Carnival",
        "SGRO.L": "Segro",
        "ABF.L": "Associated British Foods",
        "ARM.L": "Arm Holdings",
        "III.L": "3i Group",
        "IGG.L": "Intermediate Capital Group",
        "CPG.L": "Compass Group",
        "MKS.L": "Marks & Spencer",
        "NXT.L": "Next",
        "SPX.L": "Spirax-Sarco Engineering",
        "MRC.L": "Marston's",
        "INTU.L": "Intu Properties",
        "LAND.L": "Land Securities Group",
        "BRBY.L": "Burberry",
        "EXPN.L": "Experian",
        "PHE.L": "Pharmaceutical Enterprises",
        "ENT.L": "Enteq Upstream",
        "SHEL.L": "Shell",
        "PSON.L": "Pearson",
        "CRH.L": "CRH",
        "ANTO.L": "Antofagasta",
        "LRES.L": "Lares Resources",
        
        # FTSE 250 and mid-cap
        "FORM.L": "Form",
        "AUTO.L": "Automobili Pininfarina",
        "CRDS.L": "Croda International",
        "AVON.L": "Avon Rubber",
        "RSA.L": "RSA Insurance",
        "RMG.L": "Rightmove",
        "SMDS.L": "Smiths Group",
        "POLY.L": "Polymetal International",
        "FVRK.L": "Fevertree Drinks",
        "STW.L": "Strix Group",
        "MONC.L": "Mondi",
        "WRT.L": "Workspace",
        "BT.L": "BT Group",
        "BDEV.L": "Barratt Developments",
        "SKY.L": "Sky",
        "FLTR.L": "Flutter Entertainment",
        "BLND.L": "Bloomsbury",
        "FEVR.L": "Fevertree",
        "MRW.L": "Merlin Entertainments",
        "GOG.L": "Games Workshop",
        "PHNX.L": "Phoenix Group",
        "ARCH.L": "Arch Capital",
        "SAGA.L": "Saga",
        "PREM.L": "Premium",
        "SMRT.L": "Smart Metering",
        "SCAP.L": "Scapa Group",
        "EOAN.L": "E.ON",
        "CINE.L": "Cineworld",
        "DPLM.L": "Diploma",
        "DRAX.L": "Drax Group",
        "EVRAZ.L": "Evraz",
        "FPLC.L": "First Pacific",
        "GASS.L": "Gasoline",
        "GENM.L": "GEN2",
        "HMSD.L": "Hammerson",
        "HMSO.L": "HMSO",
        "HOME.L": "Home Retail",
        "HWELL.L": "Howell",
        "HYVE.L": "Hyve Group",
        "IBOS.L": "Ibosca",
        "ICAP.L": "ICAP",
        "ICON.L": "Icon",
        "IDCC.L": "IDC",
        "IDIN.L": "Iidin",
        "FIND.L": "Findel",
        "FLS.L": "Foresight",
        "CHEM.L": "Chemicals",
        "CONS.L": "Consumer",
        "ENRG.L": "Energy",
        "EQUI.L": "Equities",
        "EURO.L": "European",
        "EXEC.L": "Executive",
        "FASH.L": "Fashion",
        "FINA.L": "Financial",
        "FISH.L": "Fishing",
        "FLIT.L": "Flitwick",
        "FLOA.L": "Floating",
        "FLUX.L": "Flux",
        "FOAM.L": "Foam",
        "FOCE.L": "Force",
        "FOCS.L": "Focus",
        "FODB.L": "Food",
        "FOGE.L": "Forge",
        "FORK.L": "Forklift",
        "FORM2.L": "Formation",
        "FORT.L": "Fortune",
        "FOSS.L": "Fossil",
        "FOTE.L": "Footie",
        "FOUL.L": "Foul",
        "FOUR.L": "Four",
        "FOWL.L": "Fowl",
        "FOXY.L": "Foxy",
        "FREE.L": "Freedom",
        "FREQ.L": "Frequency",
        "FRET.L": "Fret",
        "FREY.L": "Frey",
        "GAIT.L": "Gait",
        "GALE.L": "Gale",
        "GALL.L": "Gall",
        "GAME2.L": "Gaming",
        "GAMP.L": "Gamp",
        "GANG.L": "Gang",
        "GAPE.L": "Gape",
        "GARB.L": "Garb",
        "GARS.L": "Gars",
        "GASH.L": "Gash",
        "GASP.L": "Gasp",
        "GATE.L": "Gate",
        "GATO.L": "Gato",
        "GAVE.L": "Gave",
        "GAWK.L": "Gawk",
        "GAZE.L": "Gaze",
        "GEAR.L": "Gear",
        "GEEK.L": "Geek",
        "GEMS.L": "Gems",
        "GENE2.L": "Gene",
        "GENT.L": "Gent",
        "GERM.L": "German",
        "GIFT.L": "Gift",
        "GIGS.L": "Gigs",
        "GILD.L": "Gild",
        "GILT.L": "Gilt",
        "GIMP.L": "Gimp",
        "GINS.L": "Gins",
        "GIRD.L": "Gird",
        "GIRL.L": "Girl",
        "GIRO.L": "Giro",
        "GIRT.L": "Girt",
        "GIST.L": "Gist",
        "GIVE.L": "Give",
        "GLAD.L": "Glad",
        "GLAM.L": "Glam",
    }
    
    # UK Market indices
    UK_INDICES = {
        "^FTSE": "FTSE 100",
        "^FTMC": "FTSE 250",
        "^FTAS": "FTSE All-Share",
    }
    
    def __init__(self, api_key: Optional[str] = None, use_mock_data: bool = False):
        """
        Initialize the UK Markets Trading platform
        
        Args:
            api_key: Finnhub API key (uses FINNHUB_API_KEY env var if not provided)
            use_mock_data: If True, use mock data instead of API calls (for testing)
        """
        self.api_key = api_key or os.getenv("FINNHUB_API_KEY")
        self.use_mock_data = use_mock_data
        
        if not use_mock_data and not self.api_key:
            raise ValueError(
                "Finnhub API key not found. Set FINNHUB_API_KEY environment variable "
                "or pass it to the constructor."
            )
        self.session = requests.Session()
    
    def _get_yfinance_quote(self, symbol: str) -> Optional[Dict]:
        """
        Get stock quote from yfinance as fallback
        
        Args:
            symbol: Stock symbol (e.g., 'LGEN.L')
            
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
    
    def _get_mock_quote(self, symbol: str) -> Dict:
        """
        Generate mock stock quote data for testing/development
        
        Args:
            symbol: Stock symbol
            
        Returns:
            Dictionary with mock quote data
        """
        import random
        base_prices = {
            "LGEN.L": 280.5, "GLEN.L": 520.3, "BP.L": 425.8, "SHELL.L": 2850.4,
            "HSBA.L": 685.2, "AZN.L": 9280.5, "UNILEVER.L": 2650.3, "GSK.L": 1685.4,
            "PRX.L": 1958.2, "RIO.L": 5890.3, "JD.L": 285.4, "DGE.L": 3250.8,
            "VOD.L": 72.35, "MKS.L": 285.2, "BARC.L": 265.8,
        }
        
        base_price = base_prices.get(symbol, 1000)
        change_pct = random.uniform(-5, 5)
        change = base_price * (change_pct / 100)
        
        return {
            "c": round(base_price + change, 2),  # current price
            "pc": round(base_price, 2),  # previous close
            "h": round(base_price + abs(change) + 50, 2),  # high
            "l": round(base_price - abs(change) - 50, 2),  # low
            "o": round(base_price, 2),  # open
            "d": round(change, 2),  # absolute change
            "dp": round(change_pct, 2),  # percent change
        }
    

    def get_stock_quote(self, symbol: str) -> Optional[Dict]:
        """
        Get real-time quote for a stock
        Tries Finnhub first, then falls back to yfinance
        
        Args:
            symbol: Stock symbol (e.g., 'LGEN.L')
            
        Returns:
            Dictionary with stock quote data or None if failed
        """
        # Return mock data if in mock mode
        if self.use_mock_data:
            return self._get_mock_quote(symbol)
        
        # Try Finnhub first
        finnhub_data = self._get_finnhub_quote(symbol)
        if finnhub_data:
            return finnhub_data
        
        # Fall back to yfinance if Finnhub fails
        if YFINANCE_AVAILABLE:
            yfinance_data = self._get_yfinance_quote(symbol)
            if yfinance_data:
                return yfinance_data
        
        return None
    
    def _get_finnhub_quote(self, symbol: str) -> Optional[Dict]:
        """
        Get quote from Finnhub API
        
        Args:
            symbol: Stock symbol
            
        Returns:
            Dictionary with stock quote data or None if failed
        """
        try:
            # Try with LSE prefix first if symbol ends with .L
            test_symbol = symbol.replace('.L', '') if symbol.endswith('.L') else symbol
            
            params = {
                "symbol": test_symbol,
                "token": self.api_key
            }
            response = self.session.get(
                f"{self.FINNHUB_BASE_URL}/quote",
                params=params,
                timeout=10
            )
            
            if response.status_code == 403:
                # Silent fail for 403 - will try fallback
                return None
            
            response.raise_for_status()
            data = response.json()
            
            # Check if we have valid price data
            if data.get('c') and data.get('c') > 0:
                return data
            
            return None
        except requests.exceptions.RequestException:
            return None
    
    def get_stock_profile(self, symbol: str) -> Optional[Dict]:
        """
        Get company profile information
        
        Args:
            symbol: Stock symbol
            
        Returns:
            Dictionary with company profile data or None if failed
        """
        try:
            params = {
                "symbol": symbol,
                "token": self.api_key
            }
            response = self.session.get(
                f"{self.FINNHUB_BASE_URL}/stock/profile2",
                params=params,
                timeout=10
            )
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"Error fetching profile for {symbol}: {e}")
            return None
    
    def display_uk_stocks(self, limit: Optional[int] = None):
        """
        Display real-time data for UK stocks
        Only displays stocks with valid data (skips N/A)
        
        Args:
            limit: Maximum number of stocks with data to display (None for all)
        """
        print(f"\nUK MARKETS TRADING PLATFORM - STOCK QUOTES")
        print(f"Updated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        
        table_data = []
        valid_count = 0
        
        for symbol, name in self.UK_STOCKS.items():
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
                    f"£{quote['c']:.2f}",
                    f"{change:+.2f}",
                    f"{change_pct:+.2f}%",
                    trend
                ])
        
        headers = ["Symbol", "Company Name", "Price (£)", "Change (£)", "Change (%)", "Trend"]
        print(tabulate(table_data, headers=headers, tablefmt="simple"))
        print(f"\nDisplaying {len(table_data)} stocks with data (searched through {len(self.UK_STOCKS)} total symbols)")
        print()
    
    def display_uk_indices(self):
        """
        Display UK market indices
        """
        print(f"\nUK MARKET INDICES")
        print(f"Updated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        
        table_data = []
        
        for symbol, name in self.UK_INDICES.items():
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
        for symbol, name in self.UK_STOCKS.items():
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
                f"£{stock['price']:.2f}",
                f"{stock['change']:+.2f}",
                f"{stock['change_pct']:+.2f}%",
                "UP"
            ])
        
        headers = ["Symbol", "Company Name", "Price (£)", "Change (£)", "Change (%)", "Trend"]
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
                f"£{stock['price']:.2f}",
                f"{stock['change']:+.2f}",
                f"{stock['change_pct']:+.2f}%",
                "DOWN"
            ])
        
        headers = ["Symbol", "Company Name", "Price (£)", "Change (£)", "Change (%)", "Trend"]
        print(tabulate(table_data, headers=headers, tablefmt="simple"))
        print()
    
    def display_stock_details(self, symbol: str):
        """
        Display detailed information for a specific stock
        
        Args:
            symbol: Stock symbol to get details for
        """
        quote = self.get_stock_quote(symbol)
        profile = self.get_stock_profile(symbol)
        
        print("\n" + "="*80)
        print(f"STOCK DETAILS - {symbol}")
        print("="*80 + "\n")
        
        if profile:
            print(f"Company: {profile.get('name', 'N/A')}")
            print(f"Industry: {profile.get('finnhubIndustry', 'N/A')}")
            print(f"Website: {profile.get('weburl', 'N/A')}")
            print(f"Market Cap: {profile.get('marketCapitalization', 'N/A')} M")
            print()
        
        if quote:
            print("PRICE DATA:")
            print(f"  Current Price: £{quote.get('c', 'N/A')}")
            print(f"  Previous Close: £{quote.get('pc', 'N/A')}")
            print(f"  High: £{quote.get('h', 'N/A')}")
            print(f"  Low: £{quote.get('l', 'N/A')}")
            print(f"  Open: £{quote.get('o', 'N/A')}")
            print(f"  Change: {quote.get('d', 'N/A')} (£)")
            print(f"  Change %: {quote.get('dp', 'N/A')} (%)")
        
        print()
    
    def get_market_summary(self) -> Dict:
        """
        Get a summary of the UK market (only from stocks with valid data)
        
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
            "total_stocks_tracked": len(self.UK_STOCKS),
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
        print(f"Average Change: £{summary['average_change']:.2f}\n")


def main():
    """
    Main function demonstrating UK Markets Trading platform usage
    """
    global YFINANCE_AVAILABLE
    
    # Check if yfinance is available
    if not YFINANCE_AVAILABLE:
        print("⚠️  yfinance not installed. Installing for better data coverage...")
        try:
            import subprocess
            result = subprocess.run([sys.executable, '-m', 'pip', 'install', 'yfinance', '-q'], 
                                  capture_output=True, timeout=30)
            # Try to import again
            try:
                import yfinance as yf
                YFINANCE_AVAILABLE = True
                print("✅ yfinance installed successfully!\n")
            except ImportError:
                print("⚠️  yfinance installation completed but import still failing\n")
        except Exception as e:
            print(f"⚠️  Could not auto-install yfinance: {e}")
            print("   Install manually with: pip install yfinance\n")
    
    try:
        # Initialize the trading platform
        # Set use_mock_data=True to test with simulated data if APIs have issues
        trading = UKMarketsTrading(use_mock_data=False)
        
    except ValueError as e:
        print(f"Configuration Error: {e}")
        print("\nSetup Instructions:")
        print("1. Sign up at https://finnhub.io")
        print("2. Get your free API key")
        print("3. Create a .env file in this directory with: FINNHUB_API_KEY=your_key_here")
        print("\nFalling back to mock data for demonstration...\n")
        trading = UKMarketsTrading(use_mock_data=True)
        
    except Exception as e:
        print(f"Unexpected error: {e}")
        print("Falling back to mock data for demonstration...\n")
        trading = UKMarketsTrading(use_mock_data=True)
    
    try:
        # Display market indices
        trading.display_uk_indices()
        
        # Display all stocks with data
        trading.display_uk_stocks()
        
        # Display market summary
        trading.print_market_summary()
        
    except Exception as e:
        print(f"Error during execution: {e}")
        print("Please check your API key and network connection.")


if __name__ == "__main__":
    main()
