import {
  Component,
  OnInit,
  signal
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


@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
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


  constructor(
    private accountService: AccountService,
    private marketDataService: MarketDataService,
    private auth: Auth,
    private router: Router
  ) { }


  ngOnInit(): void {

    this.loadDashboard();

    this.loadMarketData();

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


  selectInstrument(
    instrument: MarketInstrumentResponse
  ): void {

    this.selectedInstrument.set(
      instrument
    );

  }

}