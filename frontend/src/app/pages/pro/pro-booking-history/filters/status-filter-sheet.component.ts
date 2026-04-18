import { Component, inject, signal } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { TranslocoPipe } from '@jsverse/transloco';
import { CareBookingStatus } from '../../../../features/bookings/models/bookings.model';
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';

@Component({
  selector: 'app-status-filter-sheet',
  standalone: true,
  imports: [MatCheckboxModule, TranslocoPipe, SheetHandleComponent],
  template: `
    <app-sheet-handle />
    <div class="sheet-wrap">
      <h3>{{ 'pro.history.filter.status.all' | transloco }}</h3>
      <div class="list">
        @for (s of allStatuses; track s) {
          <mat-checkbox
            [checked]="selected().includes(s)"
            (change)="toggle(s)">
            {{ 'bookings.status.' + s | transloco }}
          </mat-checkbox>
        }
      </div>
      <button class="apply" (click)="apply()">{{ 'common.apply' | transloco }}</button>
    </div>
  `,
  styles: [`
    .sheet-wrap { padding: 16px; display: flex; flex-direction: column; gap: 14px; }
    h3 { margin: 0; font-size: 16px; color: #333; }
    .list { display: flex; flex-direction: column; gap: 10px; }
    .apply {
      margin-top: 8px; padding: 12px; border: none; border-radius: 10px;
      background: #c06; color: white; font-weight: 600; font-size: 14px;
      cursor: pointer;
    }
  `],
})
export class StatusFilterSheetComponent {
  private readonly dialogRef = inject(MatDialogRef<StatusFilterSheetComponent>);
  private readonly data = inject<{ selected: CareBookingStatus[] } | null>(MAT_DIALOG_DATA, { optional: true });

  readonly allStatuses: CareBookingStatus[] = [
    CareBookingStatus.CONFIRMED,
    CareBookingStatus.PENDING,
    CareBookingStatus.CANCELLED,
    CareBookingStatus.NO_SHOW,
  ];

  readonly selected = signal<CareBookingStatus[]>([...(this.data?.selected ?? [])]);

  toggle(s: CareBookingStatus): void {
    const cur = this.selected();
    this.selected.set(cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s]);
  }

  apply(): void {
    this.dialogRef.close(this.selected());
  }
}
