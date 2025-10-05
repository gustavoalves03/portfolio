import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { CategoriesService } from '../services/categories.service';
import { Category, CreateCategoryRequest, UpdateCategoryRequest } from '../models/categories.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { exhaustMap, map, pipe, switchMap, tap } from 'rxjs';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request.status.feature';

type CategoriesState = {
  categories: Category[];
};

export const CategoriesStore = signalStore(
  withState<CategoriesState>({ categories: [] }),
  withRequestStatus(),
  withMethods((store, gateway = inject(CategoriesService)) => ({
    getCategories: rxMethod<void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap(() => gateway.list({ page: 0, size: 50 })),
        tap({
          next: (page) => patchState(store, { categories: page.content }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Erreur de chargement des catégories')),
        })
      )
    ),
    createCategory: rxMethod<CreateCategoryRequest>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((payload) => gateway.create(payload)),
        tap({
          next: (created) => patchState(store, { categories: [...store.categories(), created] }, setFulfilled()),
          error: (err) => patchState(store, setError(err?.message ?? 'Erreur lors de la création de catégorie')),
        })
      )
    ),
    updateCategory: rxMethod<{ id: number; payload: UpdateCategoryRequest }>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap(({ id, payload }) => gateway.update(id, payload)),
        tap({
          next: (updated) =>
            patchState(
              store,
              {
                categories: store
                  .categories()
                  .map((it) => (it.id === updated.id ? updated : it)),
              },
              setFulfilled()
            ),
          error: (err) =>
            patchState(
              store,
              setError(err?.message ?? 'Erreur lors de la mise à jour de la catégorie')
            ),
        })
      )
    ),
    deleteCategory: rxMethod<number>(
      pipe(
        tap(() => patchState(store, setPending())),
        exhaustMap((id) => gateway.delete(id).pipe(map(() => id))),
        tap({
          next: (deletedId) =>
            patchState(
              store,
              { categories: store.categories().filter((it) => it.id !== deletedId) },
              setFulfilled()
            ),
          error: (err) =>
            patchState(store, setError(err?.message ?? 'Erreur lors de la suppression de la catégorie')),
        })
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getCategories();
    },
  }))
);
