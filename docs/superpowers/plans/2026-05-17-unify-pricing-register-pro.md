# Unify /pricing + /register/pro Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fusionner `/pricing` et `/register/pro` en une page unique d'inscription pro sans CB, et déporter la demande de paiement Stripe au clic "Publier" du dashboard pro.

**Architecture:** Refonte frontend de `RegisterProComponent` en page unique (grille tarifs intégrée + formulaire), suppression de `PricingPageComponent`, redirect `/pricing` → `/register/pro`. Backend : `ProRegisterRequest` accepte `tier` + `billing` typés. Dashboard pro : interception du clic Publier pour rediriger vers `/pro/onboarding/payment` si subscription absente. **Important** : le tier `VITRINE` reste côté backend (default tenant, status `VITRINE_FREE`, PricingCatalog) — seul le frontend cache VITRINE de l'UI publique. Refactor backend complet de VITRINE = chantier séparé hors scope.

**Tech Stack:** Angular 20 (standalone, signals), Spring Boot 3.5 (Java 21), Jasmine/Karma, JUnit 5

---

## File Structure

**Frontend — Modifier :**
- `frontend/src/app/app.routes.ts` — route `/pricing` devient redirect
- `frontend/src/app/pages/auth/register-pro/register-pro.component.ts` — refonte 1 page
- `frontend/src/app/pages/auth/register-pro/register-pro.component.html` — refonte UI
- `frontend/src/app/pages/auth/register-pro/register-pro.component.scss` — styles adaptés
- `frontend/src/app/pages/auth/register-pro/register-pro.component.spec.ts` — tests réécrits
- `frontend/src/app/pages/home/home.ts:160` — supprimer navigation vers `/pricing`
- `frontend/src/app/shared/layout/footer/footer.html` — liens `/pricing` → `/register/pro`
- `frontend/src/app/shared/layout/navigation/navigation-routes.ts:128` — vérifier
- `frontend/src/app/core/auth/auth.service.ts` — signature `registerPro({...tier, billing})`
- `frontend/src/app/pages/pro/pro-dashboard.component.ts` — gate paiement avant publish
- `frontend/src/app/pages/pro/pro-dashboard.component.spec.ts` — test gate paiement
- `frontend/src/assets/i18n/fr.json` + `en.json` — clés `register.pro.*`, suppression `pricing.*`

**Frontend — Supprimer :**
- `frontend/src/app/features/subscription/pricing/` (tout le dossier)

**Backend — Modifier :**
- `backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java` — ajouter `tier` + `billing`
- `backend/src/main/java/com/luxpretty/app/auth/AuthController.java:128` — persister `tier` + `billing` sur tenant
- `backend/src/test/java/com/luxpretty/app/auth/AuthControllerTests.java` (si existe) — adapter

---

## PR1 — Frontend : page unifiée + suppression VITRINE de l'UI

### Task 1 : Vérifier l'état initial (tests verts, pas de modifs)

**Files:** aucun

- [ ] **Step 1 : Lancer les tests frontend pour baseline**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: tous les tests passent (état initial sain)

- [ ] **Step 2 : Vérifier git status propre**

Run: `git status`
Expected: working tree clean (sauf modifs CI déjà présentes en M)

---

### Task 2 : Écrire les tests du nouveau `RegisterProComponent` (1 page, tier intégré)

**Files:**
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.spec.ts` (réécriture complète)

- [ ] **Step 1 : Réécrire le fichier de spec**

Remplacer **tout** le contenu de `register-pro.component.spec.ts` par :

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { HttpErrorResponse } from '@angular/common/http';

import { RegisterProComponent } from './register-pro.component';
import { AuthService } from '../../../core/auth/auth.service';

describe('RegisterProComponent', () => {
  let fixture: ComponentFixture<RegisterProComponent>;
  let component: RegisterProComponent;
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['registerPro']);

    await TestBed.configureTestingModule({
      imports: [
        RegisterProComponent,
        TranslocoTestingModule.forRoot({ langs: { fr: {} }, translocoConfig: { defaultLang: 'fr' } }),
      ],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterProComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('defaults to GESTION tier and YEARLY billing', () => {
    expect(component.selectedTier()).toBe('GESTION');
    expect(component.billing()).toBe('YEARLY');
  });

  it('selectTier updates selectedTier signal', () => {
    component.selectTier('PREMIUM');
    expect(component.selectedTier()).toBe('PREMIUM');
  });

  it('toggling billing updates billing signal', () => {
    component.billing.set('MONTHLY');
    expect(component.billing()).toBe('MONTHLY');
  });

  it('isFormValid returns false when fields are empty', () => {
    expect(component.isFormValid()).toBe(false);
  });

  it('isFormValid returns true when all required fields are filled', () => {
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.confirmPassword.set('password123');
    component.salonName.set('My Salon');
    component.consent.set(true);
    expect(component.isFormValid()).toBe(true);
  });

  it('isFormValid returns false when passwords do not match', () => {
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.confirmPassword.set('different');
    component.salonName.set('My Salon');
    component.consent.set(true);
    expect(component.isFormValid()).toBe(false);
  });

  it('submit calls authService.registerPro with tier+billing and navigates on success', () => {
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    authService.registerPro.and.returnValue(of({ id: 1, name: 'Alice', email: 'a@b.com' } as any));

    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.confirmPassword.set('password123');
    component.salonName.set('My Salon');
    component.consent.set(true);
    component.selectTier('PREMIUM');
    component.billing.set('MONTHLY');

    component.submit();

    expect(authService.registerPro).toHaveBeenCalledWith(jasmine.objectContaining({
      name: 'Alice',
      email: 'a@b.com',
      password: 'password123',
      salonName: 'My Salon',
      tier: 'PREMIUM',
      billing: 'MONTHLY',
    }));
    expect(router.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
  });

  it('submit does nothing when form invalid', () => {
    component.submit();
    expect(authService.registerPro).not.toHaveBeenCalled();
  });

  it('submit sets emailConflict error on 409', () => {
    authService.registerPro.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409 }))
    );

    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.confirmPassword.set('password123');
    component.salonName.set('My Salon');
    component.consent.set(true);

    component.submit();

    expect(component.error()).toBe('register.errors.emailConflict');
    expect(component.isLoading()).toBe(false);
  });

  it('re-entrancy guard: second submit while loading does nothing', () => {
    authService.registerPro.and.returnValue(of({ id: 1 } as any));
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.password.set('password123');
    component.confirmPassword.set('password123');
    component.salonName.set('My Salon');
    component.consent.set(true);

    component.isLoading.set(true);
    component.submit();

    expect(authService.registerPro).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2 : Lancer les tests pour vérifier qu'ils échouent (rouge)**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/register-pro.component.spec.ts'`
