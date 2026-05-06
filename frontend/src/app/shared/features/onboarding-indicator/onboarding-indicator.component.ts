import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { OnboardingChecklistService } from '../../../features/onboarding/onboarding-checklist.service';
import { bottomSheetConfig } from '../../uis/sheet-handle/bottom-sheet.config';
import { ONBOARDING_BREAKPOINT } from './breakpoint.token';
import {
  OnboardingIndicatorSheetComponent,
  OnboardingSheetData,
  OnboardingSheetResult,
} from './onboarding-indicator-sheet.component';

@Component({
  selector: 'app-onboarding-indicator',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe, RouterLink, MatTooltipModule],
  templateUrl: './onboarding-indicator.component.html',
  styleUrl: './onboarding-indicator.component.scss',
})
export class OnboardingIndicatorComponent {
  protected readonly store = inject(DashboardStore);
  private readonly checklistService = inject(OnboardingChecklistService);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  protected dialog = inject(MatDialog);

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

  protected onPillClick(): void {
    const readiness = this.store.readiness();
    if (!readiness) return;

    const ref = this.dialog.open<
      OnboardingIndicatorSheetComponent,
      OnboardingSheetData,
      OnboardingSheetResult
    >(
      OnboardingIndicatorSheetComponent,
      bottomSheetConfig<OnboardingSheetData>({
        data: {
          steps: this.steps(),
          progress: this.progress(),
          canPublish: this.store.canPublish(),
          slug: readiness.slug,
        },
      })
    );

    ref.afterClosed().subscribe((result) => this.handleSheetResult(result));
  }

  protected onPublishClick(): void {
    this.store.publish();
  }

  private handleSheetResult(result: OnboardingSheetResult | undefined): void {
    if (!result) return;
    switch (result.action) {
      case 'step': {
        const step = this.steps().find((s) => s.key === result.stepKey);
        if (step) this.router.navigate([step.link], { queryParams: step.queryParams ?? undefined });
        return;
      }
      case 'preview': {
        const slug = this.store.readiness()?.slug;
        if (slug) this.router.navigate(['/salon', slug]);
        return;
      }
      case 'publish':
        this.store.publish();
        return;
      case 'dashboard':
        this.router.navigate(['/pro/dashboard']);
        return;
    }
  }
}
