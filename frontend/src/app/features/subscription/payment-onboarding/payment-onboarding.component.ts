import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import type { Stripe, StripeElements, StripePaymentElement } from '@stripe/stripe-js';

import { SubscriptionService } from '../services/subscription.service';
import { SubscriptionBilling, SubscriptionTier } from '../models/subscription.model';

@Component({
  selector: 'app-payment-onboarding',
  standalone: true,
  imports: [TranslocoModule],
  templateUrl: './payment-onboarding.component.html',
  styleUrl: './payment-onboarding.component.scss',
})
export class PaymentOnboardingComponent implements OnInit, OnDestroy {
  @ViewChild('cardElement') cardElementRef!: ElementRef<HTMLDivElement>;

  private readonly platformId = inject(PLATFORM_ID);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly subscriptionService = inject(SubscriptionService);
  private readonly transloco = inject(TranslocoService);

  loading = signal(true);
  submitting = signal(false);
  error = signal<string | null>(null);
  tier = signal<SubscriptionTier>('GESTION');
  billing = signal<SubscriptionBilling>('YEARLY');

  // Imperative Stripe SDK objects (not signals)
  private stripe: Stripe | null = null;
  private elements: StripeElements | null = null;
  private paymentElement: StripePaymentElement | null = null;

  readonly recapLabel = computed(() => {
    const t = this.tier();
    const b = this.billing();
    const key = `paymentOnboarding.recap.${t.toLowerCase()}${b === 'YEARLY' ? 'Yearly' : 'Monthly'}`;
    return this.transloco.translate(key);
  });

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const tierParam = params.get('tier') as SubscriptionTier | null;
    const billingParam = params.get('billing') as SubscriptionBilling | null;

    if (tierParam) this.tier.set(tierParam);
    if (billingParam) this.billing.set(billingParam);

    if (!isPlatformBrowser(this.platformId)) {
      // SSR: keep loading=true, render skeleton, skip all browser APIs
      return;
    }

    this.initStripe();
  }

  ngOnDestroy(): void {
    this.paymentElement?.unmount();
  }

  private initStripe(): void {
    forkJoin([
      this.subscriptionService.getStripeConfig(),
      this.subscriptionService.createSetupIntent(),
    ]).subscribe({
      next: async ([config, setupIntent]) => {
        try {
          this.stripe = await this.loadStripeLib(config.publishableKey);
          if (!this.stripe) {
            this.error.set(this.transloco.translate('paymentOnboarding.errors.init'));
            this.loading.set(false);
            return;
          }

          this.elements = this.stripe.elements({ clientSecret: setupIntent.clientSecret });
          this.paymentElement = this.elements.create('payment');
          this.loading.set(false);
          // Mount after loading=false so Angular renders the #cardElement div first
          await this.mountPaymentElement();
        } catch {
          this.error.set(this.transloco.translate('paymentOnboarding.errors.init'));
          this.loading.set(false);
        }
      },
      error: () => {
        this.error.set(this.transloco.translate('paymentOnboarding.errors.init'));
        this.loading.set(false);
      },
    });
  }

  // Extracted for testability — spied on in unit tests
  protected async loadStripeLib(publishableKey: string): Promise<Stripe | null> {
    const mod = await import('@stripe/stripe-js');
    return mod.loadStripe(publishableKey);
  }

  // Extracted for testability — polls until the #cardElement div is rendered,
  // then mounts. Single microtask isn't enough in zoneless mode: change
  // detection may not have run yet, and ViewChild can still be undefined.
  protected async mountPaymentElement(): Promise<void> {
    const maxAttempts = 50; // ~500ms total
    for (let i = 0; i < maxAttempts; i++) {
      if (this.cardElementRef?.nativeElement) {
        break;
      }
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    if (!this.paymentElement) {
      this.error.set(this.transloco.translate('paymentOnboarding.errors.init'));
      return;
    }
    if (!this.cardElementRef?.nativeElement) {
      console.error('payment-onboarding: #cardElement never appeared in DOM');
      this.error.set(this.transloco.translate('paymentOnboarding.errors.init'));
      return;
    }
    this.paymentElement.mount(this.cardElementRef.nativeElement);
  }

  async submit(): Promise<void> {
    if (!this.stripe || !this.elements) return;

    this.submitting.set(true);
    this.error.set(null);

    const result = await this.stripe.confirmSetup({
      elements: this.elements,
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required',
    });

    if (result.error) {
      this.error.set(
        result.error.message ?? this.transloco.translate('paymentOnboarding.errors.stripe'),
      );
      this.submitting.set(false);
      return;
    }

    const paymentMethodId = result.setupIntent?.payment_method as string;

    this.subscriptionService
      .createSubscription({
        tier: this.tier(),
        billing: this.billing(),
        paymentMethodId,
      })
      .subscribe({
        next: () => {
          this.router.navigate(['/pro/dashboard'], {
            queryParams: { paymentSuccess: '1' },
          });
        },
        error: () => {
          this.error.set(this.transloco.translate('paymentOnboarding.errors.subscription'));
          this.submitting.set(false);
        },
      });
  }
}
