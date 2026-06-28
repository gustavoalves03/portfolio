import { HttpClient, withInterceptors, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { featureFlagInterceptor } from './feature-flag.interceptor';

describe('featureFlagInterceptor', () => {
  let http: HttpClient;
  let httpCtrl: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withInterceptors([featureFlagInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpCtrl = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpCtrl.verify());

  // Feature gating is enforced visually (lp-feature-locked overlay), not by the
  // interceptor. A FEATURE_DISABLED 403 must propagate untouched — no redirect.
  it('on 403 FEATURE_DISABLED → propagates the error unchanged (no redirect)', (done) => {
    http.get('/api/test').subscribe({
      error: (err) => {
        expect(err.status).toBe(403);
        expect(err.error.error).toBe('FEATURE_DISABLED');
        done();
      },
    });
    httpCtrl.expectOne('/api/test').flush(
      { error: 'FEATURE_DISABLED', featureKey: 'SHOP', minimumTier: 'PREMIUM' },
      { status: 403, statusText: 'Forbidden' },
    );
  });

  it('on 200 → passes through', (done) => {
    http.get('/api/test').subscribe({
      next: (body) => {
        expect(body).toEqual({ ok: true });
        done();
      },
    });
    httpCtrl.expectOne('/api/test').flush({ ok: true });
  });
});
