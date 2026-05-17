import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { PricingPageComponent } from './pricing-page.component';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { PricingPlan } from '../models/subscription.model';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { ProSignupModalComponent } from '../../../shared/modals/pro-signup-modal/pro-signup-modal.component';

const API = 'http://localhost:8080';

const MOCK_TRANSLATIONS = {
  'pricing.hero.eyebrow': 'Tarifs',
  'pricing.hero.title': 'Choisissez le rythme qui vous ressemble',
  'pricing.hero.subtitle': 'Essai gratuit 7 jours. Sans carte demandée.',
  'pricing.toggle.monthly': 'Mensuel',
  'pricing.toggle.yearly': 'Annuel',
  'pricing.toggle.discount': '−15%',
  'pricing.featured.tagline': "Tout ce qu'il faut pour gérer un salon serein.",
  'pricing.featured.cta': 'Démarrer 7 jours gratuits',
  'pricing.featured.noCard': 'Aucune carte requise.',
  'pricing.featured.pills.booking': 'Réservation en ligne',
  'pricing.featured.pills.agenda': 'Agenda multi-praticien',
  'pricing.featured.pills.clients': 'Fiches clients',
  'pricing.featured.pills.stats': 'Statistiques',
  'pricing.featured.pills.sms': 'Rappels SMS',
  'pricing.featured.pills.support': 'Support email',
  'pricing.divider': 'Comparer toutes les offres',
  'pricing.table.feature': 'Fonctionnalité',
  'pricing.table.groups.presence': 'Présence',
  'pricing.table.groups.booking': 'Réservation',
  'pricing.table.groups.clients': 'Clients',
  'pricing.table.groups.sales': 'Vente',
  'pricing.table.groups.team': 'Équipe',
  'pricing.table.features.publicPage': 'Fiche salon publique',
  'pricing.table.features.photos': 'Photos illimitées',
  'pricing.table.features.discoveryVisibility': 'Visibilité Discovery',
  'pricing.table.features.onlineBooking': 'Réservation en ligne',
  'pricing.table.features.multiPractitioner': 'Agenda multi-praticien',
  'pricing.table.features.smsReminders': 'Rappels SMS clients',
  'pricing.table.features.absences': 'Gestion des absences',
  'pricing.table.features.clientFiles': 'Fiches clients',
  'pricing.table.features.history': 'Historique & notes',
  'pricing.table.features.loyalty': 'Programme fidélité',
  'pricing.table.features.gdpr': 'Suivi RGPD',
  'pricing.table.features.onlinePayment': 'Paiement en ligne',
  'pricing.table.features.shop': 'Boutique produits',
  'pricing.table.features.autoInvoices': 'Factures auto',
  'pricing.table.features.multiPractitionerSeats': 'Multi-praticien·nes',
  'pricing.table.features.multiLocations': 'Multi-localisations',
  'pricing.table.values.yes': 'Oui',
  'pricing.table.values.no': 'Non',
  'pricing.table.values.unlimited': 'illimité',
  'pricing.table.values.priority': 'prioritaire',
  'pricing.tiers.vitrine.name': 'Vitrine',
  'pricing.tiers.vitrine.free': '0 €/mois',
  'pricing.tiers.vitrine.cta': 'Choisir Vitrine',
  'pricing.tiers.gestion.name': 'Gestion',
  'pricing.tiers.gestion.cta': 'Démarrer 7j gratuits',
  'pricing.tiers.gestion.annualNote': 'facturé annuellement',
  'pricing.tiers.premium.name': 'Premium',
  'pricing.tiers.premium.cta': 'Démarrer 7j gratuits',
  'pricing.tiers.premium.annualNote': 'facturé annuellement',
  'pricing.reassurance.title1': 'Annulable en 1 clic',
  'pricing.reassurance.body1': 'Depuis votre espace, sans appel.',
  'pricing.reassurance.title2': 'Données françaises & RGPD',
  'pricing.reassurance.body2': 'Hébergement en France, conformité totale.',
  'pricing.reassurance.title3': 'Support en français',
  'pricing.reassurance.body3': 'Réponse sous 24h ouvrées.',
};

const STUB_PLANS: PricingPlan[] = [
  { tier: 'VITRINE', billing: 'FREE', monthlyPriceEuros: 0, currency: 'EUR' },
  { tier: 'GESTION', billing: 'MONTHLY', monthlyPriceEuros: 49.99, currency: 'EUR' },
  { tier: 'GESTION', billing: 'YEARLY', monthlyPriceEuros: 42.49, currency: 'EUR' },
  { tier: 'PREMIUM', billing: 'MONTHLY', monthlyPriceEuros: 67.99, currency: 'EUR' },
  { tier: 'PREMIUM', billing: 'YEARLY', monthlyPriceEuros: 57.79, currency: 'EUR' },
];

