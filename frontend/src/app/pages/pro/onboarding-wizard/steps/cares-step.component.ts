import { Component, inject, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { PersonaSetupService } from '../../../../features/onboarding/persona-setup.service';
import { PERSONAS, Persona } from '../../../../features/onboarding/personas';

@Component({
  selector: 'app-cares-step',
  standalone: true,
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './cares-step.component.html',
  styleUrl: './cares-step.component.scss',
})
export class CaresStepComponent {
  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly personaSetup = inject(PersonaSetupService);

  protected readonly personas = PERSONAS;
  protected readonly applying = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected onPick(persona: Persona): void {
    if (this.applying()) return;
    this.applying.set(persona.key);
    this.error.set(null);
    this.personaSetup.apply(persona).subscribe({
      next: () => { this.applying.set(null); this.completed.emit(); },
      error: () => { this.applying.set(null); this.error.set('apply'); },
    });
  }
}
