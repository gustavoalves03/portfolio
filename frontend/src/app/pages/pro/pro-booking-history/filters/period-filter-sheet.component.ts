import { Component, inject, signal } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatRadioModule } from '@angular/material/radio';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { provideNativeDateAdapter } from '@angular/material/core';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';

export type PeriodPreset = '30days' | '3months' | '6months' | 'custom';

export interface PeriodResult {
  preset: PeriodPreset;
  from: string;
  to: string;
}

@Component({
  selector: 'app-period-filter-sheet',
  standalone: true,
  imports: [
    MatRadioModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    FormsModule,
    TranslocoPipe,
    SheetHandleComponent,
  ],
  providers: [provideNativeDateAdapter()],
  template: `
    <app-sheet-handle />
    <div class="sheet-wrap">
      <h3>{{ 'pro.history.filter.period.custom' | transloco }}</h3>
      <mat-radio-group [(ngModel)]="presetValue" (ngModelChange)="onPresetChange($event)">
        <mat-radio-button value="30days">{{ 'pro.history.filter.period.30days' | transloco }}</mat-radio-button>
        <mat-radio-button value="3months">{{ 'pro.history.filter.period.3months' | transloco }}</mat-radio-button>
        <mat-radio-button value="6months">{{ 'pro.history.filter.period.6months' | transloco }}</mat-radio-button>
        <mat-radio-button value="custom">{{ 'pro.history.filter.period.custom' | transloco }}</mat-radio-button>
      </mat-radio-group>

      @if (preset() === 'custom') {
        <div class="range">
          <mat-form-field>
            <mat-label>{{ 'pro.history.filter.period.from' | transloco }}</mat-label>
            <input matInput [matDatepicker]="fromPicker" [(ngModel)]="fromDateValue" />
            <mat-datepicker-toggle matIconSuffix [for]="fromPicker" />
            <mat-datepicker #fromPicker />
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'pro.history.filter.period.to' | transloco }}</mat-label>
            <input matInput [matDatepicker]="toPicker" [(ngModel)]="toDateValue" [min]="fromDateValue" />
            <mat-datepicker-toggle matIconSuffix [for]="toPicker" />
            <mat-datepicker #toPicker />
          </mat-form-field>
        </div>
      }

      <button class="apply" (click)="apply()">{{ 'common.apply' | transloco }}</button>
    </div>
  `,
  styles: [`
    .sheet-wrap { padding: 16px; display: flex; flex-direction: column; gap: 14px; }
    h3 { margin: 0; font-size: 16px; color: #333; }
    mat-radio-group { display: flex; flex-direction: column; gap: 8px; }
    .range { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .apply {
      margin-top: 8px; padding: 12px; border: none; border-radius: 10px;
      background: #c06; color: white; font-weight: 600; font-size: 14px;
      cursor: pointer;
    }
  `],
})
export class PeriodFilterSheetComponent {
  private readonly dialogRef = inject(MatDialogRef<PeriodFilterSheetComponent>);

  readonly preset = signal<PeriodPreset>('30days');
  presetValue: PeriodPreset = '30days';
  fromDateValue: Date | null = null;
  toDateValue: Date | null = new Date();

  onPresetChange(value: PeriodPreset): void {
    this.preset.set(value);
  }

  apply(): void {
    const today = new Date();
    const todayStr = toYMD(today);
    let from = todayStr;
    let to = todayStr;

    switch (this.preset()) {
      case '30days':
        from = toYMD(addDays(today, -30));
        break;
      case '3months':
        from = toYMD(addDays(today, -90));
        break;
      case '6months':
        from = toYMD(addDays(today, -180));
        break;
      case 'custom': {
        if (!this.fromDateValue || !this.toDateValue) return;
        from = toYMD(this.fromDateValue);
        to = toYMD(this.toDateValue);
        break;
      }
    }

    this.dialogRef.close({ preset: this.preset(), from, to } as PeriodResult);
  }
}

function addDays(d: Date, days: number): Date {
  const r = new Date(d);
  r.setDate(r.getDate() + days);
  return r;
}

function toYMD(d: Date): string {
  const y = d.getFullYear();
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${y}-${m}-${day}`;
}
