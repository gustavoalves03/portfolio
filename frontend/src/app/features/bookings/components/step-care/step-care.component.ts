import { Component, output } from '@angular/core';

@Component({
  selector: 'app-step-care',
  standalone: true,
  template: `<div class="step-placeholder">Step 1: Care selection (placeholder)</div>`,
  styles: [
    `
      .step-placeholder {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 200px;
        color: #888;
        font-style: italic;
      }
    `,
  ],
})
export class StepCareComponent {
  readonly careSelected = output<{ careId: number; employeeId: number }>();
}
