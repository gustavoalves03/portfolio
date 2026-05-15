import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SubscriptionService } from './subscription.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import {
  CreateSubscriptionRequest,
  PricingPlan,
  SubscriptionResponse,
} from '../models/subscription.model';

describe('SubscriptionService', () => {
  const API = 'http://localhost:8080';
  let service: SubscriptionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: API },
      ],
    });
    service = TestBed.inject(SubscriptionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getPricing GETs /api/pricing and returns the plan list', () => {
    const plans: PricingPlan[] = [
      { tier: 'VITRINE', billing: 'FREE', monthlyPriceEuros: 0, currency: 'EUR' },
      { tier: 'GESTION', billing: 'MONTHLY', monthlyPriceEuros: 49.99, currency: 'EUR' },
    ];
    let received: PricingPlan[] | undefined;
    service.getPricing().subscribe(p => (received = p));

    const req = httpMock.expectOne(`${API}/api/pricing`);
    expect(req.request.method).toBe('GET');
    req.flush(plans);
    expect(received).toEqual(plans);
  });

  it('createSetupIntent POSTs an empty body and returns clientSecret', () => {
    let secret: string | undefined;
    service.createSetupIntent().subscribe(r => (secret = r.clientSecret));

    const req = httpMock.expectOne(`${API}/api/pro/subscription/setup-intent`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ clientSecret: 'seti_secret_abc' });
    expect(secret).toBe('seti_secret_abc');
  });

  it('createSubscription POSTs the payload and returns the subscription response', () => {
    const payload: CreateSubscriptionRequest = {
      tier: 'GESTION',
      billing: 'MONTHLY',
      paymentMethodId: 'pm_xyz',
    };
    const response: SubscriptionResponse = {
      tier: 'GESTION',
      billing: 'MONTHLY',
      status: 'TRIALING',
      stripeCustomerId: 'cus_1',
      stripeSubscriptionId: 'sub_1',
      currentPeriodEnd: null,
      trialEnd: '2026-05-22T00:00:00',
    };
    let received: SubscriptionResponse | undefined;
    service.createSubscription(payload).subscribe(r => (received = r));

    const req = httpMock.expectOne(`${API}/api/pro/subscription/create`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(response);
    expect(received).toEqual(response);
  });

  it('getCurrentSubscription GETs /api/pro/subscription', () => {
    let received: SubscriptionResponse | undefined;
    service.getCurrentSubscription().subscribe(r => (received = r));

    const req = httpMock.expectOne(`${API}/api/pro/subscription`);
    expect(req.request.method).toBe('GET');
    req.flush({
      tier: 'VITRINE',
      billing: 'FREE',
      status: 'VITRINE_FREE',
      stripeCustomerId: null,
      stripeSubscriptionId: null,
      currentPeriodEnd: null,
      trialEnd: null,
    });
    expect(received?.status).toBe('VITRINE_FREE');
  });

  it('createPortalSession POSTs and returns the portal url', () => {
    let url: string | undefined;
    service.createPortalSession().subscribe(r => (url = r.url));

    const req = httpMock.expectOne(`${API}/api/pro/subscription/portal-session`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ url: 'https://billing.stripe.com/p/session/abc' });
    expect(url).toBe('https://billing.stripe.com/p/session/abc');
  });
});
