import { Component, output } from '@angular/core';

@Component({
  selector: 'app-step-datetime',
  standalone: true,
  template: `<div class="step-placeholder">Step 2: Date &amp; time selection (placeholder)</div>`,
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
export class StepDatetimeComponent {
  readonly datetimeSelected = output<{ date: string; time: string }>();
}