Expected: ÉCHEC (`component.selectedTier is not a function`, `component.billing is not a function`, etc.)

---

### Task 3 : Refonte du `RegisterProComponent` (TS)

**Files:**
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.ts`

- [ ] **Step 1 : Remplacer le contenu du composant**

Remplacer **tout** le fichier par :

```typescript
import { Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { SubscriptionBilling, SubscriptionTier } from '../../../features/subscription/models/subscription.model';

type PayableTier = Extract<SubscriptionTier, 'GESTION' | 'PREMIUM'>;

interface TierCard {
  id: PayableTier;
  nameKey: string;
  taglineKey: string;
  monthlyPrice: number;
  yearlyPrice: number;
  featuresKeys: string[];
  highlighted: boolean;
  badgeKey?: string;
}

@Component({
  selector: 'app-register-pro',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatCheckboxModule, MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  templateUrl: './register-pro.component.html',
  styleUrl: './register-pro.component.scss',
})
export class RegisterProComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly selectedTier = signal<PayableTier>('GESTION');
  readonly billing = signal<SubscriptionBilling>('YEARLY');
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);

  readonly name = signal('');
  readonly email = signal('');
  readonly password = signal('');
  readonly confirmPassword = signal('');
  readonly salonName = signal('');
  readonly phone = signal('');
  readonly addressStreet = signal('');
  readonly addressPostalCode = signal('');
  readonly addressCity = signal('');
  readonly siret = signal('');
  readonly consent = signal(false);

  readonly passwordsMatch = computed(() => {
    const p = this.password();
    const c = this.confirmPassword();
    if (!p || !c) return true;
    return p === c;
  });

  readonly tiers: TierCard[] = [
    {
      id: 'GESTION',
      nameKey: 'register.pro.tiers.gestion.name',
      taglineKey: 'register.pro.tiers.gestion.tagline',
      monthlyPrice: 49.99,
      yearlyPrice: 42.49,
      featuresKeys: [
        'register.pro.tiers.gestion.features.booking',
        'register.pro.tiers.gestion.features.agenda',
        'register.pro.tiers.gestion.features.clients',
        'register.pro.tiers.gestion.features.stats',
      ],
      highlighted: true,
      badgeKey: 'register.pro.tiers.gestion.badge',
    },
    {
      id: 'PREMIUM',
      nameKey: 'register.pro.tiers.premium.name',
      taglineKey: 'register.pro.tiers.premium.tagline',
      monthlyPrice: 67.99,
      yearlyPrice: 57.79,
      featuresKeys: [
        'register.pro.tiers.premium.features.allGestion',
        'register.pro.tiers.premium.features.sms',
        'register.pro.tiers.premium.features.priority',
        'register.pro.tiers.premium.features.support',
      ],
      highlighted: false,
    },
  ];

  getPrice(tier: TierCard): number {
    return this.billing() === 'YEARLY' ? tier.yearlyPrice : tier.monthlyPrice;
  }

  selectTier(id: PayableTier): void {
    this.selectedTier.set(id);
  }

  isFormValid(): boolean {
    return this.name().trim().length > 0
      && this.email().includes('@')
      && this.password().length >= 8
      && this.passwordsMatch()
      && this.salonName().trim().length > 0
      && this.consent();
  }

  submit(): void {
    if (!this.isFormValid()) return;
    if (this.isLoading()) return;

    this.isLoading.set(true);
    this.error.set(null);

    this.authService.registerPro({
      name: this.name().trim(),
      email: this.email().trim(),
      password: this.password(),
      salonName: this.salonName().trim(),
      phone: this.phone().trim(),
      addressStreet: this.addressStreet().trim(),
      addressPostalCode: this.addressPostalCode().trim(),
      addressCity: this.addressCity().trim(),
      siret: this.siret().trim(),
      tier: this.selectedTier(),
      billing: this.billing(),
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/pro/dashboard']);
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 409) {
          this.error.set('register.errors.emailConflict');
        } else {
          this.error.set('register.errors.generic');
        }
      },
    });
  }
}
```

- [ ] **Step 2 : Lancer les tests du composant**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/register-pro.component.spec.ts'`
Expected: PASS (12 tests verts)

Note : si compilation échoue parce que `registerPro` n'accepte pas encore `tier+billing` dans `auth.service.ts`, c'est attendu — Task 5 corrige ça. Pour avancer, modifier provisoirement la signature dans `auth.service.ts` (voir Task 5 directement).

---

### Task 4 : Refonte du template `register-pro.component.html`

