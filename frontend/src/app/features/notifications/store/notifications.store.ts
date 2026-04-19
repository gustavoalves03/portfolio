import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap, EMPTY, catchError, Subscription } from 'rxjs';
import { NotificationsService } from '../services/notifications.service';
import { WebSocketService } from '../services/websocket.service';
import { NotificationResponse } from '../models/notification.model';

const PAGE_SIZE = 20;
const RECENT_WINDOW_MS = 48 * 60 * 60 * 1000;

type NotificationsState = {
  notifications: NotificationResponse[];
  unreadCount: number;
  latestNotification: NotificationResponse | null;
  page: number;
  hasMore: boolean;
  mode: 'recent' | 'full';
};

export const NotificationsStore = signalStore(
  withState<NotificationsState>({
    notifications: [],
    unreadCount: 0,
    latestNotification: null,
    page: 0,
    hasMore: false,
    mode: 'recent',
  }),
  withComputed((store) => ({
    hasUnread: computed(() => store.unreadCount() > 0),
    badgeLabel: computed(() => {
      const count = store.unreadCount();
      if (count === 0) return '';
      if (count > 99) return '99+';
      return count.toString();
    }),
    emptyState: computed(() => store.notifications().length === 0 && store.mode() === 'full'),
  })),
  withMethods((store,
    notificationsService = inject(NotificationsService),
    webSocketService = inject(WebSocketService),
  ) => {
    // Tracks the active WebSocket notification subscription so we can dispose of
    // it cleanly and guarantee `connectWebSocket()` can be called more than once
    // without double-subscribing (which would duplicate every inbound event).
    let wsSubscription: Subscription | null = null;
    return {
    loadUnreadCount: rxMethod<void>(
      pipe(
        switchMap(() => notificationsService.unreadCount()),
        tap((count) => patchState(store, { unreadCount: count })),
        catchError(() => EMPTY)
      )
    ),
    loadInitial(): void {
      const since = new Date(Date.now() - RECENT_WINDOW_MS).toISOString();
      patchState(store, { notifications: [], page: 0, mode: 'recent', hasMore: false });
      notificationsService.list({ read: false, since, page: 0, size: PAGE_SIZE }).subscribe({
        next: (res: any) => {
          patchState(store, {
            notifications: res.content,
            page: 0,
            hasMore: !res.last,
          });
        },
        error: () => {},
      });
    },
    loadNextPage(): void {
      if (store.hasMore()) {
        const nextPage = store.page() + 1;
        const args: any = { read: false, page: nextPage, size: PAGE_SIZE };
        if (store.mode() === 'recent') {
          args.since = new Date(Date.now() - RECENT_WINDOW_MS).toISOString();
        }
        notificationsService.list(args).subscribe({
          next: (res: any) => {
            patchState(store, {
              notifications: [...store.notifications(), ...res.content],
              page: nextPage,
              hasMore: !res.last,
            });
          },
          error: () => {},
        });
        return;
      }
      if (store.mode() === 'recent') {
        patchState(store, { mode: 'full', page: 0 });
        notificationsService.list({ read: false, page: 0, size: PAGE_SIZE }).subscribe({
          next: (res: any) => {
            const existingIds = new Set(store.notifications().map((n) => n.id));
            const merged = [
              ...store.notifications(),
              ...res.content.filter((n: NotificationResponse) => !existingIds.has(n.id)),
            ];
            patchState(store, {
              notifications: merged,
              page: 0,
              hasMore: !res.last,
            });
          },
          error: () => {},
        });
      }
    },
    markAsRead: rxMethod<number>(
      pipe(
        switchMap((id) =>
          notificationsService.markAsRead(id).pipe(
            tap(() =>
              patchState(store, {
                notifications: store.notifications().map((n) =>
                  n.id === id ? { ...n, read: true } : n
                ),
                unreadCount: Math.max(0, store.unreadCount() - 1),
              })
            ),
            catchError(() => EMPTY)
          )
        )
      )
    ),
    dismiss(id: number): void {
      const item = store.notifications().find((n) => n.id === id);
      const wasUnread = item ? !item.read : false;
      notificationsService.markAsRead(id).subscribe({
        next: () => {
          patchState(store, {
            notifications: store.notifications().filter((n) => n.id !== id),
            unreadCount: wasUnread ? Math.max(0, store.unreadCount() - 1) : store.unreadCount(),
          });
        },
        error: () => {},
      });
    },
    connectWebSocket(): void {
      // Defensive: tear down any existing subscription before connecting again.
      // Otherwise a second call would double-subscribe and every notification
      // would be patched into state twice.
      wsSubscription?.unsubscribe();
      webSocketService.connect();
      wsSubscription = webSocketService.notification$.subscribe((notification) => {
        patchState(store, {
          notifications: [notification, ...store.notifications()],
          unreadCount: store.unreadCount() + 1,
          latestNotification: notification,
        });
        setTimeout(() => {
          if (store.latestNotification()?.id === notification.id) {
            patchState(store, { latestNotification: null });
          }
        }, 4000);
      });
    },
    disconnectWebSocket(): void {
      wsSubscription?.unsubscribe();
      wsSubscription = null;
      webSocketService.disconnect();
    },
    clearLatestNotification(): void {
      patchState(store, { latestNotification: null });
    },
    reset(): void {
      patchState(store, { notifications: [], unreadCount: 0, latestNotification: null, page: 0, hasMore: false, mode: 'recent' });
    },
  };
  }),
);
