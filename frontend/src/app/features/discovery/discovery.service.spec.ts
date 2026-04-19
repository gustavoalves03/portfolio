import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, PLATFORM_ID } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { DiscoveryService } from './discovery.service';
import { SalonCard } from './discovery.model';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

describe('DiscoveryService', () => {
  const API = 'http://localhost:8080';
  let service: DiscoveryService;
  let httpMock: HttpTestingController;

  function makeSalon(overrides: Partial<SalonCard> = {}): SalonCard {
    return {
      name: 'Atelier Lumière',
      slug: 'atelier-lumiere',
      description: 'Soins',
      logoUrl: null,
      categoryNames: null,
      addressCity: 'Paris',
      fullAddress: '1 rue X, 75001 Paris',
      ...overrides,
    };
  }

  function configure(platform: 'browser' | 'server') {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: API },
        { provide: PLATFORM_ID, useValue: platform },
      ],
    });
    service = TestBed.inject(DiscoveryService);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('GETs /api/public/salons with no params when none provided', () => {
    configure('browser');
    const result: SalonCard[] = [];
    service.searchSalons().subscribe((v) => (result.push(...v)));

    const req = httpMock.expectOne((r) => r.url === `${API}/api/public/salons`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([makeSalon()]);
    expect(result.length).toBe(1);
  });

  it('sends category and q params when provided', () => {
    configure('browser');
    service.searchSalons('ongles', 'lumiere').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${API}/api/public/salons`);
    expect(req.request.params.get('category')).toBe('ongles');
    expect(req.request.params.get('q')).toBe('lumiere');
    req.flush([]);
  });

  it('omits null or empty params', () => {
    configure('browser');
    service.searchSalons(null, '').subscribe();

    const req = httpMock.expectOne((r) => r.url === `${API}/api/public/salons`);
    expect(req.request.params.has('category')).toBeFalse();
    expect(req.request.params.has('q')).toBeFalse();
    req.flush([]);
  });

  it('prefixes relative logoUrl with API base on browser', (done) => {
    configure('browser');
    service.searchSalons().subscribe((salons) => {
      expect(salons[0].logoUrl).toBe(`${API}/uploads/logo.png`);
      done();
    });
    httpMock.expectOne(`${API}/api/public/salons`).flush([
      makeSalon({ logoUrl: '/uploads/logo.png' }),
    ]);
  });

  it('leaves absolute http logoUrls untouched', (done) => {
    configure('browser');
    service.searchSalons().subscribe((salons) => {
      expect(salons[0].logoUrl).toBe('https://cdn.example/logo.png');
      done();
    });
    httpMock.expectOne(`${API}/api/public/salons`).flush([
      makeSalon({ logoUrl: 'https://cdn.example/logo.png' }),
    ]);
  });

  it('leaves logoUrl untouched on the server (SSR) platform', (done) => {
    configure('server');
    service.searchSalons().subscribe((salons) => {
      expect(salons[0].logoUrl).toBe('/uploads/logo.png');
      done();
    });
    httpMock.expectOne(`${API}/api/public/salons`).flush([
      makeSalon({ logoUrl: '/uploads/logo.png' }),
    ]);
  });
});
