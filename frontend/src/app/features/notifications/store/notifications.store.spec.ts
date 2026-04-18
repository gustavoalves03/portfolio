import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of, Subject } from 'rxjs';
import { NotificationsStore } from './notifications.store';
import { NotificationsService } from '../services/notifications.service';
import { WebSocketService } from '../services/websocket.service';
import { NotificationResponse } from '../models/notification.model';

function notif(id: number, read = false, createdAt = '2026-04-18T10:00:00Z'): NotificationResponse {
  return {
    id, type: 'BOOKING_CREATED',
    category: 'INFO',
    title: `N${id}`, message: 'm',
    referenceId: id, referenceType: 'BOOKING',
    tenantSlug: 'test-salon',
    read, createdAt,
  };
}

describe('NotificationsStore', () => {
  let service: jasmine.SpyObj<NotificationsService>;
  let ws: jasmine.SpyObj<WebSocketService>;
  let store: InstanceType<typeof NotificationsStore>;

  beforeEach(() => {
    service = jasmine.createSpyObj<NotificationsService>('NotificationsService', ['list', 'unreadCount', 'markAsRead']);
    ws = jasmine.createSpyObj<WebSocketService>('WebSocketService', ['connect', 'disconnect'], { notification$: new Subject() as any });

    service.list.and.returnValue(of({
      content: [notif(1), notif(2)],
      totalElements: 2, totalPages: 1,
      last: true,
    } as any));
    service.markAsRead.and.returnValue(of(undefined as any));
    service.unreadCount.and.returnValue(of(2));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        NotificationsStore,
        { provide: NotificationsService, useValue: service },
        { provide: WebSocketService, useValue: ws },
      ],
    });
    store = TestBed.inject(NotificationsStore);
  });

  it('loadInitial calls service with since=now-48h, page=0, size=20 and sets mode=recent', () => {
    store.loadInitial();

    const args = service.list.calls.mostRecent().args[0] as { page: number; size: number; since?: string };
    expect(args.page).toBe(0);
    expect(args.size).toBe(20);
    expect(args.since).toBeDefined();

    const sinceMs = new Date(args.since!).getTime();
    const expected = Date.now() - 48 * 60 * 60 * 1000;
    expect(Math.abs(sinceMs - expected)).toBeLessThan(5000);

    expect(store.mode()).toBe('recent');
    expect(store.notifications().length).toBe(2);
    expect(store.page()).toBe(0);
    expect(store.hasMore()).toBeFalse();
  });

  it('loadNextPage is a no-op when hasMore is false and mode is full', () => {
    service.list.and.returnValue(of({
      content: [notif(1), notif(2)],
      totalElements: 2, totalPages: 1,
      last: true,
    } as any));
    store.loadInitial();
    // Trigger switch to full mode (recent + hasMore=false → switches to full)
    store.loadNextPage();
    service.list.calls.reset();
    // Now in full mode with hasMore=false; should be no-op
    store.loadNextPage();
    expect(service.list).not.toHaveBeenCalled();
  });

  it('loadNextPage increments page and concats items when hasMore is true', () => {
    service.list.and.returnValue(of({
      content: [notif(1), notif(2)],
      totalElements: 4, totalPages: 2,
      last: false,
    } as any));
    store.loadInitial();
    expect(store.hasMore()).toBeTrue();

    service.list.and.returnValue(of({
      content: [notif(3), notif(4)],
      totalElements: 4, totalPages: 2,
      last: true,
    } as any));
    store.loadNextPage();

    expect(store.notifications().length).toBe(4);
    expect(store.page()).toBe(1);
    expect(store.hasMore()).toBeFalse();
  });

  it('loadNextPage in recent with hasMore=false switches to full mode and loads without since', () => {
    service.list.and.returnValue(of({
      content: [notif(1), notif(2)],
      totalElements: 2, totalPages: 1,
      last: true,
    } as any));
    store.loadInitial();
    expect(store.mode()).toBe('recent');
    expect(store.hasMore()).toBeFalse();

    service.list.and.returnValue(of({
      content: [notif(1), notif(3), notif(4)],  // id=1 overlaps
      totalElements: 3, totalPages: 1,
      last: true,
    } as any));
    store.loadNextPage();

    expect(store.mode()).toBe('full');
    const args = service.list.calls.mostRecent().args[0] as { since?: string };
    expect(args.since).toBeUndefined();
    const ids = store.notifications().map(n => n.id).sort();
    expect(ids).toEqual([1, 2, 3, 4]);
  });

  it('dismiss removes item and decrements unreadCount when item was unread', () => {
    store.loadInitial();
    store.loadUnreadCount();
    const initialCount = store.unreadCount();

    store.dismiss(1);

    expect(service.markAsRead).toHaveBeenCalledWith(1);
    expect(store.notifications().map(n => n.id)).toEqual([2]);
    expect(store.unreadCount()).toBe(initialCount - 1);
  });

  it('dismiss does not decrement unreadCount when item was already read', () => {
    service.list.and.returnValue(of({
      content: [notif(1, true), notif(2, false)],
      totalElements: 2, totalPages: 1,
      last: true,
    } as any));
    store.loadInitial();
    store.loadUnreadCount();
    const initialCount = store.unreadCount();

    store.dismiss(1);
    expect(store.unreadCount()).toBe(initialCount);
  });
});
