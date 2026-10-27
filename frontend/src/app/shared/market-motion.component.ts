import { Component, input } from '@angular/core';

let motionId = 0;

/** Abstract brand artwork, never a market-data chart. Decorative motion follows the shared CSS pause and reduced-motion rules. */
@Component({
  selector: 'app-market-motion',
  standalone: true,
  templateUrl: './market-motion.component.html',
  styleUrl: './market-motion.component.scss',
  host: {
    'aria-hidden': 'true',
    '[class.compact]': 'compact()',
    '[class.framed]': 'framed()',
  },
})
export class MarketMotionComponent {
  readonly compact = input(false);
  readonly framed = input(false);
  readonly id = 'goat-flow-' + ++motionId;
  readonly orbit = 'M69 237a231 86 0 1 0 462 0a231 86 0 1 0-462 0';
  readonly orbitMotionPath = `path("${this.orbit}")`;
}
