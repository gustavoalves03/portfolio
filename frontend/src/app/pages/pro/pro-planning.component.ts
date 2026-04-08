import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslocoPipe } from '@jsverse/transloco';
import { AvailabilityComponent } from '../../features/availability/availability.component';
import { CalendarComponent } from '../../features/calendar/calendar.component';

@Component({
  selector: 'app-pro-planning',
  standalone: true,
  imports: [MatTabsModule, TranslocoPipe, AvailabilityComponent, CalendarComponent],
  template: `
    <div class="planning-page">
      <h1 class="page-title">{{ 'pro.planning.title' | transloco }}</h1>
      <mat-tab-group animationDuration="150ms">
        <mat-tab [label]="'pro.planning.openingHours' | transloco">
          <div class="tab-content">
            <app-availability></app-availability>
          </div>
        </mat-tab>
        <mat-tab [label]="'pro.planning.blockedSlots' | transloco">
          <div class="tab-content">
            <app-calendar></app-calendar>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .planning-page {
      background: #f5f4f2;
      padding: 16px;
      max-width: 800px;
      margin: 0 auto;
    }
    .page-title {
      font-size: 18px;
      font-weight: 600;
      color: #333;
      margin: 0 0 16px;
    }
    .tab-content {
      padding-top: 16px;
    }
    ::ng-deep .mat-mdc-tab-labels {
      justify-content: center;
    }
  `],
})
export class ProPlanningComponent {}
