import { Component, inject, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { CaresStore } from '../../../cares/store/cares.store';

@Component({
  selector: 'app-step-care',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <div class="step-care">
      <h3>{{ 'booking.stepper.step1' | transloco }}</h3>

      <div class="care-list">
        @for (care of caresStore.availableCares(); track care.id) {
          <div
            class="care-card"
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

      <button class="btn-next" [disabled]="!selectedCareId()" (click)="onNext()">
        {{ 'booking.stepper.next' | transloco }}
      </button>
    </div>
  `,
  styles: [
    `
      .step-care {
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

      .care-list {
        display: flex;
        flex-direction: column;
        gap: 10px;
        max-height: 320px;
        overflow-y: auto;
      }

      .care-card {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 14px 18px;
        border: 2px solid #e0e0e0;
        border-radius: 12px;
        cursor: pointer;
        transition:
          border-color 200ms ease,
          background 200ms ease,
          box-shadow 200ms ease;
      }

      .care-card:hover {
        border-color: #f0a0c0;
        box-shadow: 0 2px 8px rgba(204, 0, 102, 0.08);
      }

      .care-card.selected {
        border-color: #c06;
        background: #fff5f7;
      }

      .care-name {
        font-weight: 500;
        font-size: 15px;
        color: #333;
      }

      .care-details {
        display: flex;
        gap: 12px;
        font-size: 13px;
        color: #666;
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
export class StepCareComponent {
  readonly caresStore = inject(CaresStore);
  readonly selectedCareId = signal<number | null>(null);
  readonly careSelected = output<{ careId: number; employeeId: number }>();

  selectCare(id: number): void {
    this.selectedCareId.set(id);
  }

  onNext(): void {
    const careId = this.selectedCareId();
    if (careId) {
      this.careSelected.emit({ careId, employeeId: 0 });
    }
  }
}
