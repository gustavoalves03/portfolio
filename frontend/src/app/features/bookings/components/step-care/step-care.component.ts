import { Component, computed, DestroyRef, effect, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CaresStore } from '../../../cares/store/cares.store';
import { BookingsService } from '../../services/bookings.service';
import { EmployeeSlim } from '../../../salon-profile/models/salon-profile.model';

@Component({
  selector: 'app-step-care',
  standalone: true,
  imports: [CommonModule, TranslocoPipe, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="step-care">
      <h3>{{ 'booking.stepper.step1' | transloco }}</h3>

      <div class="care-list">
        @for (care of caresStore.availableCares(); track care.id) {
          <div
            class="care-card"
            data-testid="step-care-item"
            [class.selected]="selectedCareId() === care.id"
            (click)="selectCare(care.id)"
          >
            <div class="care-name">{{ care.name }}</div>
            <div class="care-details">
              <span>{{ care.price }}&euro;</span>
              <span>{{ care.duration }} min</span>
            </div>
          </div>
        }
      </div>

      @if (selectedCareId() && shouldShowEmployeeList()) {
        <div class="employee-section">
          <h4>{{ 'booking.stepper.step1Employee' | transloco }}</h4>

          @if (loadingEmployees()) {
            <div class="employee-loading">
              <mat-spinner diameter="20"></mat-spinner>
            </div>
          } @else if (errorMessage()) {
            <div class="employee-empty">{{ errorMessage()! | transloco }}</div>
          } @else if (shouldShowEmptyState()) {
            <div class="employee-empty">
              {{ 'booking.employees.empty' | transloco }}
            </div>
          } @else {
            <div class="employee-list">
              <!-- "Premier dispo" virtual card -->
              <div
                class="employee-card"
                data-testid="step-employee-any"
                [class.selected]="selectedEmployeeId() === null"
                (click)="selectEmployee(null)"
              >
                <div class="emp-avatar emp-avatar-any">
                  <mat-icon>groups</mat-icon>
                </div>
                <div class="emp-info">
                  <div class="emp-name">
                    {{ 'booking.employees.anyAvailable' | transloco }}
                  </div>
                </div>
              </div>

              @for (e of availableEmployees(); track e.id) {
                <div
                  class="employee-card"
                  data-testid="step-employee-item"
                  [class.selected]="selectedEmployeeId() === e.id"
                  (click)="selectEmployee(e.id)"
                >
                  <div class="emp-avatar">
                    @if (e.imageUrl) {
                      <img [src]="e.imageUrl" [alt]="e.name" />
                    } @else {
                      <span>{{ initials(e.name) }}</span>
                    }
                  </div>
                  <div class="emp-info">
                    <div class="emp-name">{{ e.name }}</div>
                  </div>
                </div>
              }
            </div>
          }
        </div>
      }

      <button
        class="btn-next"
        data-testid="step-next-btn"
        [disabled]="!canProceed()"
        (click)="onNext()"
      >
        {{ 'booking.stepper.next' | transloco }}
      </button>
    </div>
  `,
  styles: [
    `
      .step-care {
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

      h4 {
        margin: 4px 0 0;
        font-size: 13px;
        font-weight: 600;
        color: #555;
      }

      .care-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 240px;
        overflow-y: auto;
      }

      .care-card {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 10px 14px;
        border: 1.5px solid #e0e0e0;
        border-radius: 10px;
        background: white;
        cursor: pointer;
        transition: border-color 200ms ease, background 200ms ease, box-shadow 200ms ease;
      }

      .care-card:hover {
        border-color: #f0a0c0;
        box-shadow: 0 2px 8px rgba(204, 0, 102, 0.08);
      }

      .care-card.selected {
        border-color: var(--pf-rose);
        background: #fff5f7;
      }

      .care-name {
        font-weight: 500;
        font-size: 14px;
        color: #333;
      }

      .care-details {
        display: flex;
        gap: 10px;
        font-size: 12px;
        color: #888;
      }

      .employee-section {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .employee-list {
        display: flex;
        flex-direction: column;
        gap: 6px;
        max-height: 200px;
        overflow-y: auto;
      }

      .employee-card {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 8px 10px;
        border: 1.5px solid #e0e0e0;
        border-radius: 10px;
        background: white;
        cursor: pointer;
        transition: border-color 200ms ease, background 200ms ease;
      }

      .employee-card:hover {
        border-color: #f0a0c0;
      }

      .employee-card.selected {
        border-color: var(--pf-rose);
        background: #fff5f7;
      }

      .emp-avatar {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        background: #f1d5dc;
        color: var(--pf-rose);
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 600;
        font-size: 12px;
        flex-shrink: 0;
        overflow: hidden;
      }

      .emp-avatar img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .emp-avatar-any mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }

      .emp-info {
        display: flex;
        flex-direction: column;
      }

      .emp-name {
        font-size: 13px;
        font-weight: 500;
        color: #333;
      }

      .employee-empty {
        padding: 12px;
        background: #fef2f2;
        border-radius: 8px;
        color: #c0392b;
        font-size: 13px;
        text-align: center;
      }

      .employee-loading {
        padding: 16px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #888;
        font-size: 13px;
      }

      .btn-next {
        align-self: stretch;
        background: var(--pf-rose);
        color: white;
        border: none;
        border-radius: 8px;
        padding: 10px 24px;
        font-size: 13px;
        font-weight: 500;
        cursor: pointer;
        transition: background 200ms ease;
        margin-top: 4px;
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
export class StepCareComponent {
  readonly caresStore = inject(CaresStore);
  private readonly bookingsService = inject(BookingsService);

  readonly selectedCareId = signal<number | null>(null);
  readonly availableEmployees = signal<EmployeeSlim[]>([]);
  readonly selectedEmployeeId = signal<number | null>(null);
  readonly loadingEmployees = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly careSelected = output<{ careId: number; employeeId: number | null }>();

  // ── derived UI gates ─────────────────────────────────────────────────
  readonly shouldShowEmployeeList = computed(() => {
    const list = this.availableEmployees();
    // Hide entirely when exactly one employee is available (silent auto-select).
    return list.length !== 1;
  });

  readonly shouldShowEmptyState = computed(() => this.availableEmployees().length === 0);

  readonly canProceed = computed(() => {
    if (!this.selectedCareId()) return false;
    if (this.loadingEmployees()) return false;
    if (this.shouldShowEmptyState()) return false;
    return true;
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    effect(() => {
      const careId = this.selectedCareId();
      if (careId === null) return;
      this.loadingEmployees.set(true);
      this.errorMessage.set(null);
      this.bookingsService
        .getEmployeesForCare(careId)
        .pipe(takeUntilDestroyed(destroyRef))
        .subscribe({
          next: (list) => {
            this.availableEmployees.set(list);
            // Mono-employee → auto-select silently; otherwise default to "Premier dispo" (null).
            if (list.length === 1) {
              this.selectedEmployeeId.set(list[0].id);
            } else {
              this.selectedEmployeeId.set(null);
            }
            this.loadingEmployees.set(false);
          },
          error: () => {
            this.availableEmployees.set([]);
            this.selectedEmployeeId.set(null);
            this.loadingEmployees.set(false);
            this.errorMessage.set('booking.employees.loadError');
          },
        });
    });
  }

  selectCare(id: number): void {
    this.selectedCareId.set(id);
  }

  selectEmployee(id: number | null): void {
    this.selectedEmployeeId.set(id);
  }

  initials(name: string): string {
    return name
      .split(' ')
      .map((n) => n[0])
      .filter(Boolean)
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }

  onNext(): void {
    const careId = this.selectedCareId();
    if (careId === null) return;
    if (!this.canProceed()) return;
    this.careSelected.emit({ careId, employeeId: this.selectedEmployeeId() });
  }
}
