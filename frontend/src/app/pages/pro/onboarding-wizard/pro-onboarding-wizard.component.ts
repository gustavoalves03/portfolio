import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { TenantReadiness } from '../../../features/dashboard/models/dashboard.model';
import { WIZARD_STEP_ORDER, WizardStepKey } from '../../../features/onboarding/wizard/wizard-step.model';
import { WizardProgressBarComponent } from './wizard-progress-bar/wizard-progress-bar.component';
import { WelcomeStepComponent } from './steps/welcome-step.component';
import { NameStepComponent } from './steps/name-step.component';
import { ContactStepComponent } from './steps/contact-step.component';
import { LogoStepComponent } from './steps/logo-step.component';
import { CategoriesStepComponent } from './steps/categories-step.component';
import { CaresStepComponent } from './steps/cares-step.component';
import { OpeningHoursStepComponent } from './steps/opening-hours-step.component';
import { PublishStepComponent } from './steps/publish-step.component';

@Component({
  selector: 'app-pro-onboarding-wizard',
  standalone: true,
  imports: [
    TranslocoPipe,
    WizardProgressBarComponent,
    WelcomeStepComponent,
    NameStepComponent,
    ContactStepComponent,
    LogoStepComponent,
    CategoriesStepComponent,
    CaresStepComponent,
    OpeningHoursStepComponent,
    PublishStepComponent,
  ],
  templateUrl: './pro-onboarding-wizard.component.html',
  styleUrl: './pro-onboarding-wizard.component.scss',
})
export class ProOnboardingWizardComponent {
  protected readonly store = inject(DashboardStore);
  private readonly router = inject(Router);

  protected readonly currentStep = signal<WizardStepKey>('welcome');
  protected readonly hasSeenWelcome = signal(false);
  protected readonly currentIndex = computed(() => WIZARD_STEP_ORDER.indexOf(this.currentStep()));
  protected readonly totalSteps = WIZARD_STEP_ORDER.length;

  protected readonly publishRecap = computed(() => {
    const r = this.store.readiness();
    return r ? { name: null, addressCity: null, logoUrl: null, slug: r.slug } : null;
  });

  constructor() {
    effect(
      () => {
        const r = this.store.readiness();
        if (!r) return;
        if (r.status === 'ACTIVE') {
          this.router.navigate(['/pro/dashboard']);
          return;
        }
        // First-time pro with no progress: stay on welcome until they click through.
        if (!r.name && !this.hasSeenWelcome()) {
          this.currentStep.set('welcome');
          return;
        }
        // Resuming pro (or has seen welcome): jump to first unfinished step.
        this.currentStep.set(this.firstUnfinishedStep(r));
      },
      { allowSignalWrites: true }
    );
  }

  protected onStepCompleted(): void {
    if (this.currentStep() === 'welcome') {
      this.hasSeenWelcome.set(true);
    }
    this.store.loadReadiness();
  }

  protected onExit(): void {
    sessionStorage.setItem('pf_skipOnboarding', '1');
    this.router.navigate(['/pro/dashboard']);
  }

  protected onBack(): void {
    const i = this.currentIndex();
    if (i > 0) this.currentStep.set(WIZARD_STEP_ORDER[i - 1]);
  }

  protected onJumpTo(index: number): void {
    if (index >= 0 && index < this.currentIndex()) {
      this.currentStep.set(WIZARD_STEP_ORDER[index]);
    }
  }

  private firstUnfinishedStep(r: TenantReadiness): WizardStepKey {
    if (!r.name) return 'name';
    if (!r.hasContact) return 'contact';
    if (!r.hasLogo) return 'logo';
    if (!r.hasCategory) return 'categories';
    if (!r.hasActiveCare) return 'cares';
    if (!r.hasOpeningHours) return 'openingHours';
    return 'publish';
  }
}
