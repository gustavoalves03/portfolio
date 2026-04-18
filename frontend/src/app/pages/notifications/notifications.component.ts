import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, afterNextRender, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationsStore } from '../../features/notifications/store/notifications.store';
import { NotificationResponse } from '../../features/notifications/models/notification.model';
import { NotificationRowComponent } from '../../features/notifications/components/notification-row/notification-row.component';
import { BackButtonComponent } from '../../shared/uis/back-button/back-button.component';

@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [
    TranslocoPipe,
    MatIconModule,
    MatProgressSpinnerModule,
    NotificationRowComponent,
    BackButtonComponent,
  ],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent implements AfterViewInit, OnDestroy {
  protected readonly store = inject(NotificationsStore);
  private readonly router = inject(Router);

  @ViewChild('sentinel') sentinel?: ElementRef<HTMLElement>;
  private observer?: IntersectionObserver;

  constructor() {
    this.store.loadInitial();
    afterNextRender(() => this.setupObserver());
  }

  ngAfterViewInit(): void {
    this.setupObserver();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private setupObserver(): void {
    if (typeof IntersectionObserver === 'undefined' || !this.sentinel) return;
    this.observer?.disconnect();
    this.observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) {
        this.store.loadNextPage();
      }
    }, { rootMargin: '200px' });
    this.observer.observe(this.sentinel.nativeElement);
  }

  protected onRowClick(notification: NotificationResponse): void {
    if (!notification.read) {
      this.store.markAsRead(notification.id);
    }
    this.navigateToReference(notification);
  }

  private navigateToReference(notification: NotificationResponse): void {
    switch (notification.referenceType) {
      case 'BOOKING':
        this.router.navigate(['/pro/bookings'], {
          queryParams: { highlight: notification.referenceId },
        });
        break;
      case 'SALON_CLIENT':
        this.router.navigate(['/pro/clients', notification.referenceId]);
        break;
      default:
        break;
    }
  }
}
