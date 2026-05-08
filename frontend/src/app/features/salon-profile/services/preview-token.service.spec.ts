import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PreviewTokenService } from './preview-token.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

describe('PreviewTokenService', () => {
  let service: PreviewTokenService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api' },
      ],
    });
    service = TestBed.inject(PreviewTokenService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('list issues GET /api/pro/salon/preview-tokens', () => {
    let result: any;
    service.list().subscribe((r) => (result = r));
    const req = httpTesting.expectOne('http://api/api/pro/salon/preview-tokens');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 1,
        token: 't',
        shareUrl: '/salon/demo?preview=t',
        createdAt: '2026-05-06T10:00',
        expiresAt: null,
        revokedAt: null,
      },
    ]);
    expect(result).toEqual([jasmine.objectContaining({ id: 1, token: 't' })]);
  });

  it('create issues POST /api/pro/salon/preview-tokens', () => {
    let result: any;
    service.create().subscribe((r) => (result = r));
    const req = httpTesting.expectOne('http://api/api/pro/salon/preview-tokens');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 5,
      token: 'abc',
      shareUrl: '/salon/demo?preview=abc',
      createdAt: '2026-05-06T10:00',
      expiresAt: null,
      revokedAt: null,
    });
    expect(result.token).toBe('abc');
  });

  it('revoke issues DELETE /api/pro/salon/preview-tokens/:id', () => {
    service.revoke(99).subscribe();
    const req = httpTesting.expectOne('http://api/api/pro/salon/preview-tokens/99');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
