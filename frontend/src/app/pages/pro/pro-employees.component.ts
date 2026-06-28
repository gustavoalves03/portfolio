import { Component, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslocoPipe } from '@jsverse/transloco';
import { EmployeesComponent } from '../../features/employees/employees.component';
import { LeavesComponent } from '../../features/leaves/leaves.component';
import { FeatureLockedComponent } from '../../core/feature-flags/feature-locked.component';
import { FeatureFlagsStore } from '../../core/feature-flags/feature-flags.store';

@Component({
    selector: 'app-pro-employees',
    standalone: true,
    imports: [MatTabsModule, TranslocoPipe, EmployeesComponent, LeavesComponent, FeatureLockedComponent],
    template: `
        <lp-feature-locked feature="EMPLOYEES">
        <div class="employees-page">
            <h1 class="page-title">{{ 'pro.employees.title' | transloco }}</h1>
            <!-- Children fetch their own data on init; skip them when gated so the
                 page renders empty behind the upsell overlay (no 403 round-trips). -->
            @if (employeesEnabled()) {
            <mat-tab-group animationDuration="150ms">
                <mat-tab [label]="'pro.employees.teamTab' | transloco">
                    <div class="tab-content"><app-employees /></div>
                </mat-tab>
                <mat-tab [label]="'pro.employees.leavesTab' | transloco">
                    <div class="tab-content"><app-leaves /></div>
                </mat-tab>
            </mat-tab-group>
            }
        </div>
        </lp-feature-locked>
    `,
    styles: [`
        .employees-page { background: var(--pf-paper); padding: 16px; max-width: 1440px; margin: 0 auto; }
        @media (min-width: 768px) { .employees-page { padding: 24px 32px; } }
        @media (min-width: 1280px) { .employees-page { padding: 24px 48px; } }
        .page-title { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 16px; }
        .tab-content { padding-top: 16px; }
        ::ng-deep .mat-mdc-tab-labels { justify-content: center; }
    `],
})
export class ProEmployeesComponent {
    private readonly featureFlags = inject(FeatureFlagsStore);
    protected readonly employeesEnabled = this.featureFlags.isEnabled('EMPLOYEES');
}
