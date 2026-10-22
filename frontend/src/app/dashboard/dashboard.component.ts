import { MarketMotionComponent } from '../shared/market-motion.component';
import { HoldingsAllocationComponent } from './holdings-allocation.component';
import { ResultPaginationComponent } from '../shared/result-pagination.component';
import { boundedPage, pageRows } from '../core/utils/reporting-workflow';
import { PriceAlertPopupListenerComponent } from './price-alert-popup-listener.component';
import { FxRateService, type FxQuote } from '../core/services/fx-rate';
import {

  Component,

  computed,

  ElementRef,

  OnInit,

  signal,

  OnDestroy

} from '@angular/core';



import {

  IdempotencyService

} from '../core/services/idempotency';



import {

  MarketDataService,

  MarketInstrumentResponse

} from '../core/services/market-data';



import {

  CommonModule

} from '@angular/common';





import { AccountFundingComponent } from './account-funding.component';
import { WatchlistComponent } from './watchlist.component';
import { DailyPriceChangeComponent } from './daily-price-change.component';





import {

  HttpErrorResponse

} from '@angular/common/http';



import {

  Router

} from '@angular/router';



import {

  finalize,

  forkJoin,
  Subscription

} from 'rxjs';



import {

  AccountResponse,

  AddFundsResponse,

  AccountService,

  HoldingsResponse,

  OrderResponse

} from '../core/services/account';



import {

  Auth

} from '../core/auth/auth';



import {

  FormsModule

} from '@angular/forms';



import {

  OrderService,

  OrderSide,

  PlaceOrderRequest

} from '../core/services/order';



import {

  NotificationService

} from '../core/services/notification';

type DashboardTab = 'overview' | 'watchlist' | 'orders' | 'holdings';
interface PortfolioValuePoint {
  timestamp: number;
  value: number;
}
type PortfolioHistoryRange = 5 | 30 | -1 | 365 | 0;

interface QuoteHistoryPoint {
  timestamp: number;
  price: number;
}





@Component({

  selector: 'app-dashboard',

  standalone: true,

  imports: [

    CommonModule,

    FormsModule,
    PriceAlertPopupListenerComponent,

    AccountFundingComponent,
    WatchlistComponent,
    DailyPriceChangeComponent, MarketMotionComponent, ResultPaginationComponent, HoldingsAllocationComponent

  ],

  templateUrl: './dashboard.component.html',

  styleUrl: './dashboard.component.scss'

})

export class DashboardComponent implements OnInit, OnDestroy {
  readonly marketClasses = [
    'ALL',
    'EQUITY',
    'FOREX',
    'CRYPTO',
    'COMMODITY'
  ] as const;
    readonly portfolioValueHistory = signal<PortfolioValuePoint[]>([]);
    readonly hoveredPortfolioPoint = signal<number | null>(null);
    readonly portfolioHoverPosition = signal<{ left: number; top: number } | null>(null);
    readonly selectedMarketHistory = signal<QuoteHistoryPoint[]>([]);
    readonly portfolioHistoryDays = signal<PortfolioHistoryRange>(5);
    readonly portfolioHistoryRequestFailed = signal(false);
    private portfolioHistoryRequest?: Subscription;

    readonly portfolioHistoryRanges: ReadonlyArray<{ days: PortfolioHistoryRange; label: string }> = [
      { days: 5, label: '5D' },
      { days: 30, label: '1M' },
      { days: -1, label: 'YTD' },
      { days: 365, label: '1Y' },
      { days: 0, label: 'ALL' }
    ];

    onPortfolioChartPointerMove(event: PointerEvent): void {
      const points = this.portfolioValueHistory();
      const chart = event.currentTarget;
      const panel = chart instanceof Element ? chart.closest<HTMLElement>('.portfolio-value-panel') : null;
      if (!points.length || !chart || !panel) return;

      const chartBounds = (chart as SVGSVGElement).getBoundingClientRect();
      const panelBounds = panel.getBoundingClientRect();
      const viewBoxX = (event.clientX - chartBounds.left) / Math.max(chartBounds.width, 1) * 872 - 72;
      const pointRatio = Math.min(1, Math.max(0, viewBoxX / 800));
      const nearestIndex = Math.round(pointRatio * Math.max(points.length - 1, 0));
      const pointX = this.portfolioValuePointX(nearestIndex);
      const pointY = this.portfolioValuePointY(nearestIndex);
      const pointScreenX = chartBounds.left + ((pointX + 72) / 872) * chartBounds.width;
      const pointScreenY = chartBounds.top + (pointY / 260) * chartBounds.height;
      if (Math.hypot(event.clientX - pointScreenX, event.clientY - pointScreenY) > 14) {
        this.clearPortfolioChartHover();
        return;
      }

      const tooltipWidth = 190;
      const tooltipHeight = 36;

      this.hoveredPortfolioPoint.set(nearestIndex);
      this.portfolioHoverPosition.set({
        left: Math.min(Math.max(event.clientX - panelBounds.left + 12, 8), Math.max(panelBounds.width - tooltipWidth - 8, 8)),
        top: Math.min(Math.max(event.clientY - panelBounds.top - tooltipHeight - 10, 8), Math.max(panelBounds.height - tooltipHeight - 8, 8))
      });
    }

