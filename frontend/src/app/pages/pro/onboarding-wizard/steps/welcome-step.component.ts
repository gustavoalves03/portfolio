import { Component, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-welcome-step',
  standalone: true,
  imports: [TranslocoPipe, MatIconModule],
  templateUrl: './welcome-step.component.html',
  styleUrl: './welcome-step.component.scss',
})
export class WelcomeStepComponent {
  readonly completed = output<void>();
  readonly exit = output<void>();
}
