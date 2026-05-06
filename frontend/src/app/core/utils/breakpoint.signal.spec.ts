import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { isDesktopSignal } from './breakpoint.signal';

describe('isDesktopSignal', () => {
  it('returns false in non-browser (SSR) context', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: 'server' },
      ],
    });
    const signal = TestBed.runInInjectionContext(() => isDesktopSignal());
    expect(signal()).toBe(false);
  });

  it('reflects matchMedia(min-width: 768px) in browser context', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    });

    // Stub matchMedia to report desktop.
    const original = window.matchMedia;
    let listener: ((e: MediaQueryListEvent) => void) | null = null as ((e: MediaQueryListEvent) => void) | null;
    window.matchMedia = ((query: string): MediaQueryList => ({
      matches: query === '(min-width: 768px)',
      media: query,
      onchange: null,
      addEventListener: (_: string, cb: any) => { listener = cb; },
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    } as unknown as MediaQueryList)) as typeof window.matchMedia;

    try {
      const signal = TestBed.runInInjectionContext(() => isDesktopSignal());
      expect(signal()).toBe(true);
      // Simulate viewport shrink.
      listener?.({ matches: false } as MediaQueryListEvent);
      expect(signal()).toBe(false);
    } finally {
      window.matchMedia = original;
    }
  });
});
