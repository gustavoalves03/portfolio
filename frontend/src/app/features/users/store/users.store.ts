import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { UsersService } from '../services/users.service';
import { CreateUserRequest, User } from '../models/users.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { exhaustMap, finalize, pipe, switchMap, tap } from 'rxjs';

type UsersState = {
  users: User[];
  loading: boolean;
  error: string | null;
};

export const UsersStore = signalStore(
  withState<UsersState>({ users: [], loading: false, error: null }),
  withMethods((store, gateway = inject(UsersService)) => ({
    getUsers: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap(() => gateway.list({ page: 0, size: 50 })),
        tap({
          next: (page) => patchState(store, { users: page.content }),
          error: (err) => patchState(store, { error: err?.message ?? 'Erreur de chargement' }),
        }),
        finalize(() => patchState(store, { loading: false }))
      )
    ),
    createUser: rxMethod<CreateUserRequest>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        exhaustMap((payload) => gateway.create(payload)),
        tap({
          next: (created) => patchState(store, { users: [...store.users(), created] }),
          error: (err) => patchState(store, { error: err?.message ?? 'Erreur à la création' }),
        }),
        finalize(() => patchState(store, { loading: false }))
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getUsers();
    },
  }))
);
