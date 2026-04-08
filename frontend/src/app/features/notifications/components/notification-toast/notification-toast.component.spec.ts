import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { NotificationToastComponent } from './notification-toast.component';
import { NotificationsStore } from '../../store/notifications.store';
import { NotificationResponse } from '../../models/notification.model';

const mockNotification: NotificationResponse = {
  id: 1,
  type: 'NEW_BOOKING',
  category: 'BOOKING',
  title: 'Nouveau RDV',
  message: 'Marie - Soin visage',
  referenceId: 100,
  referenceType: 'BOOKING',
  read: false,
  tenantSlug: 'salon-a',
  createdAt: '2026-04-08T14:30:00',
};

describe('NotificationToastComponent', () => {
  let fixture: ComponentFixture<NotificationToastComponent>;
  let latestNotificationSignal: ReturnType<typeof signal<NotificationResponse | null>>;
  let mockStore: any;

  beforeEach(async () => {
    latestNotificationSignal = signal<NotificationResponse | null>(null);

    mockStore = {
      latestNotification: latestNotificationSignal.asReadonly(),
      clearLatestNotification: jasmine.createSpy('clearLatestNotification'),
    };

    await TestBed.configureTestingModule({
      imports: [NotificationToastComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
      ],
    })
      .overrideComponent(NotificationToastComponent, {
        set: {
          providers: [{ provide: NotificationsStore, useValue: mockStore }],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(NotificationToastComponent);
  });

  it('should not show toast when no notification', () => {
    fixture.detectChanges();
    const toast = fixture.nativeElement.querySelector('.toast');
    expect(toast).toBeFalsy();
  });

  it('should show toast when latestNotification exists', () => {
    latestNotificationSignal.set(mockNotification);
    fixture.detectChanges();
    const toast = fixture.nativeElement.querySelector('.toast');
    expect(toast).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.toast-title').textContent).toContain('Nouveau RDV');
  });

  it('dismiss clears the latest notification', () => {
    fixture.detectChanges();
    fixture.componentInstance.dismiss();
    expect(mockStore.clearLatestNotification).toHaveBeenCalled();
  });
});
