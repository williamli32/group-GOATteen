import {
  Component,
  OnInit,
  signal,
  OnDestroy
} from '@angular/core';

import {
  MarketDataService,
  MarketInstrumentResponse
} from '../core/services/market-data';

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

  private marketRefreshInterval: number | null = null;

  private marketRefreshInProgress = false;

  constructor(
    private accountService: AccountService,
    private marketDataService: MarketDataService,
    private orderService: OrderService,
    private auth: Auth,
    private router: Router
  ) { }


  ngOnInit(): void {

    this.loadDashboard();

    this.loadMarketData();

    this.marketRefreshInterval = window.setInterval(
      () => this.loadMarketData(false),
      3000
    );

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

            } else {

              this.selectedInstrument.set(
                instruments[0] ?? null
              );

            }

          } else {

            this.selectedInstrument.set(
              instruments[0] ?? null
            );

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

          this.loadDashboard();

          this.orderMessage.set(
            `Order #${order.id} filled.`
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

  ngOnDestroy(): void {

    if (this.marketRefreshInterval !== null) {

      window.clearInterval(
        this.marketRefreshInterval
      );

      this.marketRefreshInterval = null;

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