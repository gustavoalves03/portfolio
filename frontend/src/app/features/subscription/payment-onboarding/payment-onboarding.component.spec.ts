import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PLATFORM_ID } from '@angular/core';

import { PaymentOnboardingComponent } from './payment-onboarding.component';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { SubscriptionResponse } from '../models/subscription.model';

const API = 'http://localhost:8080';

const MOCK_TRANSLATIONS = {
  'paymentOnboarding.eyebrow': 'PAIEMENT',
  'paymentOnboarding.title': 'Votre essai gratuit commence.',
  'paymentOnboarding.subtitle': 'Aucun prélèvement avant 7 jours. Annulable à tout moment.',
  'paymentOnboarding.recap.gestionMonthly': 'Gestion · 49,99 €/mois',
  'paymentOnboarding.recap.gestionYearly': 'Gestion · 42,49 €/mois facturé à l\'année',
  'paymentOnboarding.recap.premiumMonthly': 'Premium · 67,99 €/mois',
  'paymentOnboarding.recap.premiumYearly': 'Premium · 57,79 €/mois facturé à l\'année',
  'paymentOnboarding.cardLabel': 'Informations de paiement',
  'paymentOnboarding.submit': 'Confirmer mon essai',
  'paymentOnboarding.submitting': 'Confirmation...',
  'paymentOnboarding.trust': 'Stripe gère le paiement de manière sécurisée.',
  'paymentOnboarding.errors.init': 'Impossible d\'initialiser le paiement. Réessayez.',
  'paymentOnboarding.errors.stripe': 'Carte refusée ou invalide.',
  'paymentOnboarding.errors.subscription': 'L\'abonnement n\'a pas pu être créé. Réessayez.',
  'paymentOnboarding.errors.generic': 'Une erreur est survenue.',
};

function buildFakeStripe() {
  const fakePaymentElement = {
    mount: jasmine.createSpy('mount'),
    unmount: jasmine.createSpy('unmount'),
  };

  const fakeElements = {
    create: jasmine.createSpy('create').and.returnValue(fakePaymentElement),
  };

  const fakeStripe = {
    elements: jasmine.createSpy('elements').and.returnValue(fakeElements),
    confirmSetup: jasmine.createSpy('confirmSetup'),
  };

  return { fakeStripe, fakeElements, fakePaymentElement };
}

function makeProviders(platformId: string) {
  return [
    provideZonelessChangeDetection(),
    provideHttpClient(),
    provideHttpClientTesting(),
    provideRouter([]),
    provideNoopAnimations(),
    { provide: API_BASE_URL, useValue: API },
    { provide: PLATFORM_ID, useValue: platformId },
    {
      provide: ActivatedRoute,
      useValue: {
        snapshot: {
          queryParamMap: convertToParamMap({ tier: 'GESTION', billing: 'YEARLY' }),
        },
      },
    },
  ];
}

