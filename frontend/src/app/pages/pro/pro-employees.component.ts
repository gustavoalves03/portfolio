import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslocoPipe } from '@jsverse/transloco';
import { EmployeesComponent } from '../../features/employees/employees.component';
import { LeavesComponent } from '../../features/leaves/leaves.component';

@Component({
    selector: 'app-pro-employees',
    standalone: true,
    imports: [MatTabsModule, TranslocoPipe, EmployeesComponent, LeavesComponent],
    template: `
        <div class="employees-page">
            <h1 class="page-title">{{ 'pro.employees.title' | transloco }}</h1>
            <mat-tab-group animationDuration="150ms">
                <mat-tab [label]="'pro.employees.teamTab' | transloco">
                    <div class="tab-content"><app-employees /></div>
                </mat-tab>
                <mat-tab [label]="'pro.employees.leavesTab' | transloco">
                    <div class="tab-content"><app-leaves /></div>
                </mat-tab>
            </mat-tab-group>
        </div>
    `,
    styles: [`
        .employees-page { background: #f5f4f2; padding: 16px; max-width: 800px; margin: 0 auto; }
        .page-title { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 16px; }
        .tab-content { padding-top: 16px; }
        ::ng-deep .mat-mdc-tab-labels { justify-content: center; }
    `],
})
export class ProEmployeesComponent {}
