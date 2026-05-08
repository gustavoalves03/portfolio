import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-wizard-progress-bar',
  standalone: true,
  templateUrl: './wizard-progress-bar.component.html',
  styleUrl: './wizard-progress-bar.component.scss',
})
export class WizardProgressBarComponent {
  readonly currentIndex = input.required<number>();
  readonly totalSteps = input.required<number>();
  readonly stepClick = output<number>();

  protected segments(): number[] {
    return Array.from({ length: this.totalSteps() }, (_, i) => i);
  }

  protected onSegmentClick(index: number): void {
    if (index < this.currentIndex()) this.stepClick.emit(index);
  }
}
