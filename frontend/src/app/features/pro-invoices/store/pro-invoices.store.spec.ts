import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of } from 'rxjs';
import { ProInvoicesStore } from './pro-invoices.store';
import { ProInvoicesService } from '../services/pro-invoices.service';
import { ProInvoice } from '../models/pro-invoice.model';

describe('ProInvoicesStore', () => {
  const sample: ProInvoice = {
    id: 1,
    numberLabel: 'PRO-2026-0001',
    issuedAt: '2026-05-11T12:00:00',
    periodStart: '2026-05-01',
    periodEnd: '2026-05-31',
    amountSubtotal: 59,
    amountTax: 10.03,
    amountTotal: 69.03,
    currency: 'EUR',
    taxRate: 17,
    status: 'PAID',
    hostedInvoiceUrl: null,
  };

  let svcMock: jasmine.SpyObj<ProInvoicesService>;

  beforeEach(() => {
    svcMock = jasmine.createSpyObj('ProInvoicesService', ['list', 'get', 'downloadPdf']);
    svcMock.list.and.returnValue(of({ content: [sample], totalElements: 1, number: 0, size: 20 }));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        ProInvoicesStore,
        { provide: ProInvoicesService, useValue: svcMock },
      ],
    });
  });

  it('list() populates state and marks fulfilled', () => {
    const store = TestBed.inject(ProInvoicesStore);
    store.list({});
    expect(store.invoices().length).toBe(1);
    expect(store.invoices()[0].numberLabel).toBe('PRO-2026-0001');
  });

  it('downloadPdf() calls service', () => {
    const blob = new Blob(['pdf']);
    svcMock.downloadPdf.and.returnValue(of(blob));

    const fakeAnchor = {
      click: jasmine.createSpy('click'),
      set href(_: string) {},
      set download(_: string) {},
    } as any;
    spyOn(document, 'createElement').and.returnValue(fakeAnchor);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:abc');
    spyOn(URL, 'revokeObjectURL');

    const store = TestBed.inject(ProInvoicesStore);
    store.downloadPdf(1, 'PRO-2026-0001');

    expect(svcMock.downloadPdf).toHaveBeenCalledWith(1);
    expect(fakeAnchor.click).toHaveBeenCalled();
  });
});
