import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MotionPreferences } from './motion-preferences';

const storageKey = 'goatteen-reduced-motion';

describe('MotionPreferences', () => {
  beforeEach(() => localStorage.removeItem(storageKey));
  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.removeItem(storageKey);
    document.documentElement.removeAttribute('data-motion');
    vi.unstubAllGlobals();
  });

  function mediaPreference(matches = false) {
    const addEventListener = vi.fn();
    const removeEventListener = vi.fn();
    const media = { matches, addEventListener, removeEventListener };
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => media),
    );
    return media;
  }

  it('persists a manual pause and restores it on the next visit', () => {
    mediaPreference();
    let preference = TestBed.inject(MotionPreferences);
    preference.toggle();
    TestBed.tick();
    expect(preference.reduced()).toBe(true);
    expect(localStorage.getItem(storageKey)).toBe('true');
    expect(document.documentElement.getAttribute('data-motion')).toBe('reduced');
    TestBed.resetTestingModule();
    preference = TestBed.inject(MotionPreferences);
    expect(preference.reduced()).toBe(true);
    preference.toggle();
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-motion')).toBe('full');
  });

  it('follows system changes and never overrides the system request to reduce motion', () => {
    const media = mediaPreference(true);
    const preference = TestBed.inject(MotionPreferences);
    preference.toggle();
    expect(preference.reduced()).toBe(true);
    expect(localStorage.getItem(storageKey)).toBeNull();
    const onChange = media.addEventListener.mock.calls[0][1] as (event: {
      matches: boolean;
    }) => void;
    onChange({ matches: false });
    expect(preference.reduced()).toBe(false);
    onChange({ matches: true });
    TestBed.tick();
    expect(TestBed.inject(DOCUMENT).documentElement.getAttribute('data-motion')).toBe('reduced');
    TestBed.resetTestingModule();
    expect(media.removeEventListener).toHaveBeenCalledWith('change', onChange);
  });
});
