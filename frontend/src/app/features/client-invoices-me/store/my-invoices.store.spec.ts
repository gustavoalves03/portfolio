import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of } from 'rxjs';
import { MyInvoicesStore } from './my-invoices.store';
import { MyInvoicesService } from '../services/my-invoices.service';
import { MyInvoice } from '../models/my-invoice.model';

describe('MyInvoicesStore', () => {
  const sample: MyInvoice = {
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

  let svcMock: jasmine.SpyObj<MyInvoicesService>;

  beforeEach(() => {
    svcMock = jasmine.createSpyObj('MyInvoicesService', ['list', 'get', 'downloadPdf']);
    svcMock.list.and.returnValue(of({ content: [sample], totalElements: 1, number: 0, size: 20 }));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        MyInvoicesStore,
        { provide: MyInvoicesService, useValue: svcMock },
      ],
    });
  });

  it('list() populates state', () => {
    const store = TestBed.inject(MyInvoicesStore);
    store.list({});
    expect(store.invoices().length).toBe(1);
  });
});
