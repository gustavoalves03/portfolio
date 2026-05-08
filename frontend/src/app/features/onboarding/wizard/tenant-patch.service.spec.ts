import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TenantPatchService } from './tenant-patch.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

describe('TenantPatchService', () => {
  let service: TenantPatchService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://test' },
      ],
    });
    service = TestBed.inject(TenantPatchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('PATCHes only the fields provided', () => {
    service.patch({ name: 'Belle' }).subscribe();
    const req = http.expectOne('http://test/api/pro/tenant');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ name: 'Belle' });
    req.flush({});
  });
});
