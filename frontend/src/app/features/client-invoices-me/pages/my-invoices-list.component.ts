import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { MyInvoicesStore } from '../store/my-invoices.store';
import { MyInvoiceStatus } from '../models/my-invoice.model';

@Component({
  selector: 'app-my-invoices-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatFormFieldModule,
    MatSelectModule, MatButtonModule, MatIconModule, TranslocoPipe,
  ],
  providers: [MyInvoicesStore],
  templateUrl: './my-invoices-list.component.html',
  styleUrls: ['./my-invoices-list.component.scss'],
})
export class MyInvoicesListComponent implements OnInit {
  protected readonly store = inject(MyInvoicesStore);

  status = signal<MyInvoiceStatus | null>(null);
  readonly statuses: MyInvoiceStatus[] = ['PAID', 'REFUNDED', 'FAILED', 'PENDING'];
  readonly displayedColumns = ['numberLabel', 'issuedAt', 'kind', 'amountTotal', 'status', 'actions'];

  ngOnInit() { this.store.list({}); }
  applyFilters() { this.store.list({ status: this.status() ?? undefined }); }
  pdf(id: number, label: string) { this.store.downloadPdf(id, label); }
}
