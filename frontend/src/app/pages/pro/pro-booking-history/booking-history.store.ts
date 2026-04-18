import { computed, inject } from '@angular/core';
import {
  patchState,
  signalStore,
  withComputed,
  withHooks,
  withMethods,
  withState,
} from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { debounceTime, distinctUntilChanged, pipe, tap } from 'rxjs';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../../shared/features/request.status.feature';
import { BookingsService } from '../../../features/bookings/services/bookings.service';
import { SalonClientService } from '../../../features/salon-clients/salon-client.service';
import {
  BookingFilters,
  CareBookingDetailed,
  CareBookingStatus,
} from '../../../features/bookings/models/bookings.model';

export interface HistoryFilters {
  statuses: CareBookingStatus[];
  from: string; // YYYY-MM-DD
  to: string; // YYYY-MM-DD
  clientQuery: string;
  employeeId: number | null;
}

interface HistoryState {
  items: CareBookingDetailed[];
  page: number;
  size: number;
  hasMore: boolean;
  filters: HistoryFilters;
}

interface DayGroup {
  date: string;
  label: string;
  items: CareBookingDetailed[];
}

const PAGE_SIZE = 20;
const ALL_STATUSES: CareBookingStatus[] = [
  CareBookingStatus.CONFIRMED,
  CareBookingStatus.PENDING,
  CareBookingStatus.CANCELLED,
  CareBookingStatus.NO_SHOW,
];

function toYMD(d: Date): string {
  const y = d.getFullYear();
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function today(): string {
  return toYMD(new Date());
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return toYMD(d);
}

function initialFilters(): HistoryFilters {
  return {
    statuses: [...ALL_STATUSES],
    from: daysAgo(30),
    to: today(),
    clientQuery: '',
    employeeId: null,
  };
}

function formatDay(ymd: string): string {
  const [y, m, d] = ymd.split('-');
  return `${d}/${m}/${y}`;
}

function groupByDay(items: CareBookingDetailed[]): DayGroup[] {
  const groups = new Map<string, CareBookingDetailed[]>();
  for (const b of items) {
    const arr = groups.get(b.appointmentDate) ?? [];
    arr.push(b);
    groups.set(b.appointmentDate, arr);
  }
  return Array.from(groups.entries())
    .sort((a, b) => (a[0] < b[0] ? 1 : -1))
    .map(([date, dayItems]) => ({
      date,
      label: formatDay(date),
      items: [...dayItems].sort((a, b) => (a.appointmentTime < b.appointmentTime ? 1 : -1)),
    }));
}

export const BookingHistoryStore = signalStore(
  withState<HistoryState>({
    items: [],
    page: 0,
    size: PAGE_SIZE,
    hasMore: false,
    filters: initialFilters(),
  }),
  withRequestStatus(),
  withComputed((store) => ({
    emptyState: computed(() => store.items().length === 0),
    groupedByDay: computed(() => groupByDay(store.items())),
  })),
  withMethods(
    (
      store,
      bookings = inject(BookingsService),
      _salonClients = inject(SalonClientService),
    ) => ({
      updateFilters(partial: Partial<HistoryFilters>): void {
        const nextFilters = { ...store.filters(), ...partial };
        patchState(store, {
          filters: nextFilters,
          items: [],
          page: 0,
          hasMore: false,
        });
        loadPage(store, bookings, 0);
      },
      loadNextPage(): void {
        if (!store.hasMore() || store.isPending()) return;
        loadPage(store, bookings, store.page() + 1);
      },
      searchClient: rxMethod<string>(
        pipe(
          debounceTime(300),
          distinctUntilChanged(),
          tap((query) => {
            patchState(store, {
              filters: { ...store.filters(), clientQuery: query },
              items: [],
              page: 0,
              hasMore: false,
            });
            loadPage(store, bookings, 0);
          }),
        ),
      ),
    }),
  ),
  withHooks({
    onInit(store) {
      (store as unknown as { updateFilters: (p: Partial<HistoryFilters>) => void }).updateFilters(
        {},
      );
    },
  }),
);

function loadPage(
  store: {
    filters: () => HistoryFilters;
    items: () => CareBookingDetailed[];
  } & Record<string, unknown>,
  bookings: BookingsService,
  pageNum: number,
): void {
  const f = store.filters();
  const apiFilters: BookingFilters = {
    from: f.from,
    to: f.to,
    ...(f.employeeId ? { userId: f.employeeId } : {}),
  };

  const onlyOneStatus = f.statuses.length === 1;
  if (onlyOneStatus) {
    apiFilters.status = f.statuses[0];
  }

  patchState(store as any, setPending());

  bookings
    .listDetailed(apiFilters, { page: pageNum, size: PAGE_SIZE, sort: 'appointmentDate,desc' })
    .subscribe({
      next: (res) => {
        let content = res.content;
        // Client-side status filter when 2 or 3 statuses selected
        if (f.statuses.length >= 2 && f.statuses.length <= 3) {
          content = content.filter((b) => f.statuses.includes(b.status));
        }
        // Client-side client name filter
        if (f.clientQuery && f.clientQuery.length > 0) {
          const q = f.clientQuery.toLowerCase();
          content = content.filter((b) =>
            (b.salonClientName ?? b.user.name).toLowerCase().includes(q),
          );
        }
        const newItems = pageNum === 0 ? content : [...store.items(), ...content];
        patchState(
          store as any,
          {
            items: newItems,
            page: pageNum,
            hasMore: !res.last,
          },
          setFulfilled(),
        );
      },
      error: (err) =>
        patchState(store as any, setError((err && err.message) ?? 'error')),
    });
}
