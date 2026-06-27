import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { API_BASE_URL } from '../config/api-base-url.token';
import { FeatureFlagsStore } from './feature-flags.store';

describe('FeatureFlagsStore', () => {
  let store: InstanceType<typeof FeatureFlagsStore>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        FeatureFlagsStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '' },
      ],
    });
    store = TestBed.inject(FeatureFlagsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('isEnabled returns false before load', () => {
    expect(store.isEnabled('BOOKING')()).toBe(false);
  });

  it('loads flags and exposes isEnabled', () => {
    store.load();
    const req = http.expectOne('/api/me/features');
    req.flush({
      BOOKING: true,
      SHOP: false,
      EMPLOYEES: true,
      PHOTOS: true,
      SMS_REMINDER: true,
      CLIENT_FILES: true,
      ABSENCE_MGMT: true,
      ONLINE_PAYMENT: false,
      LOYALTY: false,
      MULTI_LOCATION: false,
    });
    expect(store.isEnabled('BOOKING')()).toBe(true);
    expect(store.isEnabled('SHOP')()).toBe(false);
  });

  it('reset clears the snapshot', () => {
    store.load();
    http.expectOne('/api/me/features').flush({
      BOOKING: true,
      SHOP: false,
      EMPLOYEES: false,
      PHOTOS: false,
      SMS_REMINDER: false,
      CLIENT_FILES: false,
      ABSENCE_MGMT: false,
      ONLINE_PAYMENT: false,
      LOYALTY: false,
      MULTI_LOCATION: false,
    });
    expect(store.isEnabled('BOOKING')()).toBe(true);
    store.reset();
    expect(store.isEnabled('BOOKING')()).toBe(false);
  });
});