**Files:**
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.html`

- [ ] **Step 1 : Remplacer le contenu du template**

Remplacer **tout** le fichier par :

```html
<div class="register-pro-page">

  <!-- ═══════════ HERO ═══════════ -->
  <section class="hero">
    <h1 class="hero-title">{{ 'register.pro.hero.title' | transloco }}</h1>
    <p class="hero-subtitle">{{ 'register.pro.hero.subtitle' | transloco }}</p>
  </section>

  <!-- ═══════════ TIER SELECTION ═══════════ -->
  <section class="tier-section">
    <h2 class="section-title">{{ 'register.pro.tierSection.title' | transloco }}</h2>
    <p class="section-subtitle">{{ 'register.pro.tierSection.subtitle' | transloco }}</p>

    <!-- Billing toggle -->
    <div class="billing-toggle">
      <button
        type="button"
        class="toggle-btn"
        [class.active]="billing() === 'MONTHLY'"
        (click)="billing.set('MONTHLY')"
      >
        {{ 'register.pro.billing.monthly' | transloco }}
      </button>
      <button
        type="button"
        class="toggle-btn"
        [class.active]="billing() === 'YEARLY'"
        (click)="billing.set('YEARLY')"
      >
        {{ 'register.pro.billing.yearly' | transloco }}
        <span class="discount-badge">-15%</span>
      </button>
    </div>

    <!-- Tier cards -->
    <div class="tier-cards">
      @for (tier of tiers; track tier.id) {
        <button
          type="button"
          class="tier-card"
          [class.selected]="selectedTier() === tier.id"
          [class.highlighted]="tier.highlighted"
          (click)="selectTier(tier.id)"
        >
          @if (tier.badgeKey) {
            <span class="tier-badge">{{ tier.badgeKey | transloco }}</span>
          }
          <h3 class="tier-name">{{ tier.nameKey | transloco }}</h3>
          <p class="tier-tagline">{{ tier.taglineKey | transloco }}</p>
          <div class="tier-price">
            <span class="price-amount">{{ getPrice(tier) }}€</span>
            <span class="price-unit">/{{ 'register.pro.priceUnit' | transloco }}</span>
          </div>
          @if (billing() === 'YEARLY') {
            <p class="price-note">{{ 'register.pro.billing.yearlyNote' | transloco }}</p>
          }
          <ul class="tier-features">
            @for (featureKey of tier.featuresKeys; track featureKey) {
              <li>{{ featureKey | transloco }}</li>
            }
          </ul>
          <span class="select-indicator">
            @if (selectedTier() === tier.id) {
              <mat-icon>check_circle</mat-icon>
            } @else {
              <mat-icon>radio_button_unchecked</mat-icon>
            }
          </span>
        </button>
      }
    </div>
  </section>

  <!-- ═══════════ FORMULAIRE ═══════════ -->
  <section class="form-section">
    <h2 class="section-title">{{ 'register.pro.formSection.title' | transloco }}</h2>

    <form class="form" (submit)="$event.preventDefault(); submit()">

      <!-- Compte -->
      <h3 class="form-group-title">{{ 'register.pro.formSection.account' | transloco }}</h3>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'register.pro.fields.name' | transloco }}</mat-label>
        <input matInput required [ngModel]="name()" (ngModelChange)="name.set($event)" name="name" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'register.pro.fields.email' | transloco }}</mat-label>
        <input matInput required type="email" [ngModel]="email()" (ngModelChange)="email.set($event)" name="email" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'register.pro.fields.password' | transloco }}</mat-label>
        <input matInput required type="password" minlength="8" [ngModel]="password()" (ngModelChange)="password.set($event)" name="password" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'register.pro.fields.confirmPassword' | transloco }}</mat-label>
        <input matInput required type="password" [ngModel]="confirmPassword()" (ngModelChange)="confirmPassword.set($event)" name="confirmPassword" />
        @if (!passwordsMatch()) {
          <mat-error>{{ 'register.pro.errors.passwordMismatch' | transloco }}</mat-error>
        }
      </mat-form-field>

      <!-- Salon -->
      <h3 class="form-group-title">{{ 'register.pro.formSection.salon' | transloco }}</h3>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'register.pro.fields.salonName' | transloco }}</mat-label>
        <input matInput required [ngModel]="salonName()" (ngModelChange)="salonName.set($event)" name="salonName" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'register.pro.fields.phone' | transloco }}</mat-label>
        <input matInput type="tel" [ngModel]="phone()" (ngModelChange)="phone.set($event)" name="phone" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'register.pro.fields.addressStreet' | transloco }}</mat-label>
        <input matInput [ngModel]="addressStreet()" (ngModelChange)="addressStreet.set($event)" name="addressStreet" />
      </mat-form-field>

      <div class="form-row-2">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'register.pro.fields.postalCode' | transloco }}</mat-label>
          <input matInput [ngModel]="addressPostalCode()" (ngModelChange)="addressPostalCode.set($event)" name="addressPostalCode" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>{{ 'register.pro.fields.city' | transloco }}</mat-label>
          <input matInput [ngModel]="addressCity()" (ngModelChange)="addressCity.set($event)" name="addressCity" />
        </mat-form-field>
      </div>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'register.pro.fields.siret' | transloco }}</mat-label>
        <input matInput [ngModel]="siret()" (ngModelChange)="siret.set($event)" name="siret" />
      </mat-form-field>

      <!-- Consent -->
      <mat-checkbox required [ngModel]="consent()" (ngModelChange)="consent.set($event)" name="consent">
        {{ 'register.pro.fields.consent' | transloco }}
      </mat-checkbox>

      <!-- Error -->
      @if (error()) {
        <p class="form-error">{{ error()! | transloco }}</p>
      }

      <!-- Submit -->
      <button
        mat-flat-button
        color="primary"
        type="submit"
        class="submit-btn"
        [disabled]="!isFormValid() || isLoading()"
      >
        @if (isLoading()) {
          <mat-spinner diameter="20"></mat-spinner>
        } @else {
          {{ 'register.pro.submit.cta' | transloco }}
        }
      </button>

      <p class="no-card-notice">
        {{ 'register.pro.noCard.notice' | transloco }}
      </p>
    </form>
  </section>

