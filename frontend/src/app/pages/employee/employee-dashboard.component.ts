import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { EmployeeMeService } from '../../features/employee-profile/employee-me.service';
import { Employee } from '../../features/employees/employees.model';

@Component({
    selector: 'app-employee-dashboard',
    standalone: true,
    imports: [
        RouterLink,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        MatProgressSpinnerModule,
        TranslocoPipe,
    ],
    template: `
        <div class="dashboard-page">
            @if (loading()) {
                <div class="loading-wrapper">
                    <mat-spinner diameter="40"></mat-spinner>
                </div>
            } @else if (employee()) {
                <div class="welcome-section">
                    <h1 class="page-title">{{ 'employee.dashboard.title' | transloco }}</h1>
                    <p class="welcome-text">
                        {{ 'employee.dashboard.welcome' | transloco : { name: employee()!.name } }}
                    </p>
                </div>

                <div class="stats-row">
                    <mat-card class="stat-card">
                        <mat-card-content>
                            <div class="stat-icon">
                                <mat-icon>spa</mat-icon>
                            </div>
                            <div class="stat-value">{{ employee()!.assignedCares.length }}</div>
                            <div class="stat-label">
                                {{ 'employee.dashboard.assignedCares' | transloco }}
                            </div>
                        </mat-card-content>
                    </mat-card>
                </div>

                <div class="quick-links-section">
                    <h2 class="section-title">
                        {{ 'employee.dashboard.quickLinks' | transloco }}
                    </h2>
                    <div class="quick-links-grid">
                        <a mat-stroked-button routerLink="/employee/planning" class="quick-link-btn">
                            <mat-icon>calendar_month</mat-icon>
                            {{ 'employee.planning.title' | transloco }}
                        </a>
                        <a mat-stroked-button routerLink="/employee/bookings" class="quick-link-btn">
                            <mat-icon>event_available</mat-icon>
                            {{ 'employee.bookings.title' | transloco }}
                        </a>
                        <a mat-stroked-button routerLink="/employee/leaves" class="quick-link-btn">
                            <mat-icon>beach_access</mat-icon>
                            {{ 'employee.leaves.title' | transloco }}
                        </a>
                        <a mat-stroked-button routerLink="/employee/documents" class="quick-link-btn">
                            <mat-icon>folder</mat-icon>
                            {{ 'employee.documents.title' | transloco }}
                        </a>
                    </div>
                </div>

                @if (employee()!.assignedCares.length > 0) {
                    <div class="cares-section">
                        <h2 class="section-title">
                            {{ 'employee.dashboard.assignedCares' | transloco }}
                        </h2>
                        <div class="cares-list">
                            @for (care of employee()!.assignedCares; track care.id) {
                                <span class="care-chip">{{ care.name }}</span>
                            }
                        </div>
                    </div>
                }
            }
        </div>
    `,
    styles: [`
        .dashboard-page {
            max-width: 800px;
            margin: 0 auto;
            padding: 1.5rem;
        }

        .loading-wrapper {
            display: flex;
            justify-content: center;
            padding: 3rem;
        }

        .welcome-section {
            margin-bottom: 1.5rem;
        }

        .page-title {
            font-size: 22px;
            font-weight: 700;
            color: #cc0066;
            margin: 0 0 4px;
        }

        .welcome-text {
            font-size: 16px;
            color: #555;
            margin: 0;
        }

        .stats-row {
            display: flex;
            gap: 1rem;
            margin-bottom: 2rem;
            flex-wrap: wrap;
        }

        .stat-card {
            flex: 1;
            min-width: 140px;
            max-width: 200px;
            border-radius: 12px !important;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08) !important;
        }

        .stat-card mat-card-content {
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 1.25rem 1rem !important;
        }

        .stat-icon {
            color: #cc0066;
            margin-bottom: 8px;
        }

        .stat-icon mat-icon {
            font-size: 32px;
            width: 32px;
            height: 32px;
        }

        .stat-value {
            font-size: 28px;
            font-weight: 700;
            color: #333;
            line-height: 1;
        }

        .stat-label {
            font-size: 12px;
            color: #777;
            text-align: center;
            margin-top: 4px;
        }

        .section-title {
            font-size: 16px;
            font-weight: 600;
            color: #333;
            margin: 0 0 12px;
        }

        .quick-links-section {
            margin-bottom: 2rem;
        }

        .quick-links-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
            gap: 0.75rem;
        }

        .quick-link-btn {
            display: flex !important;
            align-items: center;
            gap: 8px;
            justify-content: flex-start;
            padding: 0.75rem 1rem !important;
            border-color: #cc0066 !important;
            color: #cc0066 !important;
            border-radius: 8px !important;
            text-decoration: none;
        }

        .quick-link-btn mat-icon {
            font-size: 20px;
            width: 20px;
            height: 20px;
        }

        .cares-section {
            margin-bottom: 1rem;
        }

        .cares-list {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }

        .care-chip {
            background: #fce4ec;
            color: #cc0066;
            padding: 4px 12px;
            border-radius: 16px;
            font-size: 13px;
            font-weight: 500;
        }
    `],
})
export class EmployeeDashboardComponent implements OnInit {
    private employeeMeService = inject(EmployeeMeService);

    readonly loading = signal(true);
    readonly employee = signal<Employee | null>(null);

    ngOnInit(): void {
        this.employeeMeService.getProfile().subscribe({
            next: (emp) => {
                this.employee.set(emp);
                this.loading.set(false);
            },
            error: () => {
                this.loading.set(false);
            },
        });
    }
}
