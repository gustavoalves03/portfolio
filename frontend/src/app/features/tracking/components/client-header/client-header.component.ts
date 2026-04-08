import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-client-header',
  standalone: true,
  imports: [DatePipe, MatIconModule, TranslocoPipe],
  template: `
    <div class="header-card">
      <div class="avatar">{{ initials() }}</div>
      <div class="info">
        <div class="name">{{ clientName() }}</div>
        <div class="since">{{ 'tracking.clientSince' | transloco }} {{ createdAt() | date:'MMMM yyyy' }}</div>
        <div class="badges">
          <span class="badge visits">{{ visitCount() }} {{ 'tracking.visits' | transloco }}</span>
          @if (visitCount() >= 5) {
            <span class="badge loyal">{{ 'tracking.loyal' | transloco }}</span>
          }
        </div>
      </div>
    </div>
    @if (allergies()) {
      <div class="allergy-alert">
        <mat-icon>warning</mat-icon>
        <div>
          <span class="alert-label">{{ 'tracking.allergiesAlert' | transloco }}</span>
          <span class="alert-text">{{ allergies() }}</span>
        </div>
      </div>
    }
  `,
  styles: `
    .header-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 1px 4px rgba(0,0,0,0.06);
      padding: 14px;
      display: flex;
      gap: 14px;
      align-items: center;
      margin-bottom: 10px;
    }
    .avatar {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      background: #c06;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      font-weight: 700;
      color: white;
      flex-shrink: 0;
    }
    .info { flex: 1; }
    .name { font-size: 16px; font-weight: 700; color: #1a1a2e; }
    .since { font-size: 11px; color: #6b7280; margin-top: 2px; }
    .badges { display: flex; gap: 6px; margin-top: 6px; }
    .badge {
      font-size: 10px;
      padding: 2px 8px;
      border-radius: 8px;
      font-weight: 600;
    }
    .badge.visits { background: #fdf2f8; color: #c06; }
    .badge.loyal { background: #f3e8ff; color: #7b2cbf; }
    .allergy-alert {
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 10px;
      padding: 10px 14px;
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 10px;
      mat-icon { color: #dc2626; font-size: 20px; }
    }
    .alert-label { font-size: 11px; font-weight: 600; color: #dc2626; }
    .alert-text { font-size: 12px; color: #7f1d1d; margin-left: 4px; }
  `
})
export class ClientHeaderComponent {
  clientName = input.required<string>();
  allergies = input<string | null>(null);
  visitCount = input<number>(0);
  createdAt = input.required<string>();

  initials = computed(() => {
    const name = this.clientName();
    const parts = name.split(' ');
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.substring(0, 2).toUpperCase();
  });
}
