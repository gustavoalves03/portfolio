import { Injectable, inject } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BaseCrudService } from '../../../core/data/base-crud.service';
import { Care, CreateCareRequest, UpdateCareRequest } from '../models/cares.model';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { Page } from '../../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class CaresService extends BaseCrudService<Care, CreateCareRequest, UpdateCareRequest> {
  protected readonly basePath = '/api/care';
  protected override apiBaseUrl = inject(API_BASE_URL);

  override list(params?: { page?: number; size?: number; sort?: string }): Observable<Page<Care>> {
    return super.list(params).pipe(
      map(page => ({
        ...page,
        content: page.content.map(care => this.transformCareImageUrls(care))
      }))
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
