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

  OrderSide

} from '../core/services/order';



import {

  NotificationService

} from '../core/services/notification';

type DashboardTab = 'overview' | 'watchlist' | 'orders' | 'holdings';





@Component({

  selector: 'app-dashboard',

  standalone: true,

  imports: [

    CommonModule,

    FormsModule,
    PriceAlertPopupListenerComponent,

    AccountFundingComponent,
    WatchlistComponent,
    DailyPriceChangeComponent

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

    const ageMs = Math.max(0, Date.now() - quotedTime);
    return Math.floor(ageMs / 1000);
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
  selectedInstrument =

    signal<MarketInstrumentResponse | null>(null);



  marketLoading =

    signal(true);


  readonly marketRefreshFailed = signal(false);

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
  private fxRequest?: Subscription;
  private fxPair = '';

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





  ngOnInit(): void {



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

                instruments[0] ?? null

              );
              this.refreshFxQuote();



            }



          } else {



            this.selectedInstrument.set(

              instruments[0] ?? null

            );
            this.refreshFxQuote();



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



  selectInstrument(

    instrument: MarketInstrumentResponse

  ): void {

    if (this.orderSubmitting()) return;

    this.selectedInstrument.set(

      instrument

    );
    this.refreshFxQuote();



    this.orderMessage.set('');



    this.orderErrorMessage.set('');



    this.quantityError.set('');

    this.selectTab('orders');

  }



  setOrderSide(

    side: OrderSide

  ): void {



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
  private refreshFxQuote(): void {
    const from = this.selectedInstrument()?.currency;
    const to = this.account()?.currency;
    if (!from || !to) return;
    const pair = `${from}/${to}`;
    if (pair === this.fxPair) return;
    this.fxPair = pair;
    this.fxQuote.set(null);
    this.fxRequest?.unsubscribe();
    this.fxRequest = this.fxRateService.quote(from, to).subscribe({
      next: quote => { if (this.fxPair === pair) this.fxQuote.set(quote); },
      error: () => { if (this.fxPair === pair) this.fxQuote.set(null); }
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





  submitOrder(): void {



    if (this.orderSubmitting()) {

      return;

    }





    this.orderMessage.set('');



    this.orderErrorMessage.set('');



    this.quantityError.set('');





    const instrument =

      this.selectedInstrument();





    if (!instrument) {



      this.orderErrorMessage.set(

        'Select an instrument first.'

      );



      return;



    }





    if (!instrument.latestQuote) {



      this.orderErrorMessage.set(

        'No market quote is available for this instrument.'

      );



      return;



    }





    const quantity =

      Number(this.orderQuantity);





    if (

      !Number.isFinite(quantity) ||

      quantity <= 0

    ) {



      this.quantityError.set(

        'Enter a valid quantity greater than 0.'

      );



      return;



    }





    this.orderSubmitting.set(true);



    const idempotencyKey =

      this.idempotencyService.getOrCreateKey();



    this.orderService.placeOrder(

      {

        instrumentId:

          instrument.instrumentId,



        side:

          this.orderSide,



        quantity

      },

      idempotencyKey

    )

      .pipe(



        finalize(() => {



          this.orderSubmitting.set(

            false

          );



        })



      )

      .subscribe({



      next: order => {
        this.addOrderToBlotter(order);
        this.loadDashboard(false);

        switch (order.status) {
          case 'FILLED':
            this.orderMessage.set(`Order #${order.id} filled.`);

            this.notificationService.show(
              `Order #${order.id} filled: ${order.side} ${order.quantity} units`,
              'success',
              5000
            );

            this.orderQuantity = null;
            this.idempotencyService.clearKey();
            break;

          case 'ACCEPTED':
          case 'SUBMITTED':
            this.orderMessage.set(
              `Order #${order.id} was received and is still processing.`
            );

            this.notificationService.show(
              `Order #${order.id} is still processing.`,
              'success',
              5000
            );
            break;

          case 'REJECTED':
            this.orderErrorMessage.set(
              order.rejectionReason ?? 'The order was rejected.'
            );

            this.idempotencyService.clearKey();
            break;

          default:
            this.orderMessage.set(
              `Order #${order.id} received status: ${order.status}.`
            );
        }
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





          /*

           * Business-rule rejection from the

           * order controller returns the stored

           * OrderResponse in the 400 body.

           */

          const rejectedOrder =

            error.error as OrderResponse;





          if (

            error.status === 400 &&

            rejectedOrder &&

            typeof rejectedOrder.id === 'number' &&

            rejectedOrder.status === 'REJECTED'

          ) {



            this.addOrderToBlotter(

              rejectedOrder

            );



            const rejectionMsg = rejectedOrder.rejectionReason ?? 'Order rejected.';



            this.orderErrorMessage.set(

              rejectionMsg

            );



            this.notificationService.show(

              `✗ Order rejected: ${rejectionMsg}`,

              'error',

              5000

            );



            
            /*
             * The order was definitively rejected by the server.
             * This idempotency key must not be reused for the
             * user's next, potentially different order.
             */
            this.idempotencyService.clearKey();

            return;



          }





          /*
           * The server has determined that the current idempotency
           * key was already used for different order parameters.
           * Discard the stale key so the next submission gets a
           * fresh key.
           */
          if (error.status === 422) {

            this.idempotencyService.clearKey();

            this.orderErrorMessage.set(
              'The previous order request key was already used. Please submit the order again.'
            );

            this.notificationService.show(
              '✗ Previous order request expired. Please submit the order again.',
              'error',
              5000
            );

            return;

          }


          this.orderErrorMessage.set(

            'Unable to submit the order.'

          );



          this.notificationService.show(

            '✗ Unable to submit the order. Please try again.',

            'error',

            5000

          );



        }



      });



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

    this.fxRequest?.unsubscribe();
    this.dashboardRequestVersion++;
    this.dashboardRequest?.unsubscribe();



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
