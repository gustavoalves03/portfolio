import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { UsersService } from '../services/users.service';
import { CreateUserRequest, User } from '../models/users.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { exhaustMap, pipe, switchMap, tap } from 'rxjs';
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
  })),
  withHooks((store) => ({
    onInit() {
      store.getUsers();
    },
  }))
);