</div>
```

- [ ] **Step 2 : Vérifier que les tests passent toujours**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/register-pro.component.spec.ts'`
Expected: PASS (les tests s'appuient sur le code TS, pas sur le template)

---

### Task 5 : Adapter `AuthService.registerPro` (signature tier + billing)

**Files:**
- Modify: `frontend/src/app/core/auth/auth.service.ts:50-67`

- [ ] **Step 1 : Modifier la signature de `registerPro`**

Remplacer le bloc lignes 47-67 par :

```typescript
  /**
   * Register a new beauty professional with email and password
   */
  registerPro(data: {
    name: string; email: string; password: string;
    salonName: string; phone: string;
    addressStreet: string; addressPostalCode: string; addressCity: string;
    siret: string;
    tier: 'GESTION' | 'PREMIUM';
    billing: 'MONTHLY' | 'YEARLY';
  }): Observable<User> {
    return this.http.post<{accessToken: string, user: User}>(
      `${this.apiBaseUrl}/api/auth/register/pro`,
      { ...data, consent: true }
    ).pipe(
      tap(response => {
        this.setToken(response.accessToken);
        this.currentUser.set(response.user);
      }),
      map(response => response.user),
      catchError(error => { throw error; })
    );
  }
```

- [ ] **Step 2 : Lancer les tests auth**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/auth.service.spec.ts'`
Expected: PASS (si tests existants utilisent `plan: string`, les adapter en remplaçant `plan: 'pro'` par `tier: 'GESTION', billing: 'YEARLY'`)

---

### Task 6 : Mettre à jour les routes (`/pricing` → redirect)

**Files:**
- Modify: `frontend/src/app/app.routes.ts:22-32`

- [ ] **Step 1 : Remplacer les définitions de routes `/pricing` et `/register/pro`**

Remplacer le bloc :

```typescript
  {
    path: 'pricing',
    loadComponent: () =>
      import('./features/subscription/pricing/pricing-page.component').then(
        (m) => m.PricingPageComponent,
      ),
  },
  {
    path: 'register/pro',
    loadComponent: () => import('./pages/auth/register-pro/register-pro.component').then(m => m.RegisterProComponent),
  },
```

par :

```typescript
  { path: 'pricing', redirectTo: '/register/pro', pathMatch: 'full' },
  {
    path: 'register/pro',
    loadComponent: () => import('./pages/auth/register-pro/register-pro.component').then(m => m.RegisterProComponent),
  },
```

- [ ] **Step 2 : Lancer les tests app**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/app.config.spec.ts'`
Expected: PASS (ou test n'existe pas → ignorer)

---

### Task 7 : Supprimer le composant `PricingPageComponent` et les fichiers `VITRINE`

**Files:**
- Delete: `frontend/src/app/features/subscription/pricing/` (tout le dossier)

- [ ] **Step 1 : Supprimer le dossier pricing**

Run: `rm -rf frontend/src/app/features/subscription/pricing`

- [ ] **Step 2 : Vérifier qu'aucun import résiduel n'existe**

Run: `grep -rn "pricing-page\|PricingPageComponent" frontend/src --include='*.ts'`
Expected: aucun résultat

- [ ] **Step 3 : Modifier `SubscriptionTier` côté frontend pour exclure VITRINE de l'UI**

**Important :** on garde `'VITRINE'` dans le type pour compatibilité avec `pro-dashboard` qui lit le tier du tenant (qui peut être VITRINE par défaut backend). Mais on ne sélectionne plus VITRINE dans le formulaire d'inscription.

Aucune modif au fichier `subscription.model.ts` — laisser tel quel.

Run: `grep -rn "'VITRINE'\|VITRINE'" frontend/src --include='*.ts' --include='*.html'`
Expected: résultats uniquement dans `subscription.model.ts` et lectures conditionnelles du dashboard (acceptable)

---

### Task 8 : Mettre à jour les CTA dans `home.ts` et `footer.html`

**Files:**
- Modify: `frontend/src/app/pages/home/home.ts:160`
- Modify: `frontend/src/app/shared/layout/footer/footer.html`

- [ ] **Step 1 : Lire `home.ts` autour de la ligne 160**

Read: `frontend/src/app/pages/home/home.ts` (lignes 150-170)

- [ ] **Step 2 : Remplacer `this.router.navigate(['/pricing'])` par `this.router.navigate(['/register/pro'])`**

Edit:
- old_string: `this.router.navigate(['/pricing']);`
- new_string: `this.router.navigate(['/register/pro']);`

- [ ] **Step 3 : Remplacer les 3 routerLink `/pricing` dans `footer.html`**

Edit dans `frontend/src/app/shared/layout/footer/footer.html` :
- `routerLink="/pricing"` → `routerLink="/register/pro"` (replace_all)

- [ ] **Step 4 : Vérifier qu'aucun lien résiduel vers `/pricing` ne pointe vers une route inexistante**

Run: `grep -rn "'/pricing'\|\"/pricing\"\|routerLink=\"/pricing\"" frontend/src --include='*.ts' --include='*.html'`
Expected: aucun résultat (sauf éventuellement dans des tests/specs — vérifier au cas par cas)

---

### Task 9 : Mettre à jour les traductions i18n (fr + en)

**Files:**
- Modify: `frontend/src/assets/i18n/fr.json`
- Modify: `frontend/src/assets/i18n/en.json`

- [ ] **Step 1 : Lire `fr.json` pour repérer la section `register` existante**

Run: `grep -n "\"register\":\|\"pricing\":" frontend/src/assets/i18n/fr.json`

- [ ] **Step 2 : Ajouter sous `"register"` dans `fr.json` la sous-clé `pro`**

Ajouter à l'objet `"register"` existant (si la clé `"pro"` existe déjà, la remplacer) :

```json
"pro": {
  "hero": {
    "title": "Démarrez votre activité avec LuxPretty",
    "subtitle": "Créez votre compte sans carte bancaire. Vous paierez au moment de mettre votre salon en ligne."
  },
  "tierSection": {
    "title": "Choisissez votre formule",
    "subtitle": "Vous pourrez changer à tout moment depuis votre dashboard."
  },
  "billing": {
    "monthly": "Mensuel",
    "yearly": "Annuel",
    "yearlyNote": "facturé annuellement"
  },
  "priceUnit": "mois",
  "tiers": {
    "gestion": {
      "name": "Gestion",
      "tagline": "Tout pour gérer votre salon au quotidien",
      "badge": "Recommandé",
      "features": {
        "booking": "Réservations en ligne illimitées",
        "agenda": "Agenda intelligent",
        "clients": "Fiches clients & historique",
        "stats": "Statistiques détaillées"
      }
    },
    "premium": {
      "name": "Premium",
      "tagline": "Pour aller plus loin",
      "features": {
        "allGestion": "Tout Gestion +",
        "sms": "Rappels SMS automatiques",
        "priority": "Support prioritaire",
        "support": "Onboarding personnalisé"
      }
    }
  },
  "formSection": {
    "title": "Vos informations",
    "account": "Compte",
    "salon": "Salon"
  },
  "fields": {
    "name": "Votre nom",
    "email": "Email",
    "password": "Mot de passe",
    "confirmPassword": "Confirmer le mot de passe",
    "salonName": "Nom du salon",
    "phone": "Téléphone",
    "addressStreet": "Adresse",
    "postalCode": "Code postal",
    "city": "Ville",
    "siret": "SIRET (optionnel)",
    "consent": "J'accepte les conditions générales d'utilisation"
  },
  "errors": {
    "passwordMismatch": "Les mots de passe ne correspondent pas"
  },
  "submit": {
    "cta": "Créer mon compte"
  },
  "noCard": {
    "notice": "Aucune carte bancaire demandée. Vous paierez au moment de publier votre salon."
  }
}
```

- [ ] **Step 3 : Ajouter la même structure dans `en.json`**

Mêmes clés sous `"register"` :

```json
"pro": {
  "hero": {
    "title": "Start your business with LuxPretty",
    "subtitle": "Create your account with no credit card. You'll only pay when publishing your salon."
  },
  "tierSection": {
    "title": "Choose your plan",
    "subtitle": "You can change anytime from your dashboard."
  },
  "billing": {
    "monthly": "Monthly",
    "yearly": "Yearly",
    "yearlyNote": "billed annually"
  },
  "priceUnit": "month",
  "tiers": {
    "gestion": {
      "name": "Management",
      "tagline": "Everything to run your salon daily",
      "badge": "Recommended",
      "features": {
        "booking": "Unlimited online bookings",
        "agenda": "Smart calendar",
        "clients": "Client records & history",
        "stats": "Detailed analytics"
      }
    },
    "premium": {
      "name": "Premium",
      "tagline": "To go further",
      "features": {
        "allGestion": "Everything in Management +",
        "sms": "Automated SMS reminders",
        "priority": "Priority support",
        "support": "Personalized onboarding"
      }
    }
  },
  "formSection": {
    "title": "Your information",
    "account": "Account",
    "salon": "Salon"
  },
  "fields": {
    "name": "Your name",
    "email": "Email",
    "password": "Password",
    "confirmPassword": "Confirm password",
    "salonName": "Salon name",
    "phone": "Phone",
    "addressStreet": "Address",
    "postalCode": "Postal code",
    "city": "City",
    "siret": "Business ID (optional)",
    "consent": "I accept the terms and conditions"
  },
  "errors": {
    "passwordMismatch": "Passwords do not match"
  },
  "submit": {
    "cta": "Create my account"
  },
  "noCard": {
    "notice": "No credit card required. You'll only pay when publishing your salon."
  }
}
```

- [ ] **Step 4 : Supprimer la clé `"pricing"` dans `fr.json` et `en.json`**

Edit : retirer l'objet `"pricing": { ... }` complet dans les deux fichiers (toute la sous-arborescence pricing).

- [ ] **Step 5 : Vérifier la validité JSON**

Run: `cd frontend && node -e "JSON.parse(require('fs').readFileSync('src/assets/i18n/fr.json')); JSON.parse(require('fs').readFileSync('src/assets/i18n/en.json')); console.log('OK')"`
Expected: `OK`

---

### Task 10 : Styles SCSS du nouveau register-pro

**Files:**
- Modify: `frontend/src/app/pages/auth/register-pro/register-pro.component.scss`

- [ ] **Step 1 : Remplacer le contenu SCSS**

Remplacer **tout** le fichier par :

```scss
.register-pro-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 16px 64px;
  font-family: var(--mat-sys-typography-font-family, Roboto, sans-serif);
}

.hero {
  text-align: center;
  margin-bottom: 48px;

  .hero-title {
    font-size: 32px;
    font-weight: 600;
    color: var(--mat-sys-on-surface);
    margin: 0 0 12px;
  }

  .hero-subtitle {
    font-size: 16px;
    color: var(--mat-sys-on-surface-variant);
    margin: 0;
  }
}

.section-title {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 8px;
}

.section-subtitle {
  font-size: 14px;
  color: var(--mat-sys-on-surface-variant);
  margin: 0 0 24px;
}

.tier-section {
  margin-bottom: 48px;
}

.billing-toggle {
  display: inline-flex;
  background: var(--mat-sys-surface-container);
  border-radius: 999px;
  padding: 4px;
  margin-bottom: 24px;

  .toggle-btn {
    border: none;
    background: transparent;
    padding: 8px 20px;
    border-radius: 999px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    color: var(--mat-sys-on-surface-variant);
    transition: background 200ms, color 200ms;

    &.active {
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
    }

    .discount-badge {
      margin-left: 8px;
      font-size: 11px;
      background: rgba(255, 255, 255, 0.2);
      padding: 2px 6px;
      border-radius: 4px;
    }
  }
}

.tier-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.tier-card {
  position: relative;
  border: 2px solid var(--mat-sys-outline-variant);
  border-radius: 16px;
  padding: 24px;
  background: var(--mat-sys-surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 200ms, transform 200ms, box-shadow 200ms;
  font-family: inherit;

  &:hover {
    border-color: var(--mat-sys-primary);
    transform: translateY(-2px);
  }

  &.selected {
    border-color: var(--mat-sys-primary);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  }

  &.highlighted {
    border-color: var(--mat-sys-primary);
  }

  .tier-badge {
    position: absolute;
    top: -10px;
    left: 24px;
    background: var(--mat-sys-primary);
    color: var(--mat-sys-on-primary);
    padding: 4px 12px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  .tier-name {
    font-size: 20px;
    font-weight: 600;
    margin: 0 0 4px;
  }

  .tier-tagline {
    font-size: 13px;
    color: var(--mat-sys-on-surface-variant);
    margin: 0 0 16px;
  }

  .tier-price {
    display: flex;
    align-items: baseline;
    gap: 4px;
    margin-bottom: 4px;

    .price-amount {
      font-size: 32px;
      font-weight: 700;
    }

    .price-unit {
      font-size: 14px;
      color: var(--mat-sys-on-surface-variant);
    }
  }

  .price-note {
    font-size: 12px;
    color: var(--mat-sys-on-surface-variant);
    margin: 0 0 16px;
  }

  .tier-features {
    list-style: none;
    padding: 0;
    margin: 16px 0 0;

    li {
      padding: 6px 0;
      font-size: 14px;
      position: relative;
      padding-left: 20px;

      &::before {
        content: '✓';
        position: absolute;
        left: 0;
        color: var(--mat-sys-primary);
        font-weight: 600;
      }
    }
  }

  .select-indicator {
    position: absolute;
    top: 16px;
    right: 16px;
    color: var(--mat-sys-primary);
  }
}

.form-section {
  margin-top: 48px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 560px;
  margin: 0 auto;
}

.form-group-title {
  font-size: 16px;
  font-weight: 600;
  margin: 16px 0 4px;
  color: var(--mat-sys-on-surface);
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 12px;
}

.form-error {
  color: var(--mat-sys-error);
  font-size: 14px;
  margin: 0;
}

.submit-btn {
  margin-top: 16px;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

.no-card-notice {
  text-align: center;
  font-size: 13px;
  color: var(--mat-sys-on-surface-variant);
  margin: 8px 0 0;
}

@media (max-width: 600px) {
  .form-row-2 {
    grid-template-columns: 1fr;
  }
  .hero .hero-title {
    font-size: 24px;
  }
}
```

- [ ] **Step 2 : Lancer le build pour valider la compilation SCSS**

Run: `cd frontend && npm run build 2>&1 | tail -30`
Expected: build OK (warnings tolérés, erreurs non)

---

### Task 11 : Lancer la suite de tests frontend complète

**Files:** aucun

- [ ] **Step 1 : Lancer tous les tests**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: tous les tests verts. Si tests dans `pricing-page.component.spec.ts` cassent (fichier supprimé), c'est cohérent (le fichier n'existe plus, donc plus de tests à lancer).

- [ ] **Step 2 : Lancer le linter**

Run: `cd frontend && npx eslint 'src/app/pages/auth/register-pro/**/*.ts' 'src/app/core/auth/auth.service.ts' 'src/app/app.routes.ts'`
Expected: aucune erreur (warnings tolérés)

---

### Task 12 : Commit PR1

**Files:** aucun

- [ ] **Step 1 : Stage des fichiers**

```bash
git add frontend/src/app/pages/auth/register-pro/ \
        frontend/src/app/app.routes.ts \
        frontend/src/app/core/auth/auth.service.ts \
        frontend/src/app/pages/home/home.ts \
        frontend/src/app/shared/layout/footer/footer.html \
        frontend/src/assets/i18n/fr.json \
        frontend/src/assets/i18n/en.json \
        frontend/src/app/features/subscription/pricing
```

- [ ] **Step 2 : Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(register-pro): unify /pricing into /register/pro single page

Remove /pricing route (redirect to /register/pro), inline tier grid
in register-pro component, drop multi-step wizard, no credit card
asked at signup. VITRINE tier removed from signup UI.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## PR2 — Backend : DTO `tier` + `billing` typés

### Task 13 : Modifier `ProRegisterRequest` (ajouter tier + billing)

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java`

- [ ] **Step 1 : Remplacer le DTO**

Remplacer **tout** le fichier par :

```java
package com.luxpretty.app.auth.dto;

import com.luxpretty.app.subscription.domain.SubscriptionBilling;
import com.luxpretty.app.subscription.domain.SubscriptionTier;
import jakarta.validation.constraints.*;

public record ProRegisterRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotNull @AssertTrue Boolean consent,
    @NotBlank String salonName,
    String phone,
    String addressStreet,
    String addressPostalCode,
    String addressCity,
    String siret,
    @NotNull SubscriptionTier tier,
    @NotNull SubscriptionBilling billing
) {}
```

- [ ] **Step 2 : Compiler le backend pour vérifier**

Run: `cd backend && mvn compile -q 2>&1 | tail -20`
Expected: BUILD SUCCESS (sauf si `AuthController.registerProWithSalonInfo` lit `request.plan()`, qui n'existe plus → corrigé Task 14)

---

### Task 14 : Adapter `AuthController.registerProWithSalonInfo` pour persister tier + billing

**Files:**
- Modify: `backend/src/main/java/com/luxpretty/app/auth/AuthController.java:128-175`

- [ ] **Step 1 : Modifier la méthode pour assigner tier + billing au tenant**

Dans la méthode `registerProWithSalonInfo`, après le bloc `tenantRepository.save(tenant);` (ligne ~151), insérer la persistence du tier + billing AVANT cette save. Trouver l'edit suivant :

Remplacer :

```java
        var tenant = tenantProvisioningService.provision(savedUser);
        tenant.setName(request.salonName());
        tenant.setPhone(request.phone());
        tenant.setAddressStreet(request.addressStreet());
        tenant.setAddressPostalCode(request.addressPostalCode());
        tenant.setAddressCity(request.addressCity());
        tenant.setSiret(request.siret());
        tenantRepository.save(tenant);
