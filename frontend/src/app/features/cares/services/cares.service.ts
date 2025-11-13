import { Injectable, inject } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BaseCrudService } from '../../../core/data/base-crud.service';
import { Care, CreateCareRequest, UpdateCareRequest } from '../models/cares.model';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

@Injectable({ providedIn: 'root' })
export class CaresService extends BaseCrudService<Care, CreateCareRequest, UpdateCareRequest> {
  protected readonly basePath = '/api/care';
  private apiBaseUrl = inject(API_BASE_URL);

  override list(): Observable<Care[]> {
    return super.list().pipe(
      map(cares => cares.map(care => this.transformCareImageUrls(care)))
    );
  }

  override get(id: number): Observable<Care> {
    return super.get(id).pipe(
      map(care => this.transformCareImageUrls(care))
    );
  }

  private transformCareImageUrls(care: Care): Care {
    if (!care.images || care.images.length === 0) {
      return care;
    }

    return {
      ...care,
      images: care.images.map(img => {
        const originalUrl = img.url;
        // If URL starts with 'data:' (Data URL) or 'http' (already absolute), keep it
        // Otherwise, prepend API base URL
        const transformedUrl = originalUrl.startsWith('data:') || originalUrl.startsWith('http')
          ? originalUrl
          : `${this.apiBaseUrl}${originalUrl}`;

        console.log(`[CaresService] Transform image URL: "${originalUrl}" -> "${transformedUrl}"`);

        return {
          ...img,
          url: transformedUrl
        };
      })
    };
  }
}