    clearPortfolioChartHover(): void {
      this.hoveredPortfolioPoint.set(null);
      this.portfolioHoverPosition.set(null);
    }

    portfolioHistoryRangeLabel(): string {
      return this.portfolioHistoryRanges.find(range => range.days === this.portfolioHistoryDays())?.label ?? '5D';
    }

    selectPortfolioHistoryRange(days: PortfolioHistoryRange): void {
      if (this.portfolioHistoryDays() === days) return;
      this.portfolioHistoryDays.set(days);
      this.clearPortfolioChartHover();
      this.portfolioHistoryRequest?.unsubscribe();
      this.portfolioHistoryRequestFailed.set(false);
      this.portfolioHistoryRequest = this.accountService.getPortfolioHistory(days).subscribe({
        next: history => {
          this.portfolioValueHistory.set(history.map(point => ({
            timestamp: new Date(`${point.date}T12:00:00`).getTime(),
            value: Number(point.value)
          })));
        },
        error: () => this.portfolioHistoryRequestFailed.set(true)
      });
    }


  readonly dashboardTabs: ReadonlyArray<{ id: DashboardTab; label: string }> = [
    { id: 'overview', label: 'Account overview' },
    { id: 'watchlist', label: 'Watchlist' },
    { id: 'orders', label: 'Orders' },
    { id: 'holdings', label: 'Holdings' }
  ];

  readonly activeTab = signal<DashboardTab>('overview');

  selectTab(tab: DashboardTab): void {
    this.activeTab.set(tab);
    // Also move focus when a shortcut leaves a panel that is about to be hidden.
    this.host.nativeElement.querySelector<HTMLButtonElement>(`#dashboard-tab-${tab}`)?.focus();
  }

  quoteAgeSeconds(quotedAt: string | null | undefined): number | null {
    if (!quotedAt) {
      return null;
    }

    const quotedTime = new Date(quotedAt).getTime();

    if (!Number.isFinite(quotedTime)) {
      return null;
    }

    const ageMs = Math.max(0, this.quoteClock() - quotedTime);
    return Math.floor(ageMs / 1000);
  }

  displayedQuoteAgeSeconds(quotedAt: string | null | undefined): number | null {
    const ageSeconds = this.quoteAgeSeconds(quotedAt);
    return ageSeconds === null ? null : Math.max(0, ageSeconds - 3);
  }

  isQuoteStale(quotedAt: string | null | undefined): boolean {
    const ageSeconds = this.quoteAgeSeconds(quotedAt);

    return ageSeconds === null
      || ageSeconds * 1000 > this.quoteStaleAfterMs;
  }

  onTabKeydown(event: KeyboardEvent, index: number): void {
    let nextIndex: number;
    switch (event.key) {
      case 'ArrowRight': nextIndex = (index + 1) % this.dashboardTabs.length; break;
      case 'ArrowLeft': nextIndex = (index - 1 + this.dashboardTabs.length) % this.dashboardTabs.length; break;
      case 'Home': nextIndex = 0; break;
      case 'End': nextIndex = this.dashboardTabs.length - 1; break;
      default: return;
    }
    event.preventDefault();
    this.selectTab(this.dashboardTabs[nextIndex].id);
  }





  marketInstruments =

    signal<MarketInstrumentResponse[]>([]);


  readonly marketSearch = signal('');
  readonly marketClassFilter = signal('ALL');

