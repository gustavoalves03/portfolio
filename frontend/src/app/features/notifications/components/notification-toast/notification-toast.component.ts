import { Component, inject } from '@angular/core';
import { NotificationsStore } from '../../store/notifications.store';

@Component({
  selector: 'app-notification-toast',
  standalone: true,
  imports: [],
  template: `
    @if (notification(); as notif) {
      <div class="toast" (click)="dismiss()">
        <div class="toast-content">
          <div class="toast-title">{{ notif.title }}</div>
          <div class="toast-message">{{ notif.message }}</div>
        </div>
        <button type="button" class="toast-close" (click)="dismiss(); $event.stopPropagation()" aria-label="Fermer">
          &times;
        </button>
      </div>
    }
  `,
  styles: `
    :host {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      z-index: 1000;
      pointer-events: none;
    }

    .toast {
      pointer-events: auto;
      margin: 12px;
      padding: 14px 20px;
      background: white;
      border-bottom: 2px solid #fda4af;
      border-radius: 12px;
      display: flex;
      align-items: center;
      gap: 14px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
      animation: slideDown 250ms ease-out;
      cursor: pointer;
    }

    .toast-content {
      flex: 1;
    }

    .toast-title {
      font-weight: 600;
      color: #1a1a2e;
      font-size: 14px;
    }

    .toast-message {
      color: #6b7280;
      font-size: 13px;
      margin-top: 2px;
    }

    .toast-close {
      background: none;
      border: none;
      color: #9ca3af;
      font-size: 20px;
      cursor: pointer;
      padding: 4px 8px;
      line-height: 1;
    }

    @keyframes slideDown {
      from {
        transform: translateY(-100%);
        opacity: 0;
      }
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }
  `,
})
export class NotificationToastComponent {
  private readonly store = inject(NotificationsStore);
  readonly notification = this.store.latestNotification;

  dismiss(): void {
    this.store.clearLatestNotification();
  }
}
