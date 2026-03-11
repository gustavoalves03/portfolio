import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-pro-dashboard',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <div class="flex items-center justify-center min-h-[60vh]">
      <div class="text-center">
        <h1 class="text-2xl font-medium text-neutral-800 mb-2">{{ 'pro.dashboard.title' | transloco }}</h1>
        <p class="text-neutral-500">{{ 'common.comingSoon' | transloco }}</p>
      </div>
    </div>
  `,
})
export class ProDashboardComponent {}
