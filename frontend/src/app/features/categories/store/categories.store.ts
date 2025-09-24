import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { CategoriesService } from '../services/categories.service';
import { Category, CreateCategoryRequest } from '../models/categories.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { exhaustMap, finalize, pipe, switchMap, tap } from 'rxjs';

type CategoriesState = {
  categories: Category[];
  loading: boolean;
  error: string | null;
};

export const CategoriesStore = signalStore(
  withState<CategoriesState>({ categories: [], loading: false, error: null }),
  withMethods((store, gateway = inject(CategoriesService)) => ({
    getCategories: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap(() => gateway.list({ page: 0, size: 50 })),
        tap({
          next: (page) => patchState(store, { categories: page.content }),
          error: (err) =>
            patchState(store, { error: err?.message ?? 'Erreur de chargement des catégories' }),
        }),
        finalize(() => patchState(store, { loading: false }))
      )
    ),
    createCategory: rxMethod<CreateCategoryRequest>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        exhaustMap((payload) => gateway.create(payload)),
        tap({
          next: (created) => patchState(store, { categories: [...store.categories(), created] }),
          error: (err) =>
            patchState(store, { error: err?.message ?? 'Erreur lors de la création de catégorie' }),
        }),
        finalize(() => patchState(store, { loading: false }))
      )
    ),
  })),
  withHooks((store) => ({
    onInit() {
      store.getCategories();
    },
  }))
);
