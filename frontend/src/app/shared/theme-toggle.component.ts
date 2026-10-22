import { Component, inject, input } from '@angular/core';
import { ThemePreferences } from '../core/ui/theme-preferences';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  template: `
    <button
      type="button"
      aria-label="Dark mode"
      [attr.aria-pressed]="theme.dark()"
      [class.inverse]="inverse()"
      [title]="theme.dark() ? 'Switch to light mode' : 'Switch to dark mode'"
      (click)="theme.toggle()"
    >
      <svg
        viewBox="0 0 20 20"
        fill="none"
        stroke="currentColor"
        stroke-width="1.4"
        aria-hidden="true"
      >
        @if (theme.dark()) {
          <path d="M16.7 11.2A7 7 0 0 1 8.8 3.3a7 7 0 1 0 7.9 7.9Z" stroke-linejoin="round" />
        } @else {
          <circle cx="10" cy="10" r="3.3" />
          <path
            d="M10 1.5v1.4m0 14.2v1.4M1.5 10h1.4m14.2 0h1.4M4 4l1 1m10 10 1 1M4 16l1-1M15 5l1-1"
            stroke-linecap="round"
          />
        }
      </svg>
      <span>Dark mode</span>
      <span class="switch" aria-hidden="true"><span></span></span>
    </button>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    button {
      display: flex;
      align-items: center;
      gap: 8px;
      min-height: 44px;
      padding: 7px 0;
      border: 0;
      border-radius: 6px;
      background: transparent;
      color: var(--muted);
      font: 500 12px var(--font);
    }
    svg {
      width: 19px;
      height: 19px;
    }
    .switch {
      display: inline-flex;
      align-items: center;
      width: 28px;
      height: 17px;
      padding: 2px;
      border: 1px solid var(--line-strong);
      border-radius: 12px;
      background: var(--surface-soft);
    }
    .switch > span {
      width: 11px;
      height: 11px;
      border-radius: 50%;
      background: var(--muted);
      transition: transform 0.18s var(--ease);
    }
    [aria-pressed='true'] .switch {
      background: var(--emerald-soft);
      border-color: var(--emerald);
    }
    [aria-pressed='true'] .switch > span {
      transform: translateX(11px);
      background: var(--emerald);
    }
    button:hover {
      color: var(--emerald);
    }
    .inverse {
      color: #b7cbbf;
    }
    .inverse .switch {
      background: #1c352a;
      border-color: #718d7c;
    }
    .inverse .switch > span {
      background: #b7cbbf;
    }
    .inverse[aria-pressed='true'] .switch {
      background: #2a493c;
      border-color: #79d5ac;
    }
    .inverse[aria-pressed='true'] .switch > span {
      background: #79d5ac;
    }
    button.inverse:hover {
      color: #d4f2dd;
    }
  `,
})
export class ThemeToggleComponent {
  readonly inverse = input(false);
  readonly theme = inject(ThemePreferences);
}
