import { Component, inject, input } from '@angular/core';
import { MotionPreferences } from '../core/ui/motion-preferences';

@Component({
  selector: 'app-motion-toggle',
  standalone: true,
  template: `<button
    type="button"
    (click)="motion.toggle()"
    [attr.aria-pressed]="motion.reduced()"
    [disabled]="motion.systemReduced()"
    [class.inverse]="inverse()"
    aria-label="Reduce decorative motion"
    [title]="
      motion.systemReduced()
        ? 'Reduced motion follows your system setting'
        : 'Control decorative animations'
    "
  >
    <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <circle cx="10" cy="10" r="7.2" stroke="currentColor" stroke-width="1.3" />
      @if (motion.reduced()) {
        <path d="m8.2 6.5 5 3.5-5 3.5z" fill="currentColor" />
      } @else {
        <path d="M8 7v6m4-6v6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      }</svg
    ><span>{{
      motion.systemReduced()
        ? 'Motion reduced by system'
        : motion.reduced()
          ? 'Motion paused'
          : 'Pause motion'
    }}</span>
  </button>`,
  styles: [
    `
      :host {
        display: block;
      }
      button {
        display: flex;
        align-items: center;
        gap: 8px;
        min-height: 40px;
        border: 0;
        background: transparent;
        color: var(--muted);
        font: 500 12px var(--font);
        padding: 7px 0;
        cursor: pointer;
      }
      svg {
        width: 19px;
        height: 19px;
      }
      .inverse {
        color: #a4b8ae;
      }
      button:hover:not(:disabled) {
        color: var(--emerald);
      }
      button.inverse:hover:not(:disabled) {
        color: #d4f2dd;
      }
      button:disabled {
        opacity: 1;
        cursor: default;
      }
    `,
  ],
})
export class MotionToggleComponent {
  readonly inverse = input(false);
  readonly motion = inject(MotionPreferences);
}