  readonly filteredMarketInstruments = computed(() => {
    const search = this.marketSearch().trim().toLowerCase();
    const selectedClass = this.marketClassFilter();

    return this.marketInstruments().filter(instrument => {
      const matchesSearch =
        search === '' ||
        instrument.symbol.toLowerCase().includes(search) ||
        instrument.name.toLowerCase().includes(search);

      const matchesClass =
        selectedClass === 'ALL' ||
        instrument.instrumentClass === selectedClass;

      return matchesSearch && matchesClass;
    });
  });
  readonly marketPage = signal(1);
  readonly marketPageSize = signal(10);
  readonly currentMarketPage = computed(() => boundedPage(this.marketPage(), this.filteredMarketInstruments().length, this.marketPageSize()));
  readonly visibleMarketInstruments = computed(() => pageRows(this.filteredMarketInstruments(), this.currentMarketPage(), this.marketPageSize()));
  setMarketSearch(value: string): void { this.marketSearch.set(value); this.marketPage.set(1); }
  setMarketClass(value: string): void { this.marketClassFilter.set(value); this.marketPage.set(1); }
  setMarketPageSize(size: number): void { if ([10, 25, 50].includes(size)) { this.marketPageSize.set(size); this.marketPage.set(1); } }

  selectedInstrument =

    signal<MarketInstrumentResponse | null>(null);



  marketLoading =

    signal(true);


  readonly marketRefreshFailed = signal(false);
  readonly quoteClock = signal(Date.now());
  private quoteClockInterval: number | null = null;

  private readonly quoteStaleAfterMs = 10_000;
  marketErrorMessage =

    signal('');



  account =

    signal<AccountResponse | null>(null);



  holdings =

    signal<HoldingsResponse | null>(null);



  orders =

    signal<OrderResponse[]>([]);



  loading =

    signal(true);



  loggingOut =

    signal(false);



  errorMessage =

    signal('');



  readonly fxQuote = signal<FxQuote | null>(null);
  readonly fxLoading = signal(false);
  readonly fxErrorMessage = signal('');
  private fxRequest?: Subscription;
  private fxPair = '';
  private fxLoadedAt = 0;
  private fxRequestVersion = 0;
  private readonly fxRefreshAfterMs = 60_000;

  readonly orderOutcomeUnconfirmed = signal(false);
  private pendingOrderRequest: { request: PlaceOrderRequest; key: string } | null = null;

  orderControlsLocked(): boolean {
    return this.orderSubmitting() || this.orderOutcomeUnconfirmed();
  }

  orderSide: OrderSide =

    'BUY';



  orderQuantity:

    number | null = null;



  orderSubmitting =

    signal(false);



  orderMessage =

    signal('');



  orderErrorMessage =

    signal('');



  quantityError =

    signal('');



  private marketRefreshInterval: number | null = null;



  private marketRefreshInProgress = false;



  private dashboardRefreshInterval:

    number | null = null;



  private dashboardRefreshInProgress =

    false;

  private dashboardRequest?: Subscription;
  private dashboardRequestVersion = 0;

  onAccountFunded(result: AddFundsResponse): void {
    const account = this.account();
    if (!account || result.accountId !== account.accountId || result.currency !== account.currency) return;
    this.dashboardRequestVersion++;
    this.dashboardRequest?.unsubscribe();
    this.dashboardRefreshInProgress = false;
    this.account.set({ ...account, cashBalance: result.cashBalance });
    this.holdings.update(holdings => holdings ? { ...holdings, cashBalance: result.cashBalance } : holdings);
    this.loading.set(false);
    this.errorMessage.set('');
    this.loadDashboard(false);
  }



  constructor(
    private accountService: AccountService,

    private fxRateService: FxRateService,

    private marketDataService: MarketDataService,

    private orderService: OrderService,

    private auth: Auth,

    private router: Router,

    public notificationService: NotificationService,

    private idempotencyService: IdempotencyService,

    private readonly host: ElementRef<HTMLElement>

  ) { }

  portfolioValue(): number {
    const currentAccount = this.account();
    const currentHoldings = this.holdings();
    if (!currentAccount) return 0;

    const holdingsValue = (currentHoldings?.positions ?? []).reduce((total, position) => {
      const instrument = this.marketInstruments().find(
        item => item.instrumentId === position.instrumentId
      );
      return total + Number(position.quantity) * (instrument?.latestQuote?.lastPrice ?? 0);
    }, 0);

    return Number(currentAccount.cashBalance) + holdingsValue;
  }

