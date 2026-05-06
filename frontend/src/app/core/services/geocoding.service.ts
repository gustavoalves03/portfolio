import { Injectable } from '@angular/core';

export interface GeocodeResult {
  readonly lat: number;
  readonly lng: number;
}

/**
 * Wrapper around OpenStreetMap Nominatim with an in-memory cache keyed by
 * the query string. Resolves to null when no result is found OR when the
 * request fails — null results are cached to avoid retrying obviously
 * unreachable addresses.
 *
 * Root-scoped so the cache is shared across the app (home page and salon
 * carousel both use it for the same addresses on a given session).
 */
@Injectable({ providedIn: 'root' })
export class GeocodingService {
  private readonly cache = new Map<string, GeocodeResult | null>();
  private static readonly NOMINATIM = 'https://nominatim.openstreetmap.org/search';

  async geocode(address: string): Promise<GeocodeResult | null> {
    if (this.cache.has(address)) {
      return this.cache.get(address)!;
    }
    const url = `${GeocodingService.NOMINATIM}?format=json&q=${encodeURIComponent(address)}&limit=1`;
    try {
      const res = await fetch(url);
      const data = (await res.json()) as Array<{ lat: string; lon: string }>;
      if (data.length > 0) {
        const result: GeocodeResult = { lat: parseFloat(data[0].lat), lng: parseFloat(data[0].lon) };
        this.cache.set(address, result);
        return result;
      }
    } catch {
      // fall through to null
    }
    this.cache.set(address, null);
    return null;
  }
}
