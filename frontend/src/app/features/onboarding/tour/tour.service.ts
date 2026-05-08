import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DashboardStore } from '../../dashboard/store/dashboard.store';
import { TenantReadiness } from '../../dashboard/models/dashboard.model';
import { TOUR_STEPS } from './tour-steps';
import { TourStep, WizardStepKey } from './tour-step.model';

const TRANSITION_DELAY_MS = 1500;

@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly router = inject(Router);
  private readonly store = inject(DashboardStore);

  private readonly _active = signal(false);
  private readonly _currentStep = signal<TourStep | null>(null);
  private readonly _inTransition = signal(false);

  readonly active = this._active.asReadonly();
  readonly currentStep = this._currentStep.asReadonly();
  readonly inTransition = this._inTransition.asReadonly();

  readonly progress = computed(() => {
    const r = this.store.readiness();
    if (!r) return { done: 0, total: TOUR_STEPS.length };
    const done = TOUR_STEPS.filter((s) => r[s.readinessFlag] === true).length;
    return { done, total: TOUR_STEPS.length };
  });

  constructor() {
    // Auto-advance when readiness flips the current step's flag to true.
    effect(() => {
      const step = this._currentStep();
      const r = this.store.readiness();
      if (!step || !r || !this._active() || this._inTransition()) return;
      if (r[step.readinessFlag] === true) {
        this.advance();
      }
    });
  }

  start(fromKey?: WizardStepKey): void {
    const r = this.store.readiness();
    if (!r) return;
    if (r.status === 'ACTIVE' && r.canPublish) return;

    const step = fromKey
      ? TOUR_STEPS.find((s) => s.key === fromKey) ?? null
      : this.firstMissing(r);
    if (!step) return;

    this._active.set(true);
    this.navigateTo(step);
  }

  stop(): void {
    this._active.set(false);
    this._currentStep.set(null);
    this._inTransition.set(false);
  }

  private advance(): void {
    this._inTransition.set(true);
    setTimeout(() => {
      const r = this.store.readiness();
      if (!r) {
        this.stop();
        return;
      }
      const next = this.firstMissing(r);
      this._inTransition.set(false);
      if (!next) {
        this.stop();
        return;
      }
      this.navigateTo(next);
    }, TRANSITION_DELAY_MS);
  }

  private navigateTo(step: TourStep): void {
    if (this.router.url.startsWith(step.route)) {
      this._currentStep.set(step);
    } else {
      this.router.navigateByUrl(step.route).then(() => this._currentStep.set(step));
    }
  }

  private firstMissing(r: TenantReadiness): TourStep | null {
    return TOUR_STEPS.find((s) => r[s.readinessFlag] === false) ?? null;
  }
}