```

par :

```java
        var tenant = tenantProvisioningService.provision(savedUser);
        tenant.setName(request.salonName());
        tenant.setPhone(request.phone());
        tenant.setAddressStreet(request.addressStreet());
        tenant.setAddressPostalCode(request.addressPostalCode());
        tenant.setAddressCity(request.addressCity());
        tenant.setSiret(request.siret());
        // Persist user's tier/billing choice for later use when publishing
        // (no Stripe subscription created here — tenant stays in DRAFT/VITRINE_FREE
        // until the pro actually clicks Publish on their dashboard).
        tenant.setSubscriptionTier(request.tier());
        tenant.setSubscriptionBilling(request.billing());
        tenantRepository.save(tenant);
```

- [ ] **Step 2 : Vérifier qu'un setter `setSubscriptionBilling` existe sur `Tenant`**

Run: `grep -n "subscriptionBilling\|SubscriptionBilling" backend/src/main/java/com/luxpretty/app/tenant/domain/Tenant.java`
Expected: champ présent. Sinon, ajouter au Tenant :

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_billing")
    private SubscriptionBilling subscriptionBilling;
```

(et son import + Lombok génère le setter via `@Data`/`@Setter`). Si pas de `@Data`, ajouter `setSubscriptionBilling` manuellement.

