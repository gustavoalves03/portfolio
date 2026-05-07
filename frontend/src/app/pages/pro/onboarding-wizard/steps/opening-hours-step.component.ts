import { Component, computed, inject, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AvailabilityService } from '../../../../features/availability/availability.service';
import { OpeningHourRequest } from '../../../../features/availability/availability.model';

interface DayRow {
  dayOfWeek: number; // 1-7 (1 = Monday)
  open: boolean;
  openTime: string;
  closeTime: string;
}

@Component({
  selector: 'app-opening-hours-step',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './opening-hours-step.component.html',
  styleUrl: './opening-hours-step.component.scss',
})
export class OpeningHoursStepComponent {
  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly availability = inject(AvailabilityService);

  protected readonly rows = signal<DayRow[]>(
    Array.from({ length: 7 }, (_, i) => ({
      dayOfWeek: i + 1,
      open: false,
      openTime: '09:00',
      closeTime: '19:00',
    }))
  );
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canSubmit = computed(() => this.rows().some(r => r.open));

  protected toggle(index: number): void {
    const next = [...this.rows()];
    next[index] = { ...next[index], open: !next[index].open };
    this.rows.set(next);
  }

  protected setTime(index: number, field: 'openTime' | 'closeTime', value: string): void {
    const next = [...this.rows()];
    next[index] = { ...next[index], [field]: value };
    this.rows.set(next);
  }

  protected presetWeekdays(): void {
    const next = this.rows().map(r => ({
      ...r,
      open: r.dayOfWeek <= 5,
      openTime: '09:00',
      closeTime: '19:00',
    }));
    this.rows.set(next);
  }

  protected presetMonSat(): void {
    const next = this.rows().map(r => ({
      ...r,
      open: r.dayOfWeek <= 6,
      openTime: '10:00',
      closeTime: '20:00',
    }));
    this.rows.set(next);
  }

  protected onSubmit(): void {
    if (!this.canSubmit()) return;
    this.saving.set(true);
    this.error.set(null);
    const payload: OpeningHourRequest[] = this.rows()
      .filter(r => r.open)
      .map(r => ({ dayOfWeek: r.dayOfWeek, openTime: r.openTime, closeTime: r.closeTime }));
    this.availability.saveHours(payload).subscribe({
      next: () => { this.saving.set(false); this.completed.emit(); },
      error: () => { this.saving.set(false); this.error.set('save'); },
    });
  }
}
