import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
    selector: 'app-employee-bookings',
    standalone: true,
    imports: [MatIconModule, TranslocoPipe],
    template: `
        <div class="bookings-page">
            <h1 class="page-title">{{ 'employee.bookings.title' | transloco }}</h1>
            <div class="empty-state">
                <mat-icon class="empty-icon">event_available</mat-icon>
                <p class="empty-text">{{ 'employee.bookings.empty' | transloco }}</p>
            </div>
        </div>
    `,
    styles: [`
        .bookings-page {
            max-width: 800px;
            margin: 0 auto;
            padding: 1.5rem;
        }

        .page-title {
            font-size: 20px;
            font-weight: 600;
            color: #333;
            margin: 0 0 24px;
        }

        .empty-state {
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 3rem 1rem;
            color: #aaa;
        }

        .empty-icon {
            font-size: 48px;
            width: 48px;
            height: 48px;
            margin-bottom: 1rem;
            color: #ddd;
        }

        .empty-text {
            font-size: 15px;
            color: #999;
            margin: 0;
        }
    `],
})
export class EmployeeBookingsComponent {}
