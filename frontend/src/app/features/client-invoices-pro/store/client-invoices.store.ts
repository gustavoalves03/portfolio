import { inject } from '@angular/core';
import { signalStore, patchState, withMethods, withState, withHooks } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap, catchError, EMPTY } from 'rxjs';
import { ClientInvoicesService } from '../services/client-invoices.service';
import { ClientInvoice, ClientInvoiceStatus } from '../models/client-invoice.model';
import { setError, setFulfilled, setPending, withRequestStatus } from '../../../shared/features/request.status.feature';

type ClientInvoicesState = {
  invoices: ClientInvoice[];
  totalElements: number;
};

export const ClientInvoicesStore = signalStore(
  withState<ClientInvoicesState>({ invoices: [], totalElements: 0 }),
  withRequestStatus(),
  withMethods((store, svc = inject(ClientInvoicesService)) => ({
    list: rxMethod<{ status?: ClientInvoiceStatus; year?: number; q?: string; page?: number; size?: number } | void>(
      pipe(
        tap(() => patchState(store, setPending())),
        switchMap((opts) =>
          svc.list(opts ?? {}).pipe(
            tap((page) => patchState(store, { invoices: page.content, totalElements: page.totalElements }, setFulfilled())),
            catchError((err) => {
              patchState(store, setError(err?.message ?? 'invoices.common.loadError'));
              return EMPTY;
            })
          )
        )
      )
    ),
    downloadPdf(id: number, numberLabel: string) {
      svc.downloadPdf(id).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `facture-${numberLabel}.pdf`;
          a.click();
          URL.revokeObjectURL(url);
        },
        error: (err) => patchState(store, setError(err?.message ?? 'invoices.common.pdfError')),
      });
    },
  })),
  withHooks(() => ({}))
);
