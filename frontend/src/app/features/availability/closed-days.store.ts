import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, mergeMap, pipe, tap } from 'rxjs';
import { ClosedDaysService } from './closed-days.service';

type ClosedDaysState = {
  closedDays: Set<string>;
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
    closedDays: new Set<string>(),
    loadedMonths: new Set<string>(),
  }),
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
            tap((dates) => {
              const closed = new Set(store.closedDays());
              dates.forEach((d) => closed.add(d));
              patchState(store, { closedDays: closed });
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
        closedDays: new Set<string>(),
        loadedMonths: new Set<string>(),
      });
    },
  }))
);
