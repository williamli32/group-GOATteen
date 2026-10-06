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
    MarketDataService
} from './market-data';

import {
    environment
} from '../../../environments/environment';


describe('MarketDataService', () => {

    let service: MarketDataService;

    let httpTesting:
        HttpTestingController;


    beforeEach(() => {

        TestBed.configureTestingModule({

            providers: [

                MarketDataService,

                provideHttpClient(),

                provideHttpClientTesting()

            ]

        });


        service =
            TestBed.inject(
                MarketDataService
            );

        httpTesting =
            TestBed.inject(
                HttpTestingController
            );

    });


    afterEach(() => {

        httpTesting.verify();

    });


    it(
        'should request tradable market instruments',
        () => {

            const mockInstruments = [

                {
                    instrumentId: 1,
                    symbol: 'AAPL',
                    name: 'Apple Inc.',
                    instrumentClass: 'EQUITY_US',
                    currency: 'USD',
                    tradable: true,

                    latestQuote: {

                        quoteId: 10,
                        instrumentId: 1,
                        symbol: 'AAPL',
                        bidPrice: 200,
                        askPrice: 200.1,
                        lastPrice: 200.05,
                        quotedAt:
                            '2026-09-14T19:00:00'

                    }

                }

            ];


            service
                .getInstruments()
                .subscribe(
                    instruments => {

                        expect(
                            instruments.length
                        ).toBe(1);

                        expect(
                            instruments[0].symbol
                        ).toBe('AAPL');

                        expect(
                            instruments[0]
                                .latestQuote
                                ?.lastPrice
                        ).toBe(200.05);

                    }
                );


            const request =
                httpTesting.expectOne(
                    `${environment.apiUrl}/market/instruments`
                );


            expect(
                request.request.method
            ).toBe('GET');


            request.flush(
                mockInstruments
            );

        }
    );


    it(
        'should request the latest quote for an instrument',
        () => {

            const mockQuote = {

                quoteId: 10,
                instrumentId: 1,
                symbol: 'AAPL',
                bidPrice: 200,
                askPrice: 200.1,
                lastPrice: 200.05,
                quotedAt:
                    '2026-09-14T19:00:00'

            };


            service
                .getLatestQuote(1)
                .subscribe(
                    quote => {

                        expect(
                            quote.symbol
                        ).toBe('AAPL');

                        expect(
                            quote.bidPrice
                        ).toBe(200);

                        expect(
                            quote.askPrice
                        ).toBe(200.1);

                    }
                );


            const request =
                httpTesting.expectOne(
                    `${environment.apiUrl}/market/instruments/1/quote`
                );


            expect(
                request.request.method
            ).toBe('GET');


            request.flush(
                mockQuote
            );

        }
    );

});