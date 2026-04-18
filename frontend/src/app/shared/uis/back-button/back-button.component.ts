import { Component, inject, input } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';

@Component({
  selector: 'app-back-button',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  template: `
    <button type="button" class="back-btn" (click)="onClick()">
      <mat-icon>arrow_back</mat-icon>
      @if (showLabel()) {
        <span>{{ 'common.back' | transloco }}</span>
      }
    </button>
  `,
  styles: [`
    .back-btn {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 6px 10px;
      background: transparent;
      border: none;
      color: #666;
      cursor: pointer;
      font-size: 13px;
    }
    .back-btn:hover { color: #c06; }
    .back-btn mat-icon { font-size: 20px; width: 20px; height: 20px; }
  `],
})
export class BackButtonComponent {
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly history = inject(NavigationHistoryService);

  readonly fallbackUrl = input<string>('/');
  readonly showLabel = input<boolean>(true);

  onClick(): void {
    if (this.history.hasInternalHistory()) {
      this.location.back();
    } else {
      this.router.navigate([this.fallbackUrl()]);
    }
  }
}
