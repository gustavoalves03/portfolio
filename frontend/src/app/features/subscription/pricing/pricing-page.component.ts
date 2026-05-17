import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslocoModule } from '@jsverse/transloco';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { SubscriptionService } from '../services/subscription.service';
import { PricingPlan } from '../models/subscription.model';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import {
  ProSignupModalComponent,
  ProSignupModalResult,
} from '../../../shared/modals/pro-signup-modal/pro-signup-modal.component';

interface FeatureValue {
  yes?: boolean;
  no?: boolean;
  unlimited?: boolean;
  priority?: boolean;
}

interface Feature {
  key: string;
  vitrine: FeatureValue;
  gestion: FeatureValue;
  premium: FeatureValue;
}

interface FeatureGroup {
  key: string;
  icon: SafeHtml;
  features: Feature[];
}

@Component({
  selector: 'app-pricing-page',
  standalone: true,
  imports: [TranslocoModule],
  templateUrl: './pricing-page.component.html',
  styleUrl: './pricing-page.component.scss',
})
export class PricingPageComponent implements OnInit {
  private readonly subscriptionService = inject(SubscriptionService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  private svg(raw: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(raw);
  }

  billing = signal<'MONTHLY' | 'YEARLY'>('YEARLY');
  plans = signal<PricingPlan[]>([]);
  loading = signal(true);

  // Accordion state for mobile
  openAccordion = signal<string | null>(null);

  // Fallback defaults from spec
  private readonly FALLBACKS = {
    vitrineMonthly: 9.99,
    vitrineYearly: 7.99,
    gestionMonthly: 49.99,
    gestionYearly: 42.49,
    premiumMonthly: 67.99,
    premiumYearly: 57.79,
  };

  vitrinePrice = computed(() => this.priceFor('VITRINE', this.billing()));
  gestionPrice = computed(() => this.priceFor('GESTION', this.billing()));
  premiumPrice = computed(() => this.priceFor('PREMIUM', this.billing()));

  gestionAnnualTotal = computed(() => {
    const monthly = this.priceFor('GESTION', 'YEARLY');
    return (monthly * 12).toFixed(2).replace('.', ',');
  });

  gestionMonthlyRef = computed(() => {
    return this.priceFor('GESTION', 'MONTHLY');
  });

  readonly featureGroups: FeatureGroup[];

  constructor() {
    this.featureGroups = [
      {
        key: 'presence',
        icon: this.svg(`<svg width="14" height="14" viewBox="0 0 14 14" fill="none" style="opacity:0.75;flex-shrink:0;"><circle cx="7" cy="7" r="6.5" stroke="white" stroke-opacity="0.6"/><path d="M4 7h6M7 4v6" stroke="white" stroke-width="1.2" stroke-linecap="round"/></svg>`),
        features: [
          { key: 'publicPage', vitrine: { yes: true }, gestion: { yes: true }, premium: { yes: true } },
          { key: 'photos', vitrine: { no: true }, gestion: { yes: true }, premium: { yes: true } },
          { key: 'discoveryVisibility', vitrine: { yes: true }, gestion: { yes: true }, premium: { priority: true } },
        ],
      },
      {
        key: 'booking',
        icon: this.svg(`<svg width="14" height="14" viewBox="0 0 14 14" fill="none" style="opacity:0.75;flex-shrink:0;"><rect x="1" y="2.5" width="12" height="10" rx="2" stroke="white" stroke-opacity="0.6"/><path d="M1 5.5h12M4.5 1v3M9.5 1v3" stroke="white" stroke-width="1.2" stroke-linecap="round"/></svg>`),
        features: [
          { key: 'onlineBooking', vitrine: { no: true }, gestion: { yes: true }, premium: { yes: true } },
          { key: 'multiPractitioner', vitrine: { no: true }, gestion: { yes: true }, premium: { yes: true } },
          { key: 'smsReminders', vitrine: { no: true }, gestion: { yes: true }, premium: { yes: true } },
          { key: 'absences', vitrine: { no: true }, gestion: { yes: true }, premium: { yes: true } },
        ],
      },
      {
        key: 'clients',
        icon: this.svg(`<svg width="14" height="14" viewBox="0 0 14 14" fill="none" style="opacity:0.75;flex-shrink:0;"><circle cx="7" cy="5" r="3" stroke="white" stroke-opacity="0.6" stroke-width="1.2"/><path d="M2 12c0-2.76 2.24-5 5-5s5 2.24 5 5" stroke="white" stroke-opacity="0.6" stroke-width="1.2" stroke-linecap="round"/></svg>`),
        features: [
          { key: 'clientFiles', vitrine: { no: true }, gestion: { yes: true }, premium: { yes: true } },
          { key: 'history', vitrine: { no: true }, gestion: { yes: true }, premium: { yes: true } },
          { key: 'loyalty', vitrine: { no: true }, gestion: { no: true }, premium: { yes: true } },
          { key: 'gdpr', vitrine: { yes: true }, gestion: { yes: true }, premium: { yes: true } },
        ],
      },
      {
        key: 'sales',
        icon: this.svg(`<svg width="14" height="14" viewBox="0 0 14 14" fill="none" style="opacity:0.75;flex-shrink:0;"><path d="M2 2h1.5l1.5 6h5l1.5-4H5" stroke="white" stroke-opacity="0.6" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="6.5" cy="11" r="1" fill="white" fill-opacity="0.6"/><circle cx="10.5" cy="11" r="1" fill="white" fill-opacity="0.6"/></svg>`),
        features: [
          { key: 'onlinePayment', vitrine: { no: true }, gestion: { no: true }, premium: { yes: true } },
          { key: 'shop', vitrine: { no: true }, gestion: { no: true }, premium: { yes: true } },
          { key: 'autoInvoices', vitrine: { no: true }, gestion: { yes: true }, premium: { yes: true } },
        ],
      },
      {
        key: 'team',
        icon: this.svg(`<svg width="14" height="14" viewBox="0 0 14 14" fill="none" style="opacity:0.75;flex-shrink:0;"><circle cx="5" cy="4.5" r="2.5" stroke="white" stroke-opacity="0.6" stroke-width="1.2"/><path d="M1 12c0-2.21 1.79-4 4-4s4 1.79 4 4" stroke="white" stroke-opacity="0.6" stroke-width="1.2" stroke-linecap="round"/><circle cx="10" cy="5" r="2" stroke="white" stroke-opacity="0.5" stroke-width="1.2"/><path d="M10.5 9.5c1.38 0 2.5 1.12 2.5 2.5" stroke="white" stroke-opacity="0.5" stroke-width="1.2" stroke-linecap="round"/></svg>`),
        features: [
          { key: 'multiPractitionerSeats', vitrine: { no: true }, gestion: { unlimited: true }, premium: { unlimited: true } },
          { key: 'multiLocations', vitrine: { no: true }, gestion: { no: true }, premium: { yes: true } },
        ],
      },
    ];
  }

  ngOnInit(): void {
    this.subscriptionService.getPricing().subscribe({
      next: (plans) => {
        this.plans.set(plans);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('[PricingPage] Failed to load pricing plans, using fallback values', err);
        this.loading.set(false);
      },
    });
  }

  priceFor(tier: 'VITRINE' | 'GESTION' | 'PREMIUM', billing: 'MONTHLY' | 'YEARLY'): number {
    const plans = this.plans();
    if (plans.length === 0) {
      // Fallback to spec defaults
      if (tier === 'VITRINE') {
        return billing === 'YEARLY' ? this.FALLBACKS.vitrineYearly : this.FALLBACKS.vitrineMonthly;
      }
      if (tier === 'GESTION') {
        return billing === 'YEARLY' ? this.FALLBACKS.gestionYearly : this.FALLBACKS.gestionMonthly;
      }
      return billing === 'YEARLY' ? this.FALLBACKS.premiumYearly : this.FALLBACKS.premiumMonthly;
    }
    const plan = plans.find((p) => p.tier === tier && p.billing === billing);
    if (!plan) {
      if (tier === 'VITRINE') {
        return billing === 'YEARLY' ? this.FALLBACKS.vitrineYearly : this.FALLBACKS.vitrineMonthly;
      }
      if (tier === 'GESTION') {
        return billing === 'YEARLY' ? this.FALLBACKS.gestionYearly : this.FALLBACKS.gestionMonthly;
      }
      return billing === 'YEARLY' ? this.FALLBACKS.premiumYearly : this.FALLBACKS.premiumMonthly;
    }
    return plan.monthlyPriceEuros;
  }

  formatPrice(value: number): string {
    return value.toFixed(2).replace('.', ',') + ' €';
  }

  toggleAccordion(tier: string): void {
    this.openAccordion.update((current) => (current === tier ? null : tier));
  }

  isAccordionOpen(tier: string): boolean {
    return this.openAccordion() === tier;
  }

  onStartTier(tier: 'VITRINE' | 'GESTION' | 'PREMIUM'): void {
    const billing = this.billing();
    const user = this.authService.user();

    if (!user) {
      this.dialog.open(ProSignupModalComponent, {
        data: { tier, billing },
        width: '480px',
      }).afterClosed().subscribe((result: ProSignupModalResult | undefined) => {
        if (result?.authenticated) {
          this.router.navigate(['/pro/dashboard']);
        }
      });
      return;
    }

    if (user.roles.includes(Role.PRO)) {
      this.router.navigate(['/pro/dashboard']);
      return;
    }

    this.authService.upgradeToPro({ tier, billing }).subscribe({
      next: () => this.router.navigate(['/pro/dashboard']),
      error: (err) => {
        if (err.status === 409) {
          this.router.navigate(['/pro/dashboard']);
        } else {
          this.snackBar.open('Une erreur est survenue', 'OK', { duration: 4000 });
        }
      },
    });
  }

}