describe('PaymentOnboardingComponent', () => {
  let fixture: ComponentFixture<PaymentOnboardingComponent>;
  let component: PaymentOnboardingComponent;
  let httpMock: HttpTestingController;

  // ── Test 1: Browser init — loads Stripe config + SetupIntent ──
  describe('browser platform', () => {
    let fakeStripeRef: ReturnType<typeof buildFakeStripe>;

    beforeEach(async () => {
      fakeStripeRef = buildFakeStripe();

      await TestBed.configureTestingModule({
        imports: [
          PaymentOnboardingComponent,
          TranslocoTestingModule.forRoot({
            langs: { fr: MOCK_TRANSLATIONS },
            translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          }),
        ],
        providers: makeProviders('browser'),
      }).compileComponents();

      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('loads Stripe config and SetupIntent on init in browser', async () => {
      fixture = TestBed.createComponent(PaymentOnboardingComponent);
      component = fixture.componentInstance;

      spyOn(component as any, 'loadStripeLib').and.returnValue(
        Promise.resolve(fakeStripeRef.fakeStripe),
      );
      // Spy on mountPaymentElement to avoid DOM dependency (cardElement rendered by @if)
      spyOn(component as any, 'mountPaymentElement').and.returnValue(Promise.resolve());

      fixture.detectChanges();

      // Flush both parallel requests
      const configReq = httpMock.expectOne(`${API}/api/stripe/config`);
      expect(configReq.request.method).toBe('GET');
      configReq.flush({ publishableKey: 'pk_test_x' });

      const intentReq = httpMock.expectOne(`${API}/api/pro/subscription/setup-intent`);
      expect(intentReq.request.method).toBe('POST');
      intentReq.flush({ clientSecret: 'cs_x' });

      await fixture.whenStable();
      fixture.detectChanges();

      expect((component as any).loadStripeLib).toHaveBeenCalledWith('pk_test_x');
      expect(fakeStripeRef.fakeStripe.elements).toHaveBeenCalledWith({ clientSecret: 'cs_x' });
      expect(fakeStripeRef.fakeElements.create).toHaveBeenCalledWith('payment');
      expect((component as any).mountPaymentElement).toHaveBeenCalled();
      expect(component.loading()).toBeFalse();
    });

    it('surfaces an error when SetupIntent fails', async () => {
      fixture = TestBed.createComponent(PaymentOnboardingComponent);
      component = fixture.componentInstance;

      spyOn(component as any, 'loadStripeLib').and.returnValue(
        Promise.resolve(fakeStripeRef.fakeStripe),
      );
      spyOn(component as any, 'mountPaymentElement').and.returnValue(Promise.resolve());

      fixture.detectChanges();

      // Config succeeds
      const configReq = httpMock.expectOne(`${API}/api/stripe/config`);
      configReq.flush({ publishableKey: 'pk_test_x' });

      // SetupIntent fails
      const intentReq = httpMock.expectOne(`${API}/api/pro/subscription/setup-intent`);
      intentReq.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.error()).toBeTruthy();
      expect(component.loading()).toBeFalse();
    });

    it('submit calls confirmSetup, then createSubscription, then navigates', async () => {
      fixture = TestBed.createComponent(PaymentOnboardingComponent);
      component = fixture.componentInstance;

      const { fakeStripe } = fakeStripeRef;
      spyOn(component as any, 'loadStripeLib').and.returnValue(Promise.resolve(fakeStripe));
      spyOn(component as any, 'mountPaymentElement').and.returnValue(Promise.resolve());

      fixture.detectChanges();

      // Init
      httpMock.expectOne(`${API}/api/stripe/config`).flush({ publishableKey: 'pk_test_x' });
      httpMock
        .expectOne(`${API}/api/pro/subscription/setup-intent`)
        .flush({ clientSecret: 'cs_x' });

      await fixture.whenStable();
      fixture.detectChanges();

      // Stub confirmSetup to return a successful setup intent
      fakeStripe.confirmSetup.and.returnValue(
        Promise.resolve({ setupIntent: { payment_method: 'pm_test' } }),
      );

      const router = TestBed.inject(Router);
      spyOn(router, 'navigate');

      // Call submit
      await component.submit();

      // Flush createSubscription POST
      const subReq = httpMock.expectOne(`${API}/api/pro/subscription/create`);
      expect(subReq.request.method).toBe('POST');
      expect(subReq.request.body).toEqual({
        tier: 'GESTION',
        billing: 'YEARLY',
        paymentMethodId: 'pm_test',
      });

      const subResponse: SubscriptionResponse = {
        tier: 'GESTION',
        billing: 'YEARLY',
        status: 'TRIALING',
        stripeCustomerId: 'cus_1',
        stripeSubscriptionId: 'sub_1',
        currentPeriodEnd: null,
        trialEnd: '2026-05-22T00:00:00',
      };
      subReq.flush(subResponse);

      await fixture.whenStable();

      expect(router.navigate).toHaveBeenCalledWith(
        ['/pro/dashboard'],
        { queryParams: { paymentSuccess: '1' } }
      );
    });

    it('surfaces error when Stripe confirmSetup returns an error', async () => {
      fixture = TestBed.createComponent(PaymentOnboardingComponent);
      component = fixture.componentInstance;

      const { fakeStripe } = fakeStripeRef;
      spyOn(component as any, 'loadStripeLib').and.returnValue(Promise.resolve(fakeStripe));
      spyOn(component as any, 'mountPaymentElement').and.returnValue(Promise.resolve());

      fixture.detectChanges();

      // Init
      httpMock.expectOne(`${API}/api/stripe/config`).flush({ publishableKey: 'pk_test_x' });
      httpMock
        .expectOne(`${API}/api/pro/subscription/setup-intent`)
        .flush({ clientSecret: 'cs_x' });

      await fixture.whenStable();
      fixture.detectChanges();

      // Stub confirmSetup to return an error
      fakeStripe.confirmSetup.and.returnValue(
        Promise.resolve({ error: { message: 'Card declined' } }),
      );

      await component.submit();

      fixture.detectChanges();

      expect(component.error()).toContain('Card declined');
      expect(component.submitting()).toBeFalse();

      // No POST to createSubscription should have been made
      httpMock.expectNone(`${API}/api/pro/subscription/create`);
    });
  });

  // ── Test 2: SSR platform — does NOT load Stripe ──
  describe('server platform (SSR)', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [
          PaymentOnboardingComponent,
          TranslocoTestingModule.forRoot({
            langs: { fr: MOCK_TRANSLATIONS },
            translocoConfig: { defaultLang: 'fr', availableLangs: ['fr'] },
          }),
        ],
        providers: makeProviders('server'),
      }).compileComponents();

      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('does not load Stripe on the server (SSR)', () => {
      fixture = TestBed.createComponent(PaymentOnboardingComponent);
      component = fixture.componentInstance;

      spyOn(component as any, 'loadStripeLib');

      fixture.detectChanges();

      // No HTTP requests should have been made
      httpMock.expectNone(`${API}/api/stripe/config`);
      httpMock.expectNone(`${API}/api/pro/subscription/setup-intent`);

      expect((component as any).loadStripeLib).not.toHaveBeenCalled();
      expect(component.loading()).toBeTrue();
    });
  });
});