  portfolioValuePoints(): string {
    const points = this.portfolioValueHistory();
    const maximum = Math.max(...points.map(point => point.value), 1);
    const minimum = Math.min(...points.map(point => point.value), 0);
    const range = Math.max(maximum - minimum, 1);

    return points.map((point, index) => {
      const x = points.length === 1 ? 400 : (index / (points.length - 1)) * 800;
      const y = 190 - ((point.value - minimum) / range) * 168;
      return `${x},${y}`;
    }).join(' ');
  }

  portfolioValueAxisLabels(): number[] {
    const values = this.portfolioValueHistory().map(point => point.value);
    const maximum = Math.max(...values, 1);
    const minimum = Math.min(...values, 0);
    const step = (maximum - minimum) / 4;
    return [maximum, maximum - step, maximum - step * 2, maximum - step * 3, minimum];
  }

  portfolioValuePointX(index: number): number {
    const points = this.portfolioValueHistory();
    return points.length === 1 ? 400 : (index / Math.max(points.length - 1, 1)) * 800;
  }

  portfolioValuePointY(index: number): number {
    const points = this.portfolioValueHistory();
    const maximum = Math.max(...points.map(point => point.value), 1);
    const minimum = Math.min(...points.map(point => point.value), 0);
    const range = Math.max(maximum - minimum, 1);
    return 190 - ((points[index]?.value ?? minimum) - minimum) / range * 168;
  }

  portfolioValueLabel(value: number): string {
    return new Intl.NumberFormat('en-GB', {
      style: 'currency',
      currency: this.account()?.currency ?? 'GBP',
      maximumFractionDigits: 2
    }).format(value);
  }

