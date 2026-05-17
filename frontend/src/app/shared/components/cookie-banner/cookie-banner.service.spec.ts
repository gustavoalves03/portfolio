import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { CookieBannerService, STORAGE_KEY } from './cookie-banner.service';

describe('CookieBannerService', () => {

  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
  });

  afterEach(() => {
    localStorage.removeItem(STORAGE_KEY);
  });

  function createService(platformId: 'browser' | 'server' = 'browser') {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        CookieBannerService,
        { provide: PLATFORM_ID, useValue: platformId },
      ],
    });
    return TestBed.inject(CookieBannerService);
  }

  it('starts not dismissed when storage is empty in browser', () => {
    const service = createService('browser');
    expect(service.dismissed()).toBe(false);
  });

  it('starts dismissed when localStorage already has the flag', () => {
    localStorage.setItem(STORAGE_KEY, 'dismissed');
    const service = createService('browser');
    expect(service.dismissed()).toBe(true);
  });

  it('starts dismissed on the server (SSR-safe, avoids hydration flash)', () => {
    const service = createService('server');
    expect(service.dismissed()).toBe(true);
  });

  it('dismiss() persists to localStorage and updates the signal', () => {
    const service = createService('browser');
    service.dismiss();
    expect(service.dismissed()).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dismissed');
  });

  it('dismiss() is a no-op on the server', () => {
    const service = createService('server');
    service.dismiss();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('dismiss() does not update the signal when localStorage.setItem throws', () => {
    const service = createService('browser');
    spyOn(localStorage, 'setItem').and.throwError('QuotaExceededError');
    service.dismiss();
    expect(service.dismissed()).toBe(false);
  });
});
