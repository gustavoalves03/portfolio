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
import { ProInvoicesStore } from '../store/pro-invoices.store';
import { ProInvoiceStatus } from '../models/pro-invoice.model';

@Component({
  selector: 'app-pro-invoices-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, TranslocoPipe,
  ],
  providers: [ProInvoicesStore],
  templateUrl: './pro-invoices-list.component.html',
  styleUrls: ['./pro-invoices-list.component.scss'],
})
export class ProInvoicesListComponent implements OnInit {
  protected readonly store = inject(ProInvoicesStore);

  q = signal<string>('');
  status = signal<ProInvoiceStatus | null>(null);
  year = signal<number | null>(null);

  readonly statuses: ProInvoiceStatus[] = ['DRAFT', 'OPEN', 'PAID', 'UNCOLLECTIBLE', 'VOID'];
  readonly displayedColumns = ['numberLabel', 'issuedAt', 'period', 'amountTotal', 'status', 'actions'];

  ngOnInit(): void {
    this.store.list({});
  }

  applyFilters(): void {
    this.store.list({
      status: this.status() ?? undefined,
      year: this.year() ?? undefined,
      q: this.q().trim() || undefined,
    });
  }

  pdf(id: number, label: string): void {
    this.store.downloadPdf(id, label);
  }
}
