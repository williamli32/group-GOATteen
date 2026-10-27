import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';

/** Decorative motion is optional; system accessibility settings always take priority. */
@Injectable({ providedIn: 'root' })
export class MotionPreferences {
  private readonly document = inject(DOCUMENT);
  private readonly manuallyReduced = signal(this.readPreference());
  readonly systemReduced = signal(false);
  readonly reduced = computed(() => this.manuallyReduced() || this.systemReduced());

  constructor() {
    const media = this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)');
    if (media) {
      this.systemReduced.set(media.matches);
      const onChange = (event: MediaQueryListEvent) => this.systemReduced.set(event.matches);
      media.addEventListener('change', onChange);
      inject(DestroyRef).onDestroy(() => media.removeEventListener('change', onChange));
    }
    effect(() =>
      this.document.documentElement.setAttribute(
        'data-motion',
        this.reduced() ? 'reduced' : 'full',
      ),
    );
  }

  toggle(): void {
    if (this.systemReduced()) return;
    this.manuallyReduced.update((value) => !value);
    try {
      localStorage.setItem('goatteen-reduced-motion', String(this.manuallyReduced()));
    } catch {
      /* Motion remains controllable when browser storage is unavailable. */
    }
  }

  private readPreference(): boolean {
    try {
      return localStorage.getItem('goatteen-reduced-motion') === 'true';
    } catch {
      return false;
    }
  }
}
