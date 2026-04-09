import { Component, output, signal } from '@angular/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { provideNativeDateAdapter } from '@angular/material/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-step-datetime',
  standalone: true,
  imports: [MatDatepickerModule, MatFormFieldModule, MatInputModule, TranslocoPipe],
  providers: [provideNativeDateAdapter()],
  template: `
    <div class="step-datetime">
      <h3>{{ 'booking.stepper.step2' | transloco }}</h3>

      <mat-form-field>
        <mat-label>{{ 'booking.stepper.selectDate' | transloco }}</mat-label>
        <input matInput [matDatepicker]="picker" (dateChange)="onDateChange($event)" [min]="minDate" />
        <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
        <mat-datepicker #picker></mat-datepicker>
      </mat-form-field>

      @if (selectedDate()) {
        <div class="time-grid">
          @for (time of timeSlots; track time) {
            <button
              class="time-btn"
              [class.selected]="selectedTime() === time"
              (click)="selectTime(time)"
            >
              {{ time }}
            </button>
          }
        </div>
      }

      <button
        class="btn-next"
        [disabled]="!selectedDate() || !selectedTime()"
        (click)="onNext()"
      >
        {{ 'booking.stepper.next' | transloco }}
      </button>
    </div>
  `,
  styles: [
    `
      .step-datetime {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      h3 {
        margin: 0;
        font-size: 18px;
        font-weight: 600;
        color: #333;
      }

      mat-form-field {
        width: 100%;
      }

      .time-grid {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 8px;
      }

      .time-btn {
        padding: 8px;
        border-radius: 8px;
        border: 1px solid #e0e0e0;
        background: white;
        font-size: 14px;
        cursor: pointer;
        transition:
          background 200ms ease,
          border-color 200ms ease,
          color 200ms ease;
      }

      .time-btn:hover:not(.selected) {
        border-color: #f0a0c0;
        background: #fff5f7;
      }

      .time-btn.selected {
        background: #c06;
        color: white;
        border-color: #c06;
      }

      .btn-next {
        align-self: flex-end;
        background: #c06;
        color: white;
        border: none;
        border-radius: 8px;
        padding: 10px 24px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: background 200ms ease;
      }

      .btn-next:hover:not(:disabled) {
        background: #a00554;
      }

      .btn-next:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    `,
  ],
})
export class StepDatetimeComponent {
  readonly selectedDate = signal<string | null>(null);
  readonly selectedTime = signal<string | null>(null);
  readonly datetimeSelected = output<{ date: string; time: string }>();
  readonly minDate = new Date();

  readonly timeSlots = [
    '09:00',
    '09:30',
    '10:00',
    '10:30',
    '11:00',
    '11:30',
    '14:00',
    '14:30',
    '15:00',
    '15:30',
    '16:00',
    '16:30',
    '17:00',
    '17:30',
  ];

  onDateChange(event: { value: Date | null }): void {
    const date = event.value;
    if (date) {
      this.selectedDate.set(date.toISOString().split('T')[0]);
    }
  }

  selectTime(time: string): void {
    this.selectedTime.set(time);
  }

  onNext(): void {
    const date = this.selectedDate();
    const time = this.selectedTime();
    if (date && time) {
      this.datetimeSelected.emit({ date, time });
    }
  }
}