  portfolioPointLabel(timestamp: number): string {
    return new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit',
      minute: '2-digit'
    }).format(timestamp);
  }

  portfolioPointDateLabel(timestamp: number): string {
    return new Intl.DateTimeFormat('en-GB', {
      month: 'short',
      day: 'numeric'
    }).format(timestamp);
  }

  showPortfolioDateLabel(index: number): boolean {
    const count = this.portfolioValueHistory().length;
    const labelInterval = Math.max(1, Math.ceil((count - 1) / 7));
    return count <= 8 || index === 0 || index === count - 1 || index % labelInterval === 0;
  }

  private recordPortfolioValue(): void {
    if (!this.account() || !this.holdings() || !this.marketInstruments().length) return;
    const point = { timestamp: Date.now(), value: this.portfolioValue() };
    const cutoff = point.timestamp - 7 * 24 * 60 * 60 * 1000;
    this.portfolioValueHistory.update(history => [
      ...history.filter(existing => existing.timestamp >= cutoff),
      point
    ]);
  }





  ngOnInit(): void {

    this.quoteClock.set(Date.now());
    this.quoteClockInterval = window.setInterval(() => this.quoteClock.set(Date.now()), 1000);


    /*

     * Initial account/portfolio state.

     */

    this.loadDashboard();



    /*

     * Initial market state.

     */

    this.loadMarketData();





    /*

     * Quotes refresh independently.

     */

    this.marketRefreshInterval =

      window.setInterval(

        () => this.loadMarketData(false),

        3000

      );





    /*

     * Sprint 4:

     *

     * Continuously refresh account cash,

     * positions and the order blotter so that

     * execution changes appear without logout

     * or a manual browser refresh.

     */

    this.dashboardRefreshInterval =

      window.setInterval(

        () => this.loadDashboard(false),

        3000

      );



  }





  loadDashboard(

    showLoading: boolean = true

  ): void {



    /*

     * Prevent overlapping account refreshes.

     */

    if (this.dashboardRefreshInProgress) {

      return;

    }

    const requestVersion = ++this.dashboardRequestVersion;
    const requestedPortfolioHistoryDays = this.portfolioHistoryDays();





    this.dashboardRefreshInProgress =

      true;





    /*

     * Only show the large loading state during

     * the initial/manual dashboard load.

     *

     * Background polling must not make the

     * dashboard flash every three seconds.

     */

    if (showLoading) {



      this.loading.set(true);



      this.errorMessage.set('');



    }





    this.dashboardRequest = forkJoin({



      account:

        this.accountService.getMyAccount(),



      holdings:

        this.accountService.getHoldings(),

      portfolioHistory:

        this.accountService.getPortfolioHistory(requestedPortfolioHistoryDays),



      orders:

        this.accountService.getBlotter()



    })

      .pipe(



        finalize(() => {

          if (requestVersion !== this.dashboardRequestVersion) return;



          this.dashboardRefreshInProgress =

            false;





          if (showLoading) {



            this.loading.set(false);



          }



        })



      )

      .subscribe({



        next: result => {

          if (requestVersion !== this.dashboardRequestVersion) return;



          /*

           * All client-visible Sprint 4 state is

           * refreshed together.

           */

          this.account.set(

            result.account

          );
          this.refreshFxQuote();



          this.holdings.set(

            result.holdings

          );

          if (this.portfolioHistoryDays() === requestedPortfolioHistoryDays) {
            this.portfolioValueHistory.set(
              result.portfolioHistory.map(point => ({
                timestamp: new Date(`${point.date}T12:00:00`).getTime(),
                value: Number(point.value)
              }))
            );
            this.portfolioHistoryRequestFailed.set(false);
          }



          this.orders.set(

            result.orders

          );






          /*

           * A successful refresh means any previous

           * temporary dashboard error is no longer

           * relevant.

           */

          if (showLoading) {



            this.errorMessage.set('');



          }



        },





        error: (

          error: HttpErrorResponse

        ) => {

          if (requestVersion !== this.dashboardRequestVersion) return;



          if (error.status === 401) {



            this.auth.clearToken();



            this.router.navigate([

              '/login'

            ]);



            return;

          }





          /*

           * Background polling should retain the

           * last successful account state rather

           * than replacing the UI with an error.

           */

          if (showLoading) {



            this.errorMessage.set(

              'Unable to load your account dashboard. Please try again.'

            );



          }



        }



      });



  }





  logout(): void {



    if (this.loggingOut()) {

      return;

    }





    this.loggingOut.set(true);





    this.auth.logoutSession()

      .pipe(



        finalize(() => {



          this.loggingOut.set(false);



        })



      )

      .subscribe({



        next: () => {



          this.finishLogout();



        },





        error: () => {



          /*

           * Even if the server is temporarily unavailable,

           * remove the locally stored access token.

           */

          this.finishLogout();



        }



      });



  }





  private finishLogout(): void {



    this.auth.clearToken();



    this.router.navigate([

      '/login'

    ]);



  }



  loadMarketData(

    showLoading = true

  ): void {



    if (this.marketRefreshInProgress) {

      return;

    }



    this.marketRefreshInProgress = true;





    if (showLoading) {



      this.marketLoading.set(true);



      this.marketErrorMessage.set('');



    }





    this.marketDataService

      .getInstruments()

      .pipe(



        finalize(() => {



          this.marketRefreshInProgress = false;



          if (showLoading) {



            this.marketLoading.set(false);



          }



        })



      )

      .subscribe({



        next: instruments => {

          this.marketRefreshFailed.set(false);

          this.marketInstruments.set(

            instruments

          );






          const currentSelected =

            this.selectedInstrument();





          if (currentSelected) {



            const refreshedSelected =

              instruments.find(

                instrument =>

                  instrument.instrumentId ===

                  currentSelected.instrumentId

              );





            if (refreshedSelected) {



              this.selectedInstrument.set(

                refreshedSelected

              );
              this.refreshFxQuote();



            } else {



              this.selectedInstrument.set(

                this.orderControlsLocked()
                  ? { ...currentSelected, latestQuote: null, dayChange: null }
                  : instruments[0] ?? null

              );
              this.refreshFxQuote();



            }



          } else {



            this.selectedInstrument.set(

              instruments[0] ?? null

            );
            this.refreshFxQuote();


          const activeInstrument = this.selectedInstrument();
          const activeQuote = activeInstrument?.latestQuote;
          if (activeQuote) {
            const point = {
              timestamp: new Date(activeQuote.quotedAt).getTime(),
              price: activeQuote.lastPrice
            };
            this.selectedMarketHistory.update(history => [
              ...history.filter(existing => existing.timestamp !== point.timestamp),
              point
            ].slice(-60));
          }


          }





          /*

           * A successful refresh clears any previous

           * market-data error.

           */

          this.marketErrorMessage.set('');



        },





        error: (

          error: HttpErrorResponse

        ) => {



          if (error.status === 401) {



            this.auth.clearToken();



            this.router.navigate([

              '/login'

            ]);



            return;



          }



          this.marketRefreshFailed.set(true);


          /*

           * Only replace the visible market area with

           * an error during the initial/manual load.

           *

           * A failed background refresh leaves the most

           * recently loaded prices visible.

           */

          if (showLoading) {



            this.marketErrorMessage.set(

              'Unable to load market data.'

            );



          }



        }



      });



  }





  isQuantityValid(): boolean {

    const quantity = Number(this.orderQuantity);

    return Number.isFinite(quantity) && quantity > 0;

  }



  validateQuantity(): void {

    if (this.orderQuantity === null || this.orderQuantity === undefined) {

      this.quantityError.set('');

      return;

    }



    const quantity = Number(this.orderQuantity);



    if (!Number.isFinite(quantity) || quantity <= 0) {

      this.quantityError.set('Enter a valid quantity greater than 0.');

    } else {

      this.quantityError.set('');

    }

  }



  holdingInstrument(position: { instrumentId: number }): MarketInstrumentResponse | null {
    return this.marketInstruments().find(instrument => instrument.instrumentId === position.instrumentId) ?? null;
  }

  openHolding(position: { instrumentId: number }): void {
    const instrument = this.holdingInstrument(position);
    if (instrument) this.selectInstrument(instrument);
  }

  selectInstrument(

    instrument: MarketInstrumentResponse

  ): void {

    if (this.orderControlsLocked()) return;

    this.selectedInstrument.set(

      instrument

    );
    this.selectedMarketHistory.set([]);
    if (instrument.latestQuote) {
      this.selectedMarketHistory.set([{
        timestamp: new Date(instrument.latestQuote.quotedAt).getTime(),
        price: instrument.latestQuote.lastPrice
      }]);
    }
    this.refreshFxQuote();



    this.orderMessage.set('');



    this.orderErrorMessage.set('');



    this.quantityError.set('');

    this.selectTab('orders');

  }

  marketHistoryPoints(): string {
    const points = this.selectedMarketHistory();
    const values = points.map(point => point.price);
    const minimum = Math.min(...values, 0);
    const maximum = Math.max(...values, 1);
    const range = Math.max(maximum - minimum, 0.000001);

    return points.map((point, index) => {
      const x = points.length === 1 ? 400 : (index / (points.length - 1)) * 800;
      const y = 190 - ((point.price - minimum) / range) * 168;
      return `${x},${y}`;
    }).join(' ');
  }

  marketHistoryPointX(index: number): number {
    const points = this.selectedMarketHistory();
    return points.length === 1 ? 400 : (index / Math.max(points.length - 1, 1)) * 800;
  }

  marketHistoryPointY(index: number): number {
    const points = this.selectedMarketHistory();
    const values = points.map(point => point.price);
    const minimum = Math.min(...values, 0);
    const maximum = Math.max(...values, 1);
    const range = Math.max(maximum - minimum, 0.000001);
    return 190 - (((points[index]?.price ?? minimum) - minimum) / range) * 168;
  }

  marketPriceLabel(price: number): string {
    return new Intl.NumberFormat('en-GB', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 6
    }).format(price);
  }



  setOrderSide(

    side: OrderSide

  ): void {

    if (this.orderControlsLocked()) return;


    this.orderSide =

      side;



    this.orderMessage.set('');



    this.orderErrorMessage.set('');



  }





  currentOrderPrice():

    number | null {



    const quote =

      this.selectedInstrument()

        ?.latestQuote;



    if (!quote) {

      return null;

    }



    return this.orderSide === 'BUY'

      ? quote.askPrice

      : quote.bidPrice;



  }





  // Pre-trade estimate only. The server re-fetches FX at execution time.
  refreshFxQuote(force = false): void {
    const from = this.selectedInstrument()?.currency;
    const to = this.account()?.currency;
    if (!from || !to) {
      this.fxRequestVersion++;
      this.fxRequest?.unsubscribe();
      this.fxPair = '';
      this.fxQuote.set(null);
      this.fxLoading.set(false);
      this.fxErrorMessage.set('');
      return;
    }
    const pair = `${from}/${to}`;
    if (pair === this.fxPair) {
      if (this.fxLoading()) return;
      // Cache successful estimates only. A failed request may retry on the next poll.
      if (!force && this.fxQuote() && Date.now() - this.fxLoadedAt < this.fxRefreshAfterMs) return;
    }
    const requestVersion = ++this.fxRequestVersion;
    this.fxRequest?.unsubscribe();
    this.fxPair = pair;
    this.fxQuote.set(null);
    this.fxLoading.set(true);
    this.fxErrorMessage.set('');
    this.fxRequest = this.fxRateService.quote(from, to)
      .pipe(finalize(() => {
        if (requestVersion === this.fxRequestVersion) this.fxLoading.set(false);
      }))
      .subscribe({
        next: quote => {
          if (requestVersion !== this.fxRequestVersion) return;
          this.fxQuote.set(quote);
          this.fxLoadedAt = Date.now();
        },
        error: () => {
          if (requestVersion !== this.fxRequestVersion) return;
          this.fxQuote.set(null);
          this.fxErrorMessage.set('The currency conversion estimate is unavailable. Retry the estimate or wait for prices to refresh.');
        }
      });
  }

  estimatedAccountSettlement(): number | null {
    const notional = this.estimatedNotional();
    const rate = this.fxQuote()?.rate;
    return notional === null || rate === undefined ? null : notional * rate;
  }

  estimatedNotional():

    number | null {



    const price =

      this.currentOrderPrice();



    const quantity =

      Number(this.orderQuantity);





    if (

      price === null ||

      !Number.isFinite(quantity) ||

      quantity <= 0

    ) {



      return null;



    }





    return price * quantity;



  }


  private customerOrderRejectionMessage(reason: string | null): string {
    const normalizedReason = reason?.toLowerCase() ?? '';

    if (normalizedReason.includes('insufficient cash')) {
      return 'You do not have enough available cash for this BUY order.';
    }

    if (
      normalizedReason.includes('insufficient holding')
      || normalizedReason.includes('insufficient position')
    ) {
      return 'You do not hold enough of this instrument to complete this SELL order.';
    }

    if (normalizedReason.includes('no market quote')) {
      return 'This instrument cannot be traded because a market quote is unavailable.';
    }

    if (
      normalizedReason.includes('fx provider')
      || normalizedReason.includes('fx rate')
      || normalizedReason.includes('currency conversion')
    ) {
      return 'The currency conversion service is temporarily unavailable. Please try again shortly.';
    }

    if (normalizedReason.includes('not currently tradable')) {
      return 'This instrument is not currently available for trading.';
    }

    if (normalizedReason.includes('order is not')) {
      return 'This order could not be completed because its status changed. Please try again.';
    }

    return 'The order was rejected. Please review the order details and try again.';
  }


  submitOrder(): void {
    if (this.orderSubmitting()) return;

    this.orderMessage.set('');
    this.orderErrorMessage.set('');
    this.quantityError.set('');

    // A confirmation must replay the original request, even if a quote disappears
    // or market polling changes while its outcome is unknown.
    let pending = this.pendingOrderRequest;
    if (!pending) {
      const instrument = this.selectedInstrument();
      if (!instrument) {
        this.orderErrorMessage.set('Select an instrument first.');
        return;
      }
      if (!instrument.latestQuote) {
        this.orderErrorMessage.set('No market quote is available for this instrument.');
        return;
      }
      const quantity = Number(this.orderQuantity);
      if (!Number.isFinite(quantity) || quantity <= 0) {
        this.quantityError.set('Enter a valid quantity greater than 0.');
        return;
      }
      try {
        pending = {
          request: { instrumentId: instrument.instrumentId, side: this.orderSide, quantity },
          key: this.idempotencyService.getOrCreateKey()
        };
      } catch {
        this.orderErrorMessage.set('Your browser could not save this request. Enable session storage, then try again.');
        return;
      }
      this.pendingOrderRequest = pending;
    }

    this.orderSubmitting.set(true);
    this.orderMessage.set(this.orderOutcomeUnconfirmed()
      ? 'Confirming your previous order...'
      : 'Submitting your order...');

    this.orderService.placeOrder({ ...pending.request }, pending.key)
      .pipe(finalize(() => this.orderSubmitting.set(false)))
      .subscribe({
        next: order => {
          this.addOrderToBlotter(order);
          this.loadDashboard(false);
          this.orderMessage.set('');

          switch (order.status) {
            case 'FILLED':
              this.completeOrderRequest();
              this.orderMessage.set(`Order #${order.id} filled.`);
              this.notificationService.show(
                `Order #${order.id} filled: ${order.side} ${order.quantity} units`, 'success', 5000);
              this.orderQuantity = null;
              break;
            case 'REJECTED':
              this.completeOrderRequest();
              this.orderErrorMessage.set(this.customerOrderRejectionMessage(order.rejectionReason));
              break;
            case 'ACCEPTED':
            case 'SUBMITTED':
              this.orderOutcomeUnconfirmed.set(true);
              this.orderMessage.set(`Order #${order.id} was received and is still processing. Confirm it again shortly.`);
              this.notificationService.show(`Order #${order.id} is still processing.`, 'info', 5000);
              break;
            default:
              this.orderOutcomeUnconfirmed.set(true);
              this.orderMessage.set(`Order #${order.id} received status: ${order.status}. Review order history before trying again.`);
          }
        },
        error: (error: HttpErrorResponse) => {
          this.orderMessage.set('');
          if (error.status === 401) {
            this.auth.clearToken();
            this.router.navigate(['/login']);
            return;
          }

          const rejectedOrder = error.error as OrderResponse;
          if (error.status === 400 && rejectedOrder
              && typeof rejectedOrder.id === 'number' && rejectedOrder.status === 'REJECTED') {
            this.addOrderToBlotter(rejectedOrder);
            this.completeOrderRequest();
            const message = this.customerOrderRejectionMessage(rejectedOrder.rejectionReason);
            this.orderErrorMessage.set(message);
            this.notificationService.show(`Order rejected: ${message}`, 'error', 5000);
            return;
          }

          if (error.status === 422 || error.status === 400 || error.status === 403) {
            this.completeOrderRequest();
            const message = error.status === 422
              ? 'This request matches an earlier order with different details. Review your order history before submitting again.'
              : error.status === 403
                ? 'You do not have permission to place this order. Sign in with the correct account.'
                : 'We could not submit this order. Check the instrument and quantity, then try again.';
            this.orderErrorMessage.set(message);
            this.notificationService.show(message, 'error', 5000);
            return;
          }

          // Lost responses, server failures and processing conflicts do not prove
          // rejection. Keep both the payload and its key for an explicit replay.
          this.orderOutcomeUnconfirmed.set(true);
          const message = error.status === 409
            ? 'This request could not be confirmed yet. Review order history, then choose Confirm previous order again shortly.'
            : 'We could not confirm the outcome of this order. Review your order history, then choose Confirm previous order to safely check the same request.';
          this.orderErrorMessage.set(message);
          this.notificationService.show(message, 'warning', 5000);
        }
      });
  }

  private completeOrderRequest(): void {
    this.pendingOrderRequest = null;
    this.orderOutcomeUnconfirmed.set(false);
    this.idempotencyService.clearKey();
  }

  private addOrderToBlotter(

    order: OrderResponse

  ): void {



    this.orders.update(

      current => [



        order,



        ...current.filter(

          existing =>

            existing.id !== order.id

        )



      ]

    );



  }



  ngOnDestroy(): void {

    if (this.quoteClockInterval !== null) {
      window.clearInterval(this.quoteClockInterval);
      this.quoteClockInterval = null;
    }
    this.fxRequestVersion++;
    this.fxRequest?.unsubscribe();
    this.dashboardRequestVersion++;
    this.dashboardRequest?.unsubscribe();
    this.portfolioHistoryRequest?.unsubscribe();



    if (this.marketRefreshInterval !== null) {



      window.clearInterval(

        this.marketRefreshInterval

      );



      this.marketRefreshInterval =

        null;



    }





    if (

      this.dashboardRefreshInterval !== null

    ) {



      window.clearInterval(

        this.dashboardRefreshInterval

      );



      this.dashboardRefreshInterval =

        null;



    }



  }



  currencyName(

    currency: string | null | undefined

  ): string {



    switch (

    currency?.toUpperCase()

    ) {



      case 'USD':

        return 'US Dollar';



      case 'GBP':

        return 'British Pound';



      case 'INR':

        return 'Indian Rupee';



      case 'EUR':

        return 'Euro';



      case 'CHF':

        return 'Swiss Franc';



      case 'CAD':

        return 'Canadian Dollar';



      case 'AUD':

        return 'Australian Dollar';



      case 'JPY':

        return 'Japanese Yen';



      default:

        return currency ?? '—';



    }



  }



  marketName(

    exchange: string | null | undefined

  ): string {



    switch (

    exchange?.toUpperCase()

    ) {



      case 'NASDAQ':

        return 'Nasdaq Stock Market';



      case 'NYSE':

        return 'New York Stock Exchange';



      case 'NSE_IN':

        return 'National Stock Exchange of India';



      case 'LSE':

        return 'London Stock Exchange';



      case 'XETRA':

        return 'Xetra';



      case 'SIX':

        return 'SIX Swiss Exchange';



      case 'TSX':

        return 'Toronto Stock Exchange';



      case 'ASX':

        return 'Australian Securities Exchange';



      case 'FOREX':

        return 'Foreign Exchange Market';



      case 'CRYPTO':

        return 'Cryptocurrency Market';



      default:

        return exchange ?? '—';



    }



  }

}