- [ ] **Step 3 : Compiler**

Run: `cd backend && mvn compile -q 2>&1 | tail -20`
Expected: BUILD SUCCESS

---

### Task 15 : Lancer les tests backend

**Files:** aucun (vérification)

- [ ] **Step 1 : Exécuter la suite de tests**

Run: `cd backend && mvn test -q 2>&1 | tail -40`
Expected: BUILD SUCCESS. Si tests `AuthControllerTests` cassent parce qu'ils envoient `"plan": "pro"` dans le body, les adapter en remplaçant par `"tier": "GESTION", "billing": "YEARLY"`.

- [ ] **Step 2 : Si tests cassent, lister les fichiers**

Run: `grep -rln "ProRegisterRequest\|register/pro\|\"plan\"" backend/src/test`
Expected: liste des tests à adapter. Les corriger un par un avec les nouvelles clés (`tier`, `billing`).

---

### Task 16 : Commit PR2

**Files:** aucun

- [ ] **Step 1 : Stage et commit**

```bash
git add backend/src/main/java/com/luxpretty/app/auth/dto/ProRegisterRequest.java \
        backend/src/main/java/com/luxpretty/app/auth/AuthController.java \
        backend/src/main/java/com/luxpretty/app/tenant/domain/Tenant.java \
        backend/src/test/
git commit -m "$(cat <<'EOF'
feat(auth): accept typed tier+billing in ProRegisterRequest

Persist user's chosen tier and billing on the tenant at signup
without creating a Stripe subscription. Stripe subscription is
deferred to the Publish action on the pro dashboard.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## PR3 — Frontend : Gate paiement avant publish

### Task 17 : Écrire le test du gate paiement dans `pro-dashboard.component.spec.ts`

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.spec.ts`

