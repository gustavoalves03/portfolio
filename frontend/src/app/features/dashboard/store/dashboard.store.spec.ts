import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { DashboardStore } from './dashboard.store';

describe('DashboardStore', () => {
  let store: InstanceType<typeof DashboardStore>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: 'http://t' },
        DashboardStore,
      ],
    });
    store = TestBed.inject(DashboardStore);
    http = TestBed.inject(HttpTestingController);
    // Drain the readiness call triggered by the store's onInit hook
    http.expectOne((r) => r.url.endsWith('/api/pro/tenant/readiness')).flush({
      slug: 'demo',
      name: false,
      hasCategory: false,
      hasContact: false,
      hasLogo: false,
      hasActiveCare: false,
      hasOpeningHours: false,
      canPublish: false,
      status: 'DRAFT',
    });
  });

  afterEach(() => http.verify());

  it('captures missing list when publish returns 422', () => {
    store.publish();
    const req = http.expectOne((r) => r.url.endsWith('/api/pro/tenant/publish'));
    req.flush({ message: 'Salon cannot be published', missing: ['hasContact', 'hasLogo'] }, {
      status: 422,
      statusText: 'Unprocessable Entity',
    });
    expect(store.publishMissing()).toEqual(['hasContact', 'hasLogo']);
    expect(store.publishSuccess()).toBeFalse();
  });

  it('clears publishMissing on success path', () => {
    store.publish();
    const req = http.expectOne((r) => r.url.endsWith('/api/pro/tenant/publish'));
    req.flush(null);
    // Followed by readiness reload
    http.expectOne((r) => r.url.endsWith('/api/pro/tenant/readiness')).flush({
      slug: 'demo',
      name: true,
      hasCategory: true,
      hasContact: true,
      hasLogo: true,
      hasActiveCare: true,
      hasOpeningHours: true,
      canPublish: true,
      status: 'ACTIVE',
    });
    expect(store.publishSuccess()).toBeTrue();
    expect(store.publishMissing()).toEqual([]);
  });

  it('redirects to /pro/onboarding/payment on 402', async () => {
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    store.publish();
    http.expectOne((r) => r.url.endsWith('/api/pro/tenant/publish')).flush(
      { message: 'Active subscription required', tier: 'PREMIUM', billing: 'MONTHLY' },
      { status: 402, statusText: 'Payment Required' }
    );

    expect(router.navigate).toHaveBeenCalledWith(
      ['/pro/onboarding/payment'],
      { queryParams: { tier: 'PREMIUM', billing: 'MONTHLY' } }
    );
  });

  it('falls back to GESTION/YEARLY when 402 body is incomplete', async () => {
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    store.publish();
    http.expectOne((r) => r.url.endsWith('/api/pro/tenant/publish')).flush(
      {},
      { status: 402, statusText: 'Payment Required' }
    );

    expect(router.navigate).toHaveBeenCalledWith(
      ['/pro/onboarding/payment'],
      { queryParams: { tier: 'GESTION', billing: 'YEARLY' } }
    );
  });

  it('clearPublishMissing resets the array', () => {
    // Manually set it via a 422 round-trip
    store.publish();
    http.expectOne((r) => r.url.endsWith('/api/pro/tenant/publish'))
      .flush({ missing: ['name'] }, { status: 422, statusText: 'Unprocessable Entity' });
    expect(store.publishMissing()).toEqual(['name']);
    store.clearPublishMissing();
    expect(store.publishMissing()).toEqual([]);
  });
});
