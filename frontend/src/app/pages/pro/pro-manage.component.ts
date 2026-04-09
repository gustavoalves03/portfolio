import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

interface ManageCard {
  labelKey: string;
  descKey: string;
  icon: string;
  path: string;
}

@Component({
  selector: 'app-pro-manage',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  template: `
    <div class="manage-page">
      <h1 class="page-title">{{ 'pro.manage.title' | transloco }}</h1>

      <div class="cards-grid">
        @for (card of cards; track card.path) {
          <button type="button" class="manage-card" (click)="navigate(card.path)">
            <mat-icon class="card-icon">{{ card.icon }}</mat-icon>
            <div class="card-text">
              <span class="card-title">{{ card.labelKey | transloco }}</span>
              <span class="card-desc">{{ card.descKey | transloco }}</span>
            </div>
          </button>
        }
      </div>
    </div>
  `,
  styles: `
    .manage-page {
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

    .cards-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }

    .manage-card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 10px;
      padding: 20px 16px;
      background: #fff;
      border: none;
      border-radius: 12px;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
      cursor: pointer;
      text-align: left;
      transition: box-shadow 150ms, transform 150ms;

      &:hover {
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        transform: translateY(-1px);
      }

      &:active {
        transform: translateY(0);
      }
    }

    .card-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
      color: #c06;
    }

    .card-text {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .card-title {
      font-size: 14px;
      font-weight: 600;
      color: #222;
    }

    .card-desc {
      font-size: 11px;
      color: #888;
    }
  `,
})
export class ProManageComponent {
  private readonly router = inject(Router);

  readonly cards: ManageCard[] = [
    { labelKey: 'pro.manage.planning', descKey: 'pro.manage.planningDesc', icon: 'calendar_today', path: '/pro/planning' },
    { labelKey: 'pro.manage.team', descKey: 'pro.manage.teamDesc', icon: 'people', path: '/pro/employees' },
    { labelKey: 'pro.manage.cares', descKey: 'pro.manage.caresDesc', icon: 'spa', path: '/pro/cares' },
    { labelKey: 'pro.manage.stats', descKey: 'pro.manage.statsDesc', icon: 'bar_chart', path: '/pro/dashboard' },
    { labelKey: 'pro.manage.settings', descKey: 'pro.manage.settingsDesc', icon: 'settings', path: '/pro/settings' },
  ];

  navigate(path: string): void {
    this.router.navigate([path]);
  }
}
