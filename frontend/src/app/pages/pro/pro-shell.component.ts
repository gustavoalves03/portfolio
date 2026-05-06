import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
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
export class ProShellComponent {}
