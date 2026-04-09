import { Component, output } from '@angular/core';

@Component({
  selector: 'app-step-client',
  standalone: true,
  template: `<div class="step-placeholder">Step 3: Client selection (placeholder)</div>`,
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
export class StepClientComponent {
  readonly clientSelected = output<{ salonClientId: number }>();
}
