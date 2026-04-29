import { Component, inject, input, output, signal } from '@angular/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { provideFrenchDateAdapter } from '../../../../shared/providers/french-date-adapter';
import { TranslocoPipe } from '@jsverse/transloco';
import { BookingsService, AvailableSlot } from '../../services/bookings.service';
import { ClosedDaysStore } from '../../../availability/closed-days.store';

@Component({
  selector: 'app-step-datetime',
  standalone: true,
  imports: [
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  providers: [provideFrenchDateAdapter()],
  template: `
    <div class="step-datetime">
      <h3>{{ 'booking.stepper.step2' | transloco }}</h3>

      <mat-form-field>
        <mat-label>{{ 'booking.stepper.selectDate' | transloco }}</mat-label>
        <input
          matInput
          data-testid="booking-date-input"
          [matDatepicker]="picker"
          [matDatepickerFilter]="dateFilter"
          (dateChange)="onDateChange($event)"
          [min]="minDate"
        />
        <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
        <mat-datepicker #picker [dateClass]="dateClass" (monthSelected)="onMonthSelected($event)" (opened)="onCalendarOpened()"></mat-datepicker>
      </mat-form-field>

      @if (loading()) {
        <div class="loading">
          <mat-spinner diameter="28"></mat-spinner>
        </div>
      }

      @if (errorMessage()) {
        <div class="error-msg">{{ errorMessage() }}</div>
      }

      @if (selectedDate() && !loading() && !errorMessage()) {
        @if (availableSlots().length === 0) {
          <div class="no-slots">Aucun créneau disponible pour cette date</div>
        } @else {
          <div class="time-grid">
            @for (slot of availableSlots(); track slot.startTime) {
              <button
                class="time-btn"
                data-testid="slot-btn"
                [class.selected]="selectedTime() === slot.startTime"
                (click)="selectTime(slot.startTime)"
              >
                {{ slot.startTime }}
              </button>
            }
          </div>
        }
      }

      <button
        class="btn-next"
        data-testid="step-next-btn"
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
        gap: 12px;
      }

      h3 {
        margin: 0;
        font-size: 15px;
        font-weight: 600;
        color: #333;
      }

      mat-form-field {
        width: 100%;
      }

      .loading {
        display: flex;
        justify-content: center;
        padding: 16px 0;
      }

      .error-msg {
        text-align: center;
        color: #dc2626;
        font-size: 13px;
        padding: 12px;
        background: #fef2f2;
        border-radius: 8px;
      }

      .no-slots {
        text-align: center;
        color: #999;
        font-size: 13px;
        padding: 16px 0;
        font-style: italic;
      }

      .time-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 6px;
      }

      .time-btn {
        padding: 8px 4px;
        border-radius: 8px;
        border: 1px solid #e0e0e0;
        background: white;
        font-size: 13px;
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
        align-self: stretch;
        background: #c06;
        color: white;
        border: none;
        border-radius: 8px;
        padding: 10px 24px;
        font-size: 13px;
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
  private readonly bookingsService = inject(BookingsService);
  private readonly closedDaysStore = inject(ClosedDaysStore);

  readonly careId = input<number | null>(null);

  readonly selectedDate = signal<string | null>(null);
  readonly selectedTime = signal<string | null>(null);
  readonly availableSlots = signal<AvailableSlot[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly datetimeSelected = output<{ date: string; time: string }>();
  readonly minDate = new Date();

  readonly dateFilter = (date: Date | null): boolean => {
    if (!date) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (date < today) return false;
    return !this.closedDaysStore.closedDays().has(this.toLocalDateString(date));
  };

  readonly dateClass = (date: Date): string => {
    return this.closedDaysStore.holidayDays().has(this.toLocalDateString(date))
      ? 'closed-holiday'
      : '';
  };

  constructor() {
    this.preloadMonthsFrom(new Date(), 6);
  }

  onCalendarOpened(): void {
    this.preloadMonthsFrom(new Date(), 6);
  }

  onMonthSelected(date: Date): void {
    this.preloadMonthsFrom(date, 3);
  }

  private preloadMonthsFrom(start: Date, count: number): void {
    for (let i = 0; i < count; i++) {
      const d = new Date(start.getFullYear(), start.getMonth() + i, 1);
      this.closedDaysStore.loadMonth({ year: d.getFullYear(), month: d.getMonth() + 1 });
    }
  }

  onDateChange(event: { value: Date | null }): void {
    const date = event.value;
    if (!date) return;

    const dateStr = this.toLocalDateString(date);
    this.selectedDate.set(dateStr);
    this.selectedTime.set(null);
    this.loadSlots(dateStr);
  }

  private loadSlots(date: string): void {
    const careId = this.careId();
    if (!careId) {
      this.errorMessage.set('Aucun soin sélectionné');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.bookingsService.getAvailableSlots(careId, date).subscribe({
      next: (slots) => {
        // Filter out past slots if date is today
        const now = new Date();
        const todayStr = this.toLocalDateString(now);
        let filtered = slots;
        if (date === todayStr) {
          const currentMinutes = now.getHours() * 60 + now.getMinutes();
          filtered = slots.filter((s) => {
            const [h, m] = s.startTime.split(':').map(Number);
            return h * 60 + m > currentMinutes;
          });
        }
        this.availableSlots.set(filtered);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load slots:', err);
        this.availableSlots.set([]);
        this.errorMessage.set(err?.status === 403 ? 'Accès refusé' : 'Erreur de chargement des créneaux');
        this.loading.set(false);
      },
    });
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

  private toLocalDateString(date: Date): string {
    const y = date.getFullYear();
    const m = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