- [ ] **Step 1 : Lire le fichier existant pour voir le pattern de tests**

Read: `frontend/src/app/pages/pro/pro-dashboard.component.spec.ts` (premières 60 lignes)

- [ ] **Step 2 : Ajouter un nouveau `describe('publish gate')` dans le fichier de tests**

Ajouter à la fin du describe principal (avant la dernière accolade) :

```typescript
  describe('publish gate (no active subscription)', () => {
    it('redirects to /pro/onboarding/payment when subscription is missing', () => {
      const router = TestBed.inject(Router);
      spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

      // Setup: tenant has tier+billing but no active subscription
      // (subscription.status === 'INCOMPLETE' or null)
      // The exact mock depends on the store API — adapt based on existing patterns
      // in this file. Here we assume `component.hasActiveSubscription` returns false.
      spyOn(component, 'hasActiveSubscription').and.returnValue(false);
      spyOn(component, 'currentTier').and.returnValue('GESTION');
      spyOn(component, 'currentBilling').and.returnValue('YEARLY');

      component.onPublish();

      expect(router.navigate).toHaveBeenCalledWith(
        ['/pro/onboarding/payment'],
        { queryParams: { tier: 'GESTION', billing: 'YEARLY' } }
      );
    });

    it('calls store.publish() when subscription is active', () => {
      spyOn(component, 'hasActiveSubscription').and.returnValue(true);
      const publishSpy = spyOn(component['store'], 'publish');

      component.onPublish();

      expect(publishSpy).toHaveBeenCalled();
    });
  });
```

**Note** : adapter les noms exacts (`onPublish`, `hasActiveSubscription`, etc.) selon ce qui existe déjà dans le composant. Si le composant n'a pas encore `hasActiveSubscription`, c'est ajouté en Task 18.

- [ ] **Step 3 : Lancer le test (doit échouer)**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pro-dashboard.component.spec.ts'`
Expected: ÉCHEC (`hasActiveSubscription is not a function` ou similaire)

---

### Task 18 : Implémenter le gate paiement dans `ProDashboardComponent`

**Files:**
- Modify: `frontend/src/app/pages/pro/pro-dashboard.component.ts`

- [ ] **Step 1 : Lire la méthode `onPublish` existante (ligne ~360)**

Read: `frontend/src/app/pages/pro/pro-dashboard.component.ts` lignes 355-375

- [ ] **Step 2 : Ajouter `hasActiveSubscription`, `currentTier`, `currentBilling` (lecteurs du store)**

Repérer dans le composant la méthode qui appelle `this.store.publish()` (probablement nommée `onPublish()` autour de ligne 363) et :

1. Au début de la classe (après les autres signals/computed), ajouter :

```typescript
  // Read from store/tenant — adapt to actual store API
  hasActiveSubscription(): boolean {
    const tenant = this.store.tenant?.() ?? null;
    // Adapter selon le schema réel : status === 'ACTIVE' ou via subscriptionService
    return tenant?.subscriptionStatus === 'ACTIVE' || tenant?.subscriptionStatus === 'TRIALING';
  }

  currentTier(): 'GESTION' | 'PREMIUM' | 'VITRINE' {
    return (this.store.tenant?.()?.subscriptionTier as any) ?? 'GESTION';
  }

  currentBilling(): 'MONTHLY' | 'YEARLY' {
    return (this.store.tenant?.()?.subscriptionBilling as any) ?? 'YEARLY';
  }
```

2. Modifier `onPublish()` pour :

