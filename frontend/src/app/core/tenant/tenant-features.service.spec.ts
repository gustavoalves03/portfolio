import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { signal } from '@angular/core';

import { TenantFeaturesService } from './tenant-features.service';
import { API_BASE_URL } from '../config/api-base-url.token';
import { AuthService } from '../auth/auth.service';
import { Role } from '../auth/auth.model';

/**
 * Pinned behavior: every settings PUT now triggers a discrete confirmation
 * snackbar so the auto-save isn't silent. Pinning the call here protects
 * against a future refactor accidentally dropping the side-effect.
 */
describe('TenantFeaturesService — auto-save snackbar', () => {
  let service: TenantFeaturesService;
  let httpMock: HttpTestingController;
  let snackOpen: jasmine.Spy;

  beforeEach(() => {
    snackOpen = jasmine.createSpy('open');
    const snackBar = { open: snackOpen } as unknown as MatSnackBar;

    // Anonymous user: skips the loadFeatures effect, so no surprise GET
    // before the test issues its PUT.
    const auth = {
      user: signal(null as any),
      isAuthenticated: () => false,
    };

    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://test' },
        { provide: AuthService, useValue: auth },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });

    service = TestBed.inject(TenantFeaturesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushPut(url: string, body?: unknown): void {
    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('PUT');
    req.flush(body ?? {});
  }

  it('setMinAdvanceMinutes: snackbar fires on successful PUT', () => {
    service.setMinAdvanceMinutes(60);
    flushPut('http://test/api/pro/tenant/settings/min-advance-minutes');
    expect(service.minAdvanceMinutes()).toBe(60);
    expect(snackOpen).toHaveBeenCalledTimes(1);
  });

  it('setMaxAdvanceDays: snackbar fires on success', () => {
    service.setMaxAdvanceDays(30);
    flushPut('http://test/api/pro/tenant/settings/max-advance-days');
    expect(service.maxAdvanceDays()).toBe(30);
    expect(snackOpen).toHaveBeenCalledTimes(1);
  });

  it('setMaxClientHoursPerDay: snackbar fires on success', () => {
    service.setMaxClientHoursPerDay(4);
    flushPut('http://test/api/pro/tenant/settings/max-client-hours-per-day');
    expect(service.maxClientHoursPerDay()).toBe(4);
    expect(snackOpen).toHaveBeenCalledTimes(1);
  });

  it('toggleEmployees: snackbar fires on success', () => {
    service.toggleEmployees(true);
    flushPut('http://test/api/pro/tenant/settings/employees');
    expect(service.employeesEnabled()).toBeTrue();
    expect(snackOpen).toHaveBeenCalledTimes(1);
  });

  it('setAnnualLeaveDays: snackbar fires on success', () => {
    service.setAnnualLeaveDays(20);
    flushPut('http://test/api/pro/tenant/settings/annual-leave-days');
    expect(service.annualLeaveDays()).toBe(20);
    expect(snackOpen).toHaveBeenCalledTimes(1);
  });

  it('toggleClosedOnHolidays: snackbar fires on success', () => {
    service.toggleClosedOnHolidays(false);
    flushPut('http://test/api/pro/tenant/settings/closed-on-holidays');
    expect(service.closedOnHolidays()).toBeFalse();
    expect(snackOpen).toHaveBeenCalledTimes(1);
  });

  it('does not snackbar on HTTP error (silent skip is acceptable for now)', () => {
    service.setMinAdvanceMinutes(5);
    const req = httpMock.expectOne('http://test/api/pro/tenant/settings/min-advance-minutes');
    req.flush({}, { status: 400, statusText: 'Bad Request' });
    expect(snackOpen).not.toHaveBeenCalled();
  });

  // ─────────────────────────────────────────────────────────────
  // Adversarial: rapid clicks, race conditions, spam input
  // ─────────────────────────────────────────────────────────────

  describe('adversarial', () => {
    it('rapid double click on toggleEmployees: each click fires its own PUT, only the latest commits', () => {
      service.toggleEmployees(true);
      service.toggleEmployees(false);
      service.toggleEmployees(true);

      const reqs = httpMock.match('http://test/api/pro/tenant/settings/employees');
      expect(reqs.length).toBe(3);
      reqs[0].flush({}); // stale → ignored by seq guard
      reqs[1].flush({}); // stale → ignored
      reqs[2].flush({}); // latest → commits

      expect(service.employeesEnabled()).toBeTrue();
      // Only the winning response triggers a single snackbar — earlier
      // requests are silently absorbed so the user isn't spammed.
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });

    it('rapid double click: latest dispatch wins, even when responses arrive out-of-order', () => {
      // First call inflight (60), user clicks again (120) before it returns.
      service.setMinAdvanceMinutes(60);
      service.setMinAdvanceMinutes(120);

      const reqs = httpMock.match('http://test/api/pro/tenant/settings/min-advance-minutes');
      expect(reqs.length).toBe(2);
      // Server resolves them in REVERSE order (HTTP/2 multiplexing).
      reqs[1].flush({}); // second dispatch lands first → seq matches → state = 120
      reqs[0].flush({}); // first dispatch lands second → seq stale → ignored

      // The seq guard ensures the latest dispatched value sticks.
      expect(service.minAdvanceMinutes()).toBe(120);
      // Only one snackbar — the stale response shouldn't notify either.
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });

    it('rapid double click: when the latest dispatch fails, state stays at last successful', () => {
      // Start from 120 (successful first call), then fire 60 which fails.
      service.setMinAdvanceMinutes(120);
      const firstReq = httpMock.expectOne('http://test/api/pro/tenant/settings/min-advance-minutes');
      firstReq.flush({});
      expect(service.minAdvanceMinutes()).toBe(120);
      snackOpen.calls.reset();

      service.setMinAdvanceMinutes(60);
      const secondReq = httpMock.expectOne('http://test/api/pro/tenant/settings/min-advance-minutes');
      secondReq.flush({}, { status: 500, statusText: 'Server Error' });

      // Failed → no commit, no snackbar.
      expect(service.minAdvanceMinutes()).toBe(120);
      expect(snackOpen).not.toHaveBeenCalled();
    });

    it('spam: 10 toggles fire 10 PUTs but only the last response commits', () => {
      for (let i = 0; i < 10; i++) {
        service.toggleClosedOnHolidays(i % 2 === 0);
      }
      const reqs = httpMock.match('http://test/api/pro/tenant/settings/closed-on-holidays');
      expect(reqs.length).toBe(10);
      reqs.forEach((r) => r.flush({}));
      // Sequence guard: 9 stale responses ignored, only the 10th notifies.
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });

    it('mixed setters fire independently — no cross-talk between settings', () => {
      service.setMinAdvanceMinutes(60);
      service.setMaxAdvanceDays(45);
      service.setMaxClientHoursPerDay(6);

      flushPut('http://test/api/pro/tenant/settings/min-advance-minutes');
      flushPut('http://test/api/pro/tenant/settings/max-advance-days');
      flushPut('http://test/api/pro/tenant/settings/max-client-hours-per-day');

      expect(service.minAdvanceMinutes()).toBe(60);
      expect(service.maxAdvanceDays()).toBe(45);
      expect(service.maxClientHoursPerDay()).toBe(6);
      expect(snackOpen).toHaveBeenCalledTimes(3);
    });

    it('huge integer is forwarded verbatim — no silent overflow', () => {
      const huge = Number.MAX_SAFE_INTEGER;
      service.setMaxClientHoursPerDay(huge);
      flushPut('http://test/api/pro/tenant/settings/max-client-hours-per-day');
      expect(service.maxClientHoursPerDay()).toBe(huge);
    });
  });
});
