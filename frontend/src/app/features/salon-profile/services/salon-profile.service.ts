import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { TenantResponse, UpdateTenantRequest, PublicSalonResponse, TimeSlot, ClientBookingRequest, ClientBookingResponse, EmployeeSlim } from '../models/salon-profile.model';

@Injectable({ providedIn: 'root' })
export class SalonProfileService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  private get baseUrl(): string {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return base;
  }

  getProfile(): Observable<TenantResponse> {
    return this.http.get<TenantResponse>(`${this.baseUrl}/api/pro/tenant`).pipe(
      map(tenant => this.transformLogoUrl(tenant))
    );
  }

  updateProfile(request: UpdateTenantRequest): Observable<TenantResponse> {
    return this.http.put<TenantResponse>(`${this.baseUrl}/api/pro/tenant`, request).pipe(
      map(tenant => this.transformLogoUrl(tenant))
    );
  }

  getAvailableSlots(slug: string, careId: number, date: string): Observable<TimeSlot[]> {
    return this.http.get<TimeSlot[]>(
      `${this.baseUrl}/api/salon/${slug}/available-slots`,
      { params: { careId: careId.toString(), date } }
    );
  }

  getPublicSalon(slug: string): Observable<PublicSalonResponse> {
    return this.http.get<PublicSalonResponse>(`${this.baseUrl}/api/salon/${slug}`).pipe(
      map(salon => this.transformPublicUrls(salon))
    );
  }

  getEmployeesForCare(slug: string, careId: number): Observable<EmployeeSlim[]> {
    return this.http.get<EmployeeSlim[]>(
      `${this.baseUrl}/api/salon/${slug}/employees`,
      { params: { careId: careId.toString() } }
    );
  }

  createBooking(slug: string, request: ClientBookingRequest): Observable<ClientBookingResponse> {
    return this.http.post<ClientBookingResponse>(
      `${this.baseUrl}/api/salon/${slug}/book`, request
    );
  }

  private transformLogoUrl(tenant: TenantResponse): TenantResponse {
    if (!this.isBrowser) return tenant;
    const transformUrl = (u: string | null) =>
      u ? (u.startsWith('http') || u.startsWith('data:') ? u : `${this.baseUrl}${u}`) : null;
    return {
      ...tenant,
      logoUrl: transformUrl(tenant.logoUrl),
      heroImageUrl: transformUrl(tenant.heroImageUrl),
    };
  }

  private transformPublicUrls(salon: PublicSalonResponse): PublicSalonResponse {
    if (!this.isBrowser) return salon;
    return {
      ...salon,
      logoUrl: salon.logoUrl ? (salon.logoUrl.startsWith('http') ? salon.logoUrl : `${this.baseUrl}${salon.logoUrl}`) : null,
      heroImageUrl: salon.heroImageUrl ? (salon.heroImageUrl.startsWith('http') ? salon.heroImageUrl : `${this.baseUrl}${salon.heroImageUrl}`) : null,
      categories: salon.categories.map(cat => ({
        ...cat,
        cares: cat.cares.map(care => ({
          ...care,
          imageUrls: care.imageUrls.map(url =>
            url.startsWith('http') ? url : `${this.baseUrl}${url}`
          )
        }))
      }))
    };
  }
}
