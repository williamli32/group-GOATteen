import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';

type Theme = 'light' | 'dark';
const storageKey = 'goatteen-theme';

/** Follow the system until the user makes a choice; keep that choice across visits. */
@Injectable({ providedIn: 'root' })
export class ThemePreferences {
  private readonly document = inject(DOCUMENT);
  private readonly window = this.document.defaultView;
  private readonly preference = signal<Theme | null>(this.readPreference());
  private readonly systemDark = signal(false);
  readonly theme = computed<Theme>(
    () => this.preference() ?? (this.systemDark() ? 'dark' : 'light'),
  );
  readonly dark = computed(() => this.theme() === 'dark');

  constructor() {
    const destroyRef = inject(DestroyRef);
    const media = this.window?.matchMedia?.('(prefers-color-scheme: dark)');
    if (media) {
      this.systemDark.set(media.matches);
      const onChange = (event: MediaQueryListEvent) => this.systemDark.set(event.matches);
      media.addEventListener('change', onChange);
      destroyRef.onDestroy(() => media.removeEventListener('change', onChange));
    }

    // Reflect changes from another tab, including removal of the saved preference.
    const onStorage = (event: StorageEvent) => {
      if (event.key === storageKey || event.key === null) {
        this.preference.set(this.readPreference());
      }
    };
    this.window?.addEventListener('storage', onStorage);
    destroyRef.onDestroy(() => this.window?.removeEventListener('storage', onStorage));

    effect(() => {
      const theme = this.theme();
      this.document.documentElement.setAttribute('data-theme', theme);
      this.document
        .querySelector('meta[name="theme-color"]')
        ?.setAttribute('content', theme === 'dark' ? '#101b18' : '#f5f6f3');
    });
  }

  toggle(): void {
    const theme = this.dark() ? 'light' : 'dark';
    this.preference.set(theme);
    try {
      this.window?.localStorage.setItem(storageKey, theme);
    } catch {
      // The theme still works for this visit when browser storage is unavailable.
    }
  }

  private readPreference(): Theme | null {
    try {
      const theme = this.window?.localStorage.getItem(storageKey);
      return theme === 'light' || theme === 'dark' ? theme : null;
    } catch {
      return null;
    }
  }
}
