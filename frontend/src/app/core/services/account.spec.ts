import {
  TestBed
} from '@angular/core/testing';

import {
  provideHttpClient
} from '@angular/common/http';

import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import {
  AccountService
} from './account';

import {
  environment
} from '../../../environments/environment';


describe('AccountService', () => {

  let service: AccountService;

  let httpTesting:
    HttpTestingController;


  beforeEach(() => {

    TestBed.configureTestingModule({

      providers: [

        AccountService,

        provideHttpClient(),

        provideHttpClientTesting()

      ]

    });


    service =
      TestBed.inject(AccountService);

    httpTesting =
      TestBed.inject(
        HttpTestingController
      );

  });


  afterEach(() => {

    httpTesting.verify();

  });


  it(
    'should request the current account',
    () => {

      const mockAccount = {

        accountId: 1,

        accountNumber:
          'LEAP-ABC123',

        cashBalance: 1000,

        currency: 'GBP',

        firstName: 'Joanna',

        lastName: 'Smith'

      };


      service.getMyAccount()
        .subscribe(account => {

          expect(
            account.accountNumber
          ).toBe(
            'LEAP-ABC123'
          );

        });


      const request =
        httpTesting.expectOne(
          `${environment.apiUrl}/accounts/me`
        );


      expect(
        request.request.method
      ).toBe('GET');


      request.flush(
        mockAccount
      );

    }
  );


  it(
    'should request current-user holdings',
    () => {

      service.getHoldings()
        .subscribe();


      const request =
        httpTesting.expectOne(
          `${environment.apiUrl}/accounts/me/holdings`
        );


      expect(
        request.request.method
      ).toBe('GET');


      request.flush({

        accountId: 1,

        cashBalance: 1000,

        currency: 'GBP',

        positions: []

      });

    }
  );


  it(
    'should request current-user blotter',
    () => {

      service.getBlotter()
        .subscribe();


      const request =
        httpTesting.expectOne(
          `${environment.apiUrl}/accounts/me/blotter`
        );


      expect(
        request.request.method
      ).toBe('GET');


      request.flush([]);

    }
  );

});