import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemePreferences } from './theme-preferences';

const storageKey = 'goatteen-theme';

describe('ThemePreferences', () => {
  let themeMeta: HTMLMetaElement;

  beforeEach(() => {
    localStorage.removeItem(storageKey);
    themeMeta = document.createElement('meta');
    themeMeta.name = 'theme-color';
    document.head.append(themeMeta);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    localStorage.removeItem(storageKey);
    document.documentElement.removeAttribute('data-theme');
    themeMeta.remove();
  });

  function mediaPreference(matches = false) {
    const media = { matches, addEventListener: vi.fn(), removeEventListener: vi.fn() };
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => media),
    );
    return media;
  }

  it('follows system changes until a user chooses a theme and removes its listener on destruction', () => {
    const media = mediaPreference(true);
    const preference = TestBed.inject(ThemePreferences);
    TestBed.tick();
    expect(preference.dark()).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(themeMeta.content).toBe('#101b18');
    expect(localStorage.getItem(storageKey)).toBeNull();

    const onChange = media.addEventListener.mock.calls[0][1] as (event: {
      matches: boolean;
    }) => void;
    onChange({ matches: false });
    TestBed.tick();
    expect(preference.dark()).toBe(false);
    expect(themeMeta.content).toBe('#f5f6f3');

    preference.toggle();
    onChange({ matches: false });
    expect(preference.dark()).toBe(true);
    TestBed.resetTestingModule();
    expect(media.removeEventListener).toHaveBeenCalledWith('change', onChange);
  });

  it.each(['light', 'dark'] as const)(
    'restores a saved %s preference instead of overriding it with the system theme',
    (theme) => {
      localStorage.setItem(storageKey, theme);
      mediaPreference(theme === 'light');
      const preference = TestBed.inject(ThemePreferences);
      TestBed.tick();
      expect(preference.theme()).toBe(theme);
      expect(document.documentElement.getAttribute('data-theme')).toBe(theme);
    },
  );

  it('persists both toggle directions and restores the choice on a new visit', () => {
    mediaPreference();
    let preference = TestBed.inject(ThemePreferences);
    preference.toggle();
    TestBed.tick();
    expect(localStorage.getItem(storageKey)).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    TestBed.resetTestingModule();
    preference = TestBed.inject(ThemePreferences);
    expect(preference.dark()).toBe(true);
    preference.toggle();
    TestBed.tick();
    expect(localStorage.getItem(storageKey)).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('ignores an invalid saved preference and falls back to the system', () => {
    localStorage.setItem(storageKey, 'not-a-theme');
    mediaPreference(true);
    expect(TestBed.inject(ThemePreferences).dark()).toBe(true);
  });

  it('keeps the toggle usable when reading or writing browser storage is blocked', () => {
    mediaPreference(true);
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('Blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('Blocked');
    });
    const preference = TestBed.inject(ThemePreferences);
    expect(preference.dark()).toBe(true);
    expect(() => preference.toggle()).not.toThrow();
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('syncs saved choices across tabs and resumes the system default when storage is cleared', () => {
    mediaPreference(true);
    const preference = TestBed.inject(ThemePreferences);
    localStorage.setItem(storageKey, 'light');
    window.dispatchEvent(new StorageEvent('storage', { key: storageKey, newValue: 'light' }));
    expect(preference.theme()).toBe('light');
    localStorage.clear();
    window.dispatchEvent(new StorageEvent('storage', { key: null }));
    expect(preference.theme()).toBe('dark');
  });

  it('defaults to light when the browser does not expose a system preference', () => {
    vi.stubGlobal('matchMedia', undefined);
    const preference = TestBed.inject(ThemePreferences);
    expect(preference.dark()).toBe(false);
    preference.toggle();
    expect(preference.dark()).toBe(true);
  });
});
