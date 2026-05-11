export type ProInvoiceStatus = 'DRAFT' | 'OPEN' | 'PAID' | 'UNCOLLECTIBLE' | 'VOID';

export interface ProInvoice {
  id: number;
  numberLabel: string;
  issuedAt: string;
  periodStart: string | null;
  periodEnd: string | null;
  amountSubtotal: number;
  amountTax: number;
  amountTotal: number;
  currency: string;
  taxRate: number;
  status: ProInvoiceStatus;
  hostedInvoiceUrl: string | null;
}
