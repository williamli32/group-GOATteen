import {
  ComponentFixture,
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
  provideRouter
} from '@angular/router';

import { Register } from './register';
import { environment } from '../../../../environments/environment';


describe('Register', () => {

  let component: Register;
  let fixture: ComponentFixture<Register>;


  beforeEach(async () => {

    await TestBed.configureTestingModule({

      imports: [
        Register
      ],

      providers: [

        provideHttpClient(),
        provideHttpClientTesting(),

        provideRouter([])

      ]

    }).compileComponents();


    fixture =
      TestBed.createComponent(Register);

    component =
      fixture.componentInstance;

    fixture.detectChanges();

    TestBed.inject(HttpTestingController).expectOne(environment.apiUrl + '/auth/account-currencies').flush([
      { code: 'USD', name: 'US Dollar', minorUnits: 2 },
      { code: 'GBP', name: 'British Pound', minorUnits: 2 },
      { code: 'INR', name: 'Indian Rupee', minorUnits: 2 }
    ]);

  });



  it(
    'should reject mismatched passwords',
    () => {

      component.firstName = 'Joanna';
      component.lastName = 'Smith';

      component.email =
        'joanna@example.com';

      component.password =
        'Password123!';

      component.confirmPassword =
        'DifferentPassword123!';

      component.register();

      expect(
        component.validationMessage()
      ).toBe(
        'Passwords do not match.'
      );

    }
  );

  it(
    'should reject a password shorter than 12 characters',
    () => {

      component.firstName =
        'Joanna';

      component.lastName =
        'Smith';

      component.email =
        'joanna@example.com';

      component.password =
        'Short123!';

      component.confirmPassword =
        'Short123!';


      component.register();


      expect(
        component.validationMessage()
      ).toBe(
        'Password must be between 12 and 72 characters.'
      );

    }
  );

  it('should require an access code for analyst registration', () => {
    component.firstName = 'Joanna';
    component.lastName = 'Smith';
    component.email = 'joanna@example.com';
    component.password = 'Password123!';
    component.confirmPassword = 'Password123!';
    component.role = 'ANALYST';

    component.register();

    expect(component.validationMessage()).toBe(
      'An analyst access code is required.'
    );
  });

  it('does not require an access code for client registration', () => {
    component.firstName = 'Joanna';
    component.lastName = 'Smith';
    component.email = 'joanna@example.com';
    component.password = 'Password123!';
    component.confirmPassword = 'Password123!';
    component.role = 'CLIENT';
    component.currency = 'USD';

    component.register();

    expect(component.validationMessage()).toBe('');
  });
  it('offers customer, analyst and operations with explicit account currency selection', () => {
    fixture.detectChanges();
    const types = Array.from(fixture.nativeElement.querySelectorAll('#role option'),
      (option: HTMLOptionElement) => option.value);
    expect(types).toEqual(['CLIENT', 'ANALYST', 'OPERATIONS']);
    expect(component.currency).toBe('');
    expect(fixture.nativeElement.querySelector('#currency').textContent).toContain('USD');
    expect(fixture.nativeElement.querySelector('.submit-button').disabled).toBe(true);
  });

  function completeForm() {
    component.firstName = 'Test'; component.lastName = 'User'; component.email = 'test@example.com';
    component.password = 'Password123!'; component.confirmPassword = 'Password123!';
    component.currency = 'USD';
  }

  it('requires an operations access code and sends the role and currency', () => {
    completeForm();
    component.role = 'OPERATIONS';
    component.register();
    expect(component.validationMessage()).toBe('An operations access code is required.');
    component.accessCode = 'staff-code-from-user';
    component.register();
    TestBed.inject(HttpTestingController).expectOne(environment.apiUrl + '/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 'csrf' });
    const request = TestBed.inject(HttpTestingController).expectOne(environment.apiUrl + '/auth/register');
    expect(request.request.body).toMatchObject({ role: 'OPERATIONS', currency: 'USD', accessCode: 'staff-code-from-user' });
    request.flush({}, { status: 400, statusText: 'Bad Request' });
  });

  it('requires a currency before creating any kind of account', () => {
    completeForm();
    component.currency = '';
    component.register();
    expect(component.validationMessage()).toBe('Please select an account currency.');
    TestBed.inject(HttpTestingController).expectNone(environment.apiUrl + '/auth/register');
  });

  it('does not transmit a staff code for a customer or send duplicate registrations', () => {
    completeForm();
    component.accessCode = 'previous-staff-code';
    component.register();
    component.register();
    TestBed.inject(HttpTestingController).expectOne(environment.apiUrl + '/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN', token: 'csrf' });
    const requests = TestBed.inject(HttpTestingController).match(environment.apiUrl + '/auth/register');
    expect(requests.length).toBe(1);
    expect(requests[0].request.body.currency).toBe('USD');
    expect(requests[0].request.body.accessCode).toBeUndefined();
    requests[0].flush({}, { status: 409, statusText: 'Conflict' });
  });
});
