import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';
import { OnboardingIndicatorComponent } from '../../shared/features/onboarding-indicator/onboarding-indicator.component';

@Component({
  selector: 'app-pro-shell',
  standalone: true,
  imports: [RouterOutlet, OnboardingIndicatorComponent],
  providers: [DashboardStore],
  templateUrl: './pro-shell.component.html',
  styleUrl: './pro-shell.component.scss',
})
export class ProShellComponent {
  private readonly store = inject(DashboardStore);
  private readonly router = inject(Router);

  constructor() {
    // Re-fetch readiness on every entry to the dashboard so the checklist
    // reflects steps the pro just completed on /pro/cares or /pro/planning.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        filter((e) => e.urlAfterRedirects.startsWith('/pro/dashboard')),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.store.loadReadiness());
  }
}
