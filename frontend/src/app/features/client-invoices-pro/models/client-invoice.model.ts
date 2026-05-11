export type ClientInvoiceStatus = 'PAID' | 'REFUNDED' | 'FAILED' | 'PENDING';
export type ClientInvoiceKind = 'NO_SHOW_FEE' | 'CARE_PAYMENT';

export interface ClientInvoiceLine {
  id: number;
  description: string;
  quantity: number;
  unitPriceHt: number;
  totalHt: number;
  position: number;
}

export interface ClientInvoice {
  id: number;
  numberLabel: string;
  issuedAt: string;
  kind: ClientInvoiceKind;
  amountSubtotal: number;
  amountTax: number;
  amountTotal: number;
  currency: string;
  taxRate: number;
  status: ClientInvoiceStatus;
  bookingId: number | null;
  clientUserId: number | null;
  lines: ClientInvoiceLine[];
}
