import { HttpClient, withInterceptors, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { featureFlagInterceptor } from './feature-flag.interceptor';

describe('featureFlagInterceptor', () => {
  let routerSpy: jasmine.SpyObj<Router>;
  let snackSpy: jasmine.SpyObj<MatSnackBar>;
  let translocoSpy: jasmine.SpyObj<TranslocoService>;
  let http: HttpClient;
  let httpCtrl: HttpTestingController;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    snackSpy = jasmine.createSpyObj('MatSnackBar', ['open']);
    translocoSpy = jasmine.createSpyObj('TranslocoService', ['translate']);
    translocoSpy.translate.and.returnValue('translated');

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withInterceptors([featureFlagInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
        { provide: MatSnackBar, useValue: snackSpy },
        { provide: TranslocoService, useValue: translocoSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpCtrl = TestBed.inject(HttpTestingController);
  });

  it('on 403 FEATURE_DISABLED → opens snackbar and navigates to /pricing', (done) => {
    http.get('/api/test').subscribe({
      error: () => {
        expect(routerSpy.navigate).toHaveBeenCalledWith(['/pricing'], { queryParams: { highlight: 'SHOP' } });
        expect(snackSpy.open).toHaveBeenCalled();
        expect(translocoSpy.translate).toHaveBeenCalledWith('errors.features.disabled', { tier: 'PREMIUM' });
        done();
      },
    });
    httpCtrl.expectOne('/api/test').flush(
      { error: 'FEATURE_DISABLED', featureKey: 'SHOP', minimumTier: 'PREMIUM' },
      { status: 403, statusText: 'Forbidden' },
    );
  });

  it('on 403 without FEATURE_DISABLED body → passes through unchanged', (done) => {
    http.get('/api/test').subscribe({
      error: () => {
        expect(routerSpy.navigate).not.toHaveBeenCalled();
        expect(snackSpy.open).not.toHaveBeenCalled();
        done();
      },
    });
    httpCtrl.expectOne('/api/test').flush(
      { error: 'OTHER_REASON' },
      { status: 403, statusText: 'Forbidden' },
    );
  });

  it('on 200 → no-op', (done) => {
    http.get('/api/test').subscribe({
      next: () => {
        expect(routerSpy.navigate).not.toHaveBeenCalled();
        done();
      },
    });
    httpCtrl.expectOne('/api/test').flush({});
  });
});
