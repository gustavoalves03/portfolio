import { Component, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { VisitRecordResponse, AccessLevel } from '../../tracking.model';
import { VisitCardComponent } from '../visit-card/visit-card.component';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-client-visits',
  standalone: true,
  imports: [TranslocoPipe, VisitCardComponent, MatIconModule, MatButtonModule],
  template: `
    <div class="section">
      <div class="section-header">
        <span class="section-title">{{ 'tracking.lastVisit' | transloco }}</span>
        @if (visits().length > 1) {
          <button class="view-all" (click)="showAll.set(!showAll())">
            {{ showAll() ? ('tracking.showLess' | transloco) : ('tracking.viewAll' | transloco) }} →
          </button>
        }
      </div>
      @if (visits().length === 0) {
        <div class="empty">{{ 'tracking.noVisits' | transloco }}</div>
      } @else {
        <div class="visit-list">
          @if (showAll()) {
            @for (visit of visits(); track visit.id) {
              <app-visit-card [visit]="visit" [apiBaseUrl]="apiBaseUrl()" />
            }
          } @else {
            <app-visit-card [visit]="visits()[0]" [apiBaseUrl]="apiBaseUrl()" />
          }
        </div>
      }
      @if (accessLevel() === 'WRITE') {
        <button mat-flat-button class="new-visit-btn" (click)="createVisit.emit()">
          <mat-icon>add</mat-icon>
          {{ 'tracking.newVisit' | transloco }}
        </button>
      }
    </div>
  `,
  styles: `
    .section { margin-bottom: 12px; }
    .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .section-title { font-size: 13px; font-weight: 600; color: #333; }
    .view-all { background: none; border: none; font-size: 11px; color: #c06; font-weight: 600; cursor: pointer; }
    .empty { text-align: center; padding: 2rem; color: #9ca3af; font-size: 13px; }
    .visit-list { display: flex; flex-direction: column; gap: 8px; }
    .new-visit-btn {
      width: 100%;
      margin-top: 12px;
      background: #c06;
      color: white;
      border-radius: 10px;
    }
  `
})
export class ClientVisitsComponent {
  visits = input.required<VisitRecordResponse[]>();
  accessLevel = input<AccessLevel>('WRITE');
  apiBaseUrl = input<string>('');
  createVisit = output<void>();
  showAll = signal(false);
}
