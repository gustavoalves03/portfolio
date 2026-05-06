import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { GeocodingService } from './geocoding.service';

describe('GeocodingService', () => {
  let service: GeocodingService;
  let fetchSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(GeocodingService);
    fetchSpy = spyOn(window, 'fetch');
  });

  it('returns coords on first lookup and caches them', async () => {
    fetchSpy.and.resolveTo(new Response(JSON.stringify([{ lat: '48.85', lon: '2.35' }])));
    const first = await service.geocode('1 rue de Paris');
    expect(first).toEqual({ lat: 48.85, lng: 2.35 });
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('returns cached value on second lookup without re-fetching', async () => {
    fetchSpy.and.resolveTo(new Response(JSON.stringify([{ lat: '48.85', lon: '2.35' }])));
    await service.geocode('1 rue de Paris');
    await service.geocode('1 rue de Paris');
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('returns null and caches null when no result', async () => {
    fetchSpy.and.resolveTo(new Response(JSON.stringify([])));
    const result = await service.geocode('unknown');
    expect(result).toBeNull();
    await service.geocode('unknown');
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('returns null and caches null on network error', async () => {
    fetchSpy.and.rejectWith(new Error('boom'));
    const result = await service.geocode('1 rue de Paris');
    expect(result).toBeNull();
    await service.geocode('1 rue de Paris');
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});