```typescript
  onPublish(): void {
    if (!this.hasActiveSubscription()) {
      this.router.navigate(['/pro/onboarding/payment'], {
        queryParams: {
          tier: this.currentTier(),
          billing: this.currentBilling(),
        },
      });
      return;
    }
    this.store.publish();
  }
```

**Note** : si le composant utilise déjà une autre méthode pour publier (par ex. via clic template direct), adapter en conséquence. Inspecter d'abord le template `pro-dashboard.component.html:124` (`data-testid="publish-btn"`) pour voir le handler exact.

- [ ] **Step 3 : Vérifier que `router` est injecté**

Run: `grep -n "private.*router\|router = inject" frontend/src/app/pages/pro/pro-dashboard.component.ts`
Expected: déjà présent. Sinon, ajouter `private readonly router = inject(Router);` + import.

- [ ] **Step 4 : Lancer le test**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless --include='**/pro-dashboard.component.spec.ts'`
Expected: PASS

---

### Task 19 : Vérifier que l'i18n contient un message "abonnement actif, cliquez Publier" (retour de paiement)

**Files:**
- Modify: `frontend/src/app/features/subscription/payment-onboarding/payment-onboarding.component.ts` (vérification seulement)

- [ ] **Step 1 : Lire le `submit()` du paiement et vérifier la redirection finale**

Read: `frontend/src/app/features/subscription/payment-onboarding/payment-onboarding.component.ts:120-158`

- [ ] **Step 2 : Constater que la redirection est `/pro/dashboard` direct**

Aucune modif requise dans ce composant — c'est le dashboard qui gère le message. Le dashboard détectera `subscriptionStatus === 'ACTIVE'` au prochain refresh et le bouton Publier deviendra fonctionnel.

**Optionnel** : ajouter un query param `?paymentSuccess=1` dans la redirection :

Remplacer ligne 150 :
```typescript
this.router.navigate(['/pro/dashboard']);
```
par :
```typescript
this.router.navigate(['/pro/dashboard'], { queryParams: { paymentSuccess: '1' } });
```

Et dans `pro-dashboard.component.ts` `ngOnInit`, lire le query param et afficher un snackbar "Abonnement actif — vous pouvez maintenant publier votre salon" via `MatSnackBar`. Si cette UX n'est pas critique pour le MVP, **skip cette task**.

- [ ] **Step 3 : Décision : skip pour MVP**

Pas de modif. Le pro retourne au dashboard, voit que l'abonnement est ACTIVE (badge ou état changé), reclique Publier.

---

### Task 20 : Tests + lint final

**Files:** aucun

- [ ] **Step 1 : Tous les tests frontend**

Run: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
Expected: tous verts

- [ ] **Step 2 : Tests backend**

Run: `cd backend && mvn test -q 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 3 : Build frontend production**

Run: `cd frontend && npm run build 2>&1 | tail -10`
Expected: build OK

- [ ] **Step 4 : Smoke test manuel**

Démarrer l'app (`docker compose --profile dev up frontend-dev` + `mvn spring-boot:run` côté backend) et vérifier manuellement :
- Aller sur `/` → cliquer "Démarrer" → arrive sur `/register/pro` (page unique)
- Aller sur `/pricing` → redirige vers `/register/pro`
- Sélectionner PREMIUM, toggle MONTHLY, remplir le form, soumettre → arrive sur `/pro/dashboard` (créé en DRAFT)
- Cliquer "Publier" → redirige vers `/pro/onboarding/payment?tier=PREMIUM&billing=MONTHLY`

---

### Task 21 : Commit PR3

**Files:** aucun

- [ ] **Step 1 : Stage et commit**

```bash
git add frontend/src/app/pages/pro/pro-dashboard.component.ts \
        frontend/src/app/pages/pro/pro-dashboard.component.spec.ts
git commit -m "$(cat <<'EOF'
feat(pro-dashboard): gate publish behind active subscription

When pro clicks Publish without an active Stripe subscription,
redirect to /pro/onboarding/payment with tier+billing in query
params. Once payment completes, pro returns to dashboard and
re-clicks Publish manually.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review (effectuée)

**Spec coverage :**
- ✅ Suppression `/pricing` → Task 6, 7
- ✅ Refonte `/register/pro` 1 page → Task 2-4, 10
- ✅ Suppression VITRINE UI (frontend) → Task 7 (note : backend conservé volontairement, documenté dans la spec)
- ✅ Flow Publier avec gate paiement → Task 17-19
- ✅ CTA mis à jour (home, footer) → Task 8
- ✅ Redirect `/pricing` → Task 6
- ✅ Backend `RegisterProRequest` typé → Task 13, 14
- ✅ i18n fr+en → Task 9
- ✅ Tests → Task 2, 11, 15, 17, 20

**Placeholders :** aucun "TBD" ou "implement later" dans les steps. Tous les blocs de code sont complets.

**Type consistency :**
- `selectedTier` / `selectTier` / `selectedPlan` : utilisé cohéremment `selectedTier` + `selectTier` partout
- `tier: 'GESTION' | 'PREMIUM'` cohérent dans `auth.service.ts`, `RegisterProComponent`, `ProRegisterRequest.java`
- `billing: 'MONTHLY' | 'YEARLY'` idem

**Ajustements à noter pendant l'implem :**
- Task 18 : adapter `hasActiveSubscription` selon l'API réelle du store dashboard (champs exacts du tenant en runtime à vérifier)
- Task 14 : vérifier si `Tenant` a déjà `subscriptionBilling` ; sinon l'ajouter (avec migration Flyway si nécessaire — sortir alors une mini-task)
- Task 9 : la clé `register` existe peut-être déjà dans fr.json — fusionner sous `register.pro` sans écraser le reste

---

## Execution Handoff

**Plan complet et sauvegardé dans `docs/superpowers/plans/2026-05-17-unify-pricing-register-pro.md`. Deux options d'exécution :**

**1. Subagent-Driven (recommandé)** — un subagent par task, review entre chaque, itération rapide

**2. Inline Execution** — exécution dans cette session, batch avec checkpoints

**Quelle approche ?**
