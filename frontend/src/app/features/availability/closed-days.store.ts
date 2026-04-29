import { computed } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, mergeMap, pipe, tap } from 'rxjs';
import { ClosedDay, ClosedDayReason, ClosedDaysService } from './closed-days.service';

type ClosedDaysState = {
  byDate: Record<string, ClosedDayReason>;
  loadedMonths: Set<string>;
};

const monthKey = (year: number, month: number): string =>
  `${year}-${String(month).padStart(2, '0')}`;

const monthRange = (year: number, month: number): { from: string; to: string } => {
  const m = String(month).padStart(2, '0');
  const lastDay = new Date(year, month, 0).getDate();
  return {
    from: `${year}-${m}-01`,
    to: `${year}-${m}-${String(lastDay).padStart(2, '0')}`,
  };
};

export const ClosedDaysStore = signalStore(
  { providedIn: 'root' },
  withState<ClosedDaysState>({
    byDate: {},
    loadedMonths: new Set<string>(),
  }),
  withComputed((store) => ({
    closedDays: computed(() => new Set(Object.keys(store.byDate()))),
    holidayDays: computed(() => {
      const map = store.byDate();
      const result = new Set<string>();
      for (const [date, reason] of Object.entries(map)) {
        if (reason === 'HOLIDAY') result.add(date);
      }
      return result;
    }),
  })),
  withMethods((store, service = inject(ClosedDaysService)) => ({
    loadMonth: rxMethod<{ year: number; month: number }>(
      pipe(
        mergeMap(({ year, month }) => {
          const key = monthKey(year, month);
          if (store.loadedMonths().has(key)) return EMPTY;
          const months = new Set(store.loadedMonths());
          months.add(key);
          patchState(store, { loadedMonths: months });
          const { from, to } = monthRange(year, month);
          return service.loadClosedDays(from, to).pipe(
            tap((days: ClosedDay[]) => {
              const next = { ...store.byDate() };
              for (const d of days) next[d.date] = d.reason;
              patchState(store, { byDate: next });
            }),
            catchError(() => {
              const rollback = new Set(store.loadedMonths());
              rollback.delete(key);
              patchState(store, { loadedMonths: rollback });
              return EMPTY;
            })
          );
        })
      )
    ),
    invalidate(): void {
      patchState(store, {
        byDate: {},
        loadedMonths: new Set<string>(),
      });
    },
  }))
);
