import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  CreateSubscriptionRequest,
  PortalSessionResponse,
  PricingPlan,
  SetupIntentResponse,
  StripeConfigResponse,
  SubscriptionResponse,
} from '../models/subscription.model';

@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  getStripeConfig(): Observable<StripeConfigResponse> {
    return this.http.get<StripeConfigResponse>(`${this.apiBaseUrl}/api/stripe/config`);
  }

  getPricing(): Observable<PricingPlan[]> {
    return this.http.get<PricingPlan[]>(`${this.apiBaseUrl}/api/pricing`);
  }

  createSetupIntent(): Observable<SetupIntentResponse> {
    return this.http.post<SetupIntentResponse>(
      `${this.apiBaseUrl}/api/pro/subscription/setup-intent`,
      {},
    );
  }

  createSubscription(payload: CreateSubscriptionRequest): Observable<SubscriptionResponse> {
    return this.http.post<SubscriptionResponse>(
      `${this.apiBaseUrl}/api/pro/subscription/create`,
      payload,
    );
  }

  getCurrentSubscription(): Observable<SubscriptionResponse> {
    return this.http.get<SubscriptionResponse>(`${this.apiBaseUrl}/api/pro/subscription`);
  }

  createPortalSession(): Observable<PortalSessionResponse> {
    return this.http.post<PortalSessionResponse>(
      `${this.apiBaseUrl}/api/pro/subscription/portal-session`,
      {},
    );
  }
}
