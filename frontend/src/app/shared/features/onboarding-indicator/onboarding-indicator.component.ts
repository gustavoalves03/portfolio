import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { OnboardingChecklistService } from '../../../features/onboarding/onboarding-checklist.service';
import { ONBOARDING_BREAKPOINT } from './breakpoint.token';

@Component({
  selector: 'app-onboarding-indicator',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe],
  templateUrl: './onboarding-indicator.component.html',
  styleUrl: './onboarding-indicator.component.scss',
})
export class OnboardingIndicatorComponent {
  private readonly store = inject(DashboardStore);
  private readonly checklistService = inject(OnboardingChecklistService);
  private readonly transloco = inject(TranslocoService);

  protected readonly isDesktop = inject(ONBOARDING_BREAKPOINT)();

  protected readonly steps = computed(() =>
    this.checklistService.buildSteps(this.store.readiness())
  );
  protected readonly progress = computed(() =>
    this.checklistService.computeProgress(this.steps())
  );
  protected readonly visible = computed(() => this.store.isDraft());

  protected readonly nextStepLabel = computed(() => {
    const key = this.progress().nextKey;
    if (!key) return '';
    return this.transloco.translate(`pro.dashboard.checklist.${key}`);
  });
}
