import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { UsersService } from '../services/users.service';
import { CreateUserRequest, UpdateUserRequest, User } from '../models/users.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, exhaustMap, pipe, switchMap, tap } from 'rxjs';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request.status.feature';

type UsersState = {
  users: User[];
};

export const UsersStore = signalStore(
  withState<UsersState>({ users: [] }),
  withRequestStatus(),
  withMethods((store, gateway = inject(UsersService)) => ({
    getUsers: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() => gateway.list({ page: 0, size: 50 })),
        tap({
          next: (page) => patchState(store, { users: page.content }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Erreur de chargement')),
        })
      )
    ),
    createUser: rxMethod<CreateUserRequest>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((payload) => gateway.create(payload)),
        tap({
          next: (created) => patchState(store, { users: [...store.users(), created] }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Erreur à la création')),
        })
      )
    ),
    updateUser: rxMethod<{ id: number; payload: UpdateUserRequest }>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap(({ id, payload }) =>
          gateway.update(id, payload).pipe(
            tap((updatedUser) =>
              patchState(
                store,
                {
                  users: store.users().map((user) => (user.id === id ? updatedUser : user))
                },
                setFulfilled()
              )
            ),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Erreur lors de la modification de l\'utilisateur'));
              return EMPTY;
            })
          )
        )
      )
    ),
    deleteUser: rxMethod<number>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((id) =>
          gateway.delete(id).pipe(
            tap(() =>
              patchState(
                store,
                {
                  users: store.users().filter((user) => user.id !== id)
                },
                setFulfilled()
              )
            ),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'Erreur lors de la suppression de l\'utilisateur'));
              return EMPTY;
            })
          )
        )
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getUsers();
    },
  }))
);