describe('PricingPageComponent', () => {
  let httpMock: HttpTestingController;

  function setup(): { fixture: ComponentFixture<PricingPageComponent>; component: PricingPageComponent } {
    const fixture = TestBed.createComponent(PricingPageComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges(); // triggers ngOnInit → GET /api/pricing
    return { fixture, component };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        PricingPageComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: MOCK_TRANSLATIONS },
          translocoConfig: { defaultLang: 'en', availableLangs: ['en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: API_BASE_URL, useValue: API },
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', ['user', 'upgradeToPro']) },
        { provide: MatDialog, useValue: jasmine.createSpyObj('MatDialog', ['open']) },
        { provide: MatSnackBar, useValue: jasmine.createSpyObj('MatSnackBar', ['open']) },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── Test 1: renders 3 tier names on init ──
  it('renders Vitrine, Gestion and Premium tier names on init', () => {
    const { fixture } = setup();

    const req = httpMock.expectOne(`${API}/api/pricing`);
    req.flush(STUB_PLANS);
    fixture.detectChanges();

    const html: string = fixture.nativeElement.innerHTML;
    expect(html).toContain('Vitrine');
    expect(html).toContain('Gestion');
    expect(html).toContain('Premium');
  });

  // ── Test 2: defaults to YEARLY billing ──
  it('defaults to YEARLY billing and shows the yearly Gestion price', () => {
    const { fixture, component } = setup();

    const req = httpMock.expectOne(`${API}/api/pricing`);
    req.flush(STUB_PLANS);
    fixture.detectChanges();

    expect(component.billing()).toBe('YEARLY');
    expect(component.gestionPrice()).toBeCloseTo(42.49, 2);
  });

  // ── Test 3: toggling to MONTHLY swaps prices ──
  it('toggling to MONTHLY swaps Gestion price to the monthly value', () => {
    const { fixture, component } = setup();

    const req = httpMock.expectOne(`${API}/api/pricing`);
    req.flush(STUB_PLANS);
    fixture.detectChanges();

    // Initially YEARLY → 42.49
    expect(component.gestionPrice()).toBeCloseTo(42.49, 2);

    // Switch to MONTHLY
    component.billing.set('MONTHLY');
    fixture.detectChanges();

    expect(component.gestionPrice()).toBeCloseTo(49.99, 2);
    expect(component.premiumPrice()).toBeCloseTo(67.99, 2);
  });

  // ── Test 4: fetches plans from SubscriptionService on init ──
  it('fetches plans from /api/pricing on init and updates computed prices', () => {
    const { fixture, component } = setup();

    expect(component.loading()).toBeTrue();

    const req = httpMock.expectOne(`${API}/api/pricing`);
    expect(req.request.method).toBe('GET');
    req.flush(STUB_PLANS);
    fixture.detectChanges();

    expect(component.loading()).toBeFalse();
    expect(component.plans().length).toBe(5);
    expect(component.gestionPrice()).toBeCloseTo(42.49, 2);
    expect(component.premiumPrice()).toBeCloseTo(57.79, 2);
  });

  // ── Test 5: falls back to spec defaults when /api/pricing fails ──
  it('falls back to spec defaults when /api/pricing returns an error', () => {
    const { fixture, component } = setup();

    spyOn(console, 'error');

    const req = httpMock.expectOne(`${API}/api/pricing`);
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.loading()).toBeFalse();
    expect(component.plans().length).toBe(0);

    // Fallback spec defaults: GESTION/YEARLY = 42.49, PREMIUM/YEARLY = 57.79
    expect(component.gestionPrice()).toBeCloseTo(42.49, 2);
    expect(component.premiumPrice()).toBeCloseTo(57.79, 2);

    // MONTHLY fallbacks: 49.99 / 67.99
    component.billing.set('MONTHLY');
    expect(component.gestionPrice()).toBeCloseTo(49.99, 2);
    expect(component.premiumPrice()).toBeCloseTo(67.99, 2);

    fixture.detectChanges();
    expect(fixture.nativeElement.innerHTML).toBeTruthy();
  });

  describe('onStartTier', () => {
    let authService: jasmine.SpyObj<AuthService>;
    let dialog: jasmine.SpyObj<MatDialog>;
    let router: Router;
    let component: PricingPageComponent;

    beforeEach(() => {
      authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
      dialog = TestBed.inject(MatDialog) as jasmine.SpyObj<MatDialog>;
      router = TestBed.inject(Router);
      // Set up component; flush the /api/pricing HTTP request so tests are clean
      const { component: c, fixture } = setup();
      component = c;
      httpMock.expectOne(`${API}/api/pricing`).flush([]);
      fixture.detectChanges();
    });

    it('opens ProSignupModal when user is not authenticated', () => {
      authService.user.and.returnValue(null);
      const dialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
      dialogRef.afterClosed.and.returnValue(of({ authenticated: true }));
      dialog.open.and.returnValue(dialogRef);
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('GESTION');

      expect(dialog.open).toHaveBeenCalledWith(ProSignupModalComponent, jasmine.objectContaining({
        data: jasmine.objectContaining({ tier: 'GESTION' }),
      }));
      expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
    });

    it('does not navigate when modal closes without authentication', () => {
      authService.user.and.returnValue(null);
      const dialogRef = jasmine.createSpyObj('MatDialogRef', ['afterClosed']);
      dialogRef.afterClosed.and.returnValue(of({ authenticated: false }));
      dialog.open.and.returnValue(dialogRef);
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('GESTION');

      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('navigates directly to /pro/dashboard when user has PRO role', () => {
      authService.user.and.returnValue({ id: 1, roles: [Role.PRO] } as any);
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('PREMIUM');

      expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
      expect(dialog.open).not.toHaveBeenCalled();
    });

    it('calls upgradeToPro and navigates when user is a client (no PRO role)', () => {
      authService.user.and.returnValue({ id: 1, roles: [] } as any);
      authService.upgradeToPro.and.returnValue(of({ id: 1, roles: [Role.PRO] } as any));
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('VITRINE');

      expect(authService.upgradeToPro).toHaveBeenCalledWith({ tier: 'VITRINE', billing: jasmine.any(String) });
      expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
    });

    it('redirects to dashboard on 409 (user already has tenant)', () => {
      authService.user.and.returnValue({ id: 1, roles: [] } as any);
      authService.upgradeToPro.and.returnValue(throwError(() => ({ status: 409 })));
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      component.onStartTier('GESTION');

      expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
    });
  });
});
