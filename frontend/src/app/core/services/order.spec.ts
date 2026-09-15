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
    OrderService
} from './order';

import {
    environment
} from '../../../environments/environment';


describe('OrderService', () => {

    let service: OrderService;

    let httpTesting:
        HttpTestingController;


    beforeEach(() => {

        TestBed.configureTestingModule({

            providers: [

                OrderService,

                provideHttpClient(),

                provideHttpClientTesting()

            ]

        });


        service =
            TestBed.inject(
                OrderService
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
        'should submit an order without an account id',
        () => {

            const requestBody = {

                instrumentId: 1,

                side: 'BUY' as const,

                quantity: 2

            };


            service
                .placeOrder(requestBody)
                .subscribe(order => {

                    expect(
                        order.status
                    ).toBe('ACCEPTED');

                });


            const request =
                httpTesting.expectOne(
                    `${environment.apiUrl}/orders`
                );


            expect(
                request.request.method
            ).toBe('POST');


            expect(
                request.request.body
            ).toEqual(
                requestBody
            );


            expect(
                request.request.body.accountId
            ).toBeUndefined();


            request.flush({

                id: 15,

                side: 'BUY',

                quantity: 2,

                status: 'ACCEPTED',

                rejectionReason: null,

                fillPrice: null,

                submittedAt:
                    '2026-09-15T14:00:00',

                filledAt: null

            });

        }
    );

});