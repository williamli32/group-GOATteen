import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ThemeToggleComponent } from './theme-toggle.component';

describe('ThemeToggleComponent', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.removeItem('goatteen-theme');
    document.documentElement.removeAttribute('data-theme');
    vi.unstubAllGlobals();
  });

  it('exposes the current state to assistive technology and keeps two controls in sync', () => {
    localStorage.setItem('goatteen-theme', 'light');
    vi.stubGlobal('matchMedia', undefined);
    const desktop = TestBed.createComponent(ThemeToggleComponent);
    const mobile = TestBed.createComponent(ThemeToggleComponent);
    desktop.componentRef.setInput('inverse', true);
    desktop.detectChanges();
    mobile.detectChanges();
    const button = desktop.nativeElement.querySelector('button') as HTMLButtonElement;
    const otherButton = mobile.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.getAttribute('aria-label')).toBe('Dark mode');
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(button.title).toBe('Switch to dark mode');
    button.click();
    desktop.detectChanges();
    mobile.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(otherButton.getAttribute('aria-pressed')).toBe('true');
    expect(button.title).toBe('Switch to light mode');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    otherButton.click();
    desktop.detectChanges();
    mobile.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(localStorage.getItem('goatteen-theme')).toBe('light');
  });
});
