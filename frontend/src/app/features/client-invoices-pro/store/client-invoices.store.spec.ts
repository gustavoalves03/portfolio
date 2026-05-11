import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of } from 'rxjs';
import { ClientInvoicesStore } from './client-invoices.store';
import { ClientInvoicesService } from '../services/client-invoices.service';
import { ClientInvoice } from '../models/client-invoice.model';

describe('ClientInvoicesStore', () => {
  const sample: ClientInvoice = {
    id: 1,
    numberLabel: 'DEMO-2026-0001',
    issuedAt: '2026-05-11T12:00:00',
    kind: 'NO_SHOW_FEE',
    amountSubtotal: 25,
    amountTax: 4.25,
    amountTotal: 29.25,
    currency: 'EUR',
    taxRate: 17,
    status: 'PAID',
    bookingId: null,
    clientUserId: 7,
    lines: [],
  };

  let svcMock: jasmine.SpyObj<ClientInvoicesService>;

  beforeEach(() => {
    svcMock = jasmine.createSpyObj('ClientInvoicesService', ['list', 'get', 'downloadPdf']);
    svcMock.list.and.returnValue(of({ content: [sample], totalElements: 1, number: 0, size: 20 }));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        ClientInvoicesStore,
        { provide: ClientInvoicesService, useValue: svcMock },
      ],
    });
  });

  it('list() populates state', () => {
    const store = TestBed.inject(ClientInvoicesStore);
    store.list({});
    expect(store.invoices().length).toBe(1);
    expect(store.invoices()[0].kind).toBe('NO_SHOW_FEE');
  });
});
