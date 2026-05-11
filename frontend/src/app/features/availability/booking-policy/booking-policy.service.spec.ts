import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { BookingPolicyService } from './booking-policy.service';

describe('BookingPolicyService', () => {
  let service: BookingPolicyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://test' },
        BookingPolicyService,
      ],
    });
    service = TestBed.inject(BookingPolicyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs the current policy', () => {
    let received: any;
    service.getCurrent().subscribe((p) => (received = p));
    const req = http.expectOne('http://test/api/pro/booking-policy');
    expect(req.request.method).toBe('GET');
    req.flush({
      maxBookingsPerDayPerClient: 1,
      maxBookingsPerWeekForNewClient: 1,
      updatedAt: '2026-05-11T10:00:00',
    });
    expect(received.maxBookingsPerDayPerClient).toBe(1);
  });

  it('PUTs an update with the request body', () => {
    service.update({ maxBookingsPerDayPerClient: 3, maxBookingsPerWeekForNewClient: 2 }).subscribe();
    const req = http.expectOne('http://test/api/pro/booking-policy');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ maxBookingsPerDayPerClient: 3, maxBookingsPerWeekForNewClient: 2 });
    req.flush({
      maxBookingsPerDayPerClient: 3,
      maxBookingsPerWeekForNewClient: 2,
      updatedAt: '2026-05-11T10:00:00',
    });
  });

  it('propagates HTTP errors to the observable', (done) => {
    service.getCurrent().subscribe({
      next: () => done.fail('should have errored'),
      error: (err) => {
        expect(err.status).toBe(500);
        done();
      },
    });
    http.expectOne('http://test/api/pro/booking-policy').flush('boom', { status: 500, statusText: 'err' });
  });
});
