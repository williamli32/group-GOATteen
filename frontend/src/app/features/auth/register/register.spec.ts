import {
  ComponentFixture,
  TestBed
} from '@angular/core/testing';

import {
  provideHttpClient
} from '@angular/common/http';

import {
  provideHttpClientTesting
} from '@angular/common/http/testing';

import {
  provideRouter
} from '@angular/router';

import { Register } from './register';


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

  });


  it('should create', () => {

    expect(component).toBeTruthy();

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
});