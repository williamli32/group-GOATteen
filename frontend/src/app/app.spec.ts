import { TestBed } from '@angular/core/testing';
<<<<<<< HEAD
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Hello, frontend');
  });
});
=======
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {

  beforeEach(async () => {

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([])
      ]
    }).compileComponents();

  });


  it('should create the app', () => {

    const fixture =
      TestBed.createComponent(App);

    expect(
      fixture.componentInstance
    ).toBeTruthy();

  });


  it('should render the router outlet', () => {

    const fixture =
      TestBed.createComponent(App);

    fixture.detectChanges();

    const compiled =
      fixture.nativeElement as HTMLElement;

    expect(
      compiled.querySelector(
        'router-outlet'
      )
    ).toBeTruthy();

  });

});
>>>>>>> 272756fdd31cbc5e77a8f9646662bbac0fccf6b4
