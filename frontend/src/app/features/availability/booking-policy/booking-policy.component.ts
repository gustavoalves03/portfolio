import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BookingPolicyStore } from './booking-policy.store';

@Component({
  selector: 'app-booking-policy',
  standalone: true,
  imports: [MatButtonModule, MatProgressSpinnerModule, TranslocoPipe],
  templateUrl: './booking-policy.component.html',
  styleUrl: './booking-policy.component.scss',
})
export class BookingPolicyComponent {
  readonly store = inject(BookingPolicyStore);
  private readonly snackbar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected readonly perDay = signal(1);
  protected readonly perWeekNew = signal(1);

  protected readonly canSave = computed(
    () =>
      !this.store.isPending() &&
      this.perDay() >= 1 &&
      this.perDay() <= 10 &&
      this.perWeekNew() >= 1 &&
      this.perWeekNew() <= 10,
  );

  private readonly saveTriggered = signal(false);

  constructor() {
    this.store.load();

    // Sync form fields with the loaded policy (only when no unsaved changes pending)
    effect(() => {
      const policy = this.store.policy();
      if (policy) {
        untracked(() => {
          if (!this.saveTriggered()) {
            this.perDay.set(policy.maxBookingsPerDayPerClient);
            this.perWeekNew.set(policy.maxBookingsPerWeekForNewClient);
          }
        });
      }
    });

    // Show snackbar on update success
    let lastUpdatedAt: string | undefined;
    effect(() => {
      const policy = this.store.policy();
      const isFulfilled = this.store.isFulfilled();
      if (policy && isFulfilled && lastUpdatedAt && policy.updatedAt !== lastUpdatedAt) {
        untracked(() => {
          if (this.saveTriggered()) {
            this.snackbar.open(this.transloco.translate('pro.bookingPolicy.saved'), undefined, {
              duration: 3000,
            });
            this.saveTriggered.set(false);
          }
        });
      }
      lastUpdatedAt = policy?.updatedAt;
    });

    // Show snackbar on error
    effect(() => {
      const err = this.store.error();
      if (err) {
        untracked(() => {
          this.snackbar.open(this.transloco.translate('pro.bookingPolicy.error'), undefined, {
            duration: 4000,
          });
          this.saveTriggered.set(false);
        });
      }
    });
  }

  protected onSave(): void {
    this.saveTriggered.set(true);
    this.store.update({
      maxBookingsPerDayPerClient: this.perDay(),
      maxBookingsPerWeekForNewClient: this.perWeekNew(),
    });
  }
}
