import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { ClientInvoicesStore } from '../store/client-invoices.store';
import { ClientInvoiceStatus } from '../models/client-invoice.model';

@Component({
  selector: 'app-client-invoices-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, TranslocoPipe,
  ],
  providers: [ClientInvoicesStore],
  templateUrl: './client-invoices-list.component.html',
  styleUrls: ['./client-invoices-list.component.scss'],
})
export class ClientInvoicesListComponent implements OnInit {
  protected readonly store = inject(ClientInvoicesStore);

  q = signal<string>('');
  status = signal<ClientInvoiceStatus | null>(null);

  readonly statuses: ClientInvoiceStatus[] = ['PAID', 'REFUNDED', 'FAILED', 'PENDING'];
  readonly displayedColumns = ['numberLabel', 'issuedAt', 'kind', 'amountTotal', 'status', 'actions'];

  ngOnInit() { this.store.list({}); }
  applyFilters() { this.store.list({ status: this.status() ?? undefined, q: this.q().trim() || undefined }); }
  pdf(id: number, label: string) { this.store.downloadPdf(id, label); }
}
