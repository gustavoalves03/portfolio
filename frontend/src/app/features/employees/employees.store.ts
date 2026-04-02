import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { catchError, EMPTY, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { Employee, CreateEmployeeRequest, UpdateEmployeeRequest } from './employees.model';
import { EmployeesService } from './employees.service';
import {
  setError,
  setFulfilled,
  setPending,
  withRequestStatus,
} from '../../shared/features/request.status.feature';

type EmployeesState = {
  employees: Employee[];
};

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (typeof error.error === 'string' && error.error) {
      return error.error;
    }
    if (error.error?.error) {
      return error.error.error;
    }
    if (error.error?.detail) {
      return error.error.detail;
    }
    if (error.error?.message) {
      return error.error.message;
    }
    return error.message ?? fallback;
  }

  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
    return error.message;
  }

  return fallback;
}

export const EmployeesStore = signalStore(
  withState<EmployeesState>({ employees: [] }),
  withRequestStatus(),
  withMethods((store, service = inject(EmployeesService)) => ({
    loadEmployees: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() =>
          service.list().pipe(
            tap((employees) => patchState(store, { employees }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Error loading employees')));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
    createEmployee: rxMethod<CreateEmployeeRequest>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((req) =>
          service.create(req).pipe(
            tap((created) =>
              patchState(
                store,
                { employees: [...store.employees(), created] },
                setFulfilled(),
              ),
            ),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Error creating employee')));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
    updateEmployee: rxMethod<{ id: number; req: UpdateEmployeeRequest }>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap(({ id, req }) =>
          service.update(id, req).pipe(
            tap((updated) =>
              patchState(
                store,
                {
                  employees: store.employees().map((e) => (e.id === id ? updated : e)),
                },
                setFulfilled(),
              ),
            ),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Error updating employee')));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
    deleteEmployee: rxMethod<number>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((id) =>
          service.delete(id).pipe(
            tap(() =>
              patchState(
                store,
                { employees: store.employees().filter((e) => e.id !== id) },
                setFulfilled(),
              ),
            ),
            catchError((err) => {
              patchState(store, setError(extractErrorMessage(err, 'Error deleting employee')));
              return EMPTY;
            }),
          ),
        ),
      ),
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.loadEmployees();
    },
  })),
);
