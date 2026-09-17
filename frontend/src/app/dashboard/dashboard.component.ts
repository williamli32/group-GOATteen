import {
  Component,
  OnInit,
  OnDestroy,
  signal,
  computed
} from '@angular/core';

import {
  MarketDataService,
  MarketInstrumentResponse
} from '../core/services/market-data';

import {
  MarketDataMicroserviceClient,
  MarketData
} from '../core/services/market-data-microservice';

import {
  CommonModule
} from '@angular/common';

import {
  HttpErrorResponse
} from '@angular/common/http';

import {
  Router
} from '@angular/router';

import {
  finalize,
  forkJoin
} from 'rxjs';

import {
  AccountResponse,
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


@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  marketInstruments =
    signal<MarketInstrumentResponse[]>([]);

  selectedInstrument =
    signal<MarketInstrumentResponse | null>(null);

  marketData =
    signal<MarketData[]>([]);

  selectedMarketData =
    signal<MarketData | null>(null);

  activeTab =
    signal<'all' | 'forex' | 'crypto'>('all');

  filteredMarketData = computed(() => {
    const data = this.marketData();
    const tab = this.activeTab();

    if (tab === 'forex') {
      return data.filter(d => 
        d.symbol.includes('/') || 
        ['EUR/USD', 'GBP/USD', 'USD/JPY', 'AUD/USD', 'USD/CHF', 'USD/CAD'].includes(d.symbol)
      );
    } else if (tab === 'crypto') {
      return data.filter(d => 
        ['BTC', 'ETH', 'SOL', 'XRP', 'ADA', 'DOGE', 'AAPL-CRYPTO'].includes(d.symbol)
      );
    }
    return data;
  });

  marketLoading =
    signal(true);

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

  private autoRefreshInterval: number | null = null;


  constructor(
    private accountService: AccountService,
    private marketDataService: MarketDataService,
    private marketDataMicroserviceClient: MarketDataMicroserviceClient,
    private orderService: OrderService,
    private auth: Auth,
    private router: Router
  ) { }


  ngOnInit(): void {

    this.loadDashboard();

    this.loadMarketData();

    this.loadMicroserviceMarketData();

    // Auto-refresh market data every 3 seconds
    this.autoRefreshInterval = window.setInterval(
      () => this.loadMicroserviceMarketData(),
      3000
    );

  }

  ngOnDestroy(): void {

    // Clear the auto-refresh interval when component is destroyed
    if (this.autoRefreshInterval !== null) {
      clearInterval(this.autoRefreshInterval);
      this.autoRefreshInterval = null;
    }

  }


  loadDashboard(): void {

    this.loading.set(true);

    this.errorMessage.set('');


    forkJoin({

      account:
        this.accountService.getMyAccount(),

      holdings:
        this.accountService.getHoldings(),

      orders:
        this.accountService.getBlotter()

    })
      .pipe(

        finalize(() => {

          this.loading.set(false);

        })

      )
      .subscribe({

        next: result => {

          this.account.set(
            result.account
          );

          this.holdings.set(
            result.holdings
          );

          this.orders.set(
            result.orders
          );

        },


        error: (error: HttpErrorResponse) => {

          if (error.status === 401) {

            this.auth.clearToken();

            this.router.navigate([
              '/login'
            ]);

            return;
          }


          this.errorMessage.set(
            'Unable to load your account dashboard. Please try again.'
          );

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

  loadMarketData(): void {

    this.marketLoading.set(true);

    this.marketErrorMessage.set('');


    this.marketDataService
      .getInstruments()
      .pipe(

        finalize(() => {

          this.marketLoading.set(false);

        })

      )
      .subscribe({

        next: instruments => {

          this.marketInstruments.set(
            instruments
          );

          if (
            instruments.length > 0 &&
            !this.selectedInstrument()
          ) {

            this.selectedInstrument.set(
              instruments[0]
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


          this.marketErrorMessage.set(
            'Unable to load market data.'
          );

        }

      });

  }


  loadMicroserviceMarketData(): void {

    this.marketLoading.set(true);

    this.marketErrorMessage.set('');


    this.marketDataMicroserviceClient
      .getAllMarketData()
      .pipe(

        finalize(() => {

          this.marketLoading.set(false);

        })

      )
      .subscribe({

        next: (data: MarketData[]) => {

          this.marketData.set(
            data
          );

          if (
            data.length > 0 &&
            !this.selectedMarketData()
          ) {

            this.selectedMarketData.set(
              data[0]
            );

          }

          console.log(
            'Loaded ' + data.length + ' market data records from microservice'
          );

        },


        error: (
          error: HttpErrorResponse
        ) => {

          console.error(
            'Error loading market data from microservice:',
            error
          );

          this.marketErrorMessage.set(
            'Unable to load market data from microservice. Make sure the service is running on port 8081.'
          );

        }

      });

  }


  selectMarketData(
    data: MarketData
  ): void {

    this.selectedMarketData.set(
      data
    );

  }

  setActiveTab(
    tab: 'all' | 'forex' | 'crypto'
  ): void {

    this.activeTab.set(tab);
    this.selectedMarketData.set(null);

  }


  selectInstrument(
    instrument: MarketInstrumentResponse
  ): void {

    this.selectedInstrument.set(
      instrument
    );

    this.orderMessage.set('');

    this.orderErrorMessage.set('');

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

      this.orderErrorMessage.set(
        'Quantity must be greater than zero.'
      );

      return;

    }


    this.orderSubmitting.set(true);


    this.orderService
      .placeOrder({

        instrumentId:
          instrument.instrumentId,

        side:
          this.orderSide,

        quantity

      })
      .pipe(

        finalize(() => {

          this.orderSubmitting.set(
            false
          );

        })

      )
      .subscribe({

        next: order => {

          this.addOrderToBlotter(
            order
          );

          this.orderMessage.set(
            `Order #${order.id} accepted.`
          );

          this.orderQuantity =
            null;

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

            this.orderErrorMessage.set(
              rejectedOrder
                .rejectionReason
              ??
              'Order rejected.'
            );

            return;

          }


          this.orderErrorMessage.set(
            'Unable to submit the order.'
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

}