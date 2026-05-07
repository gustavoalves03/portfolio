import { Component, inject, input, output, signal, OnInit } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantPatchService } from '../../../../features/onboarding/wizard/tenant-patch.service';

@Component({
  selector: 'app-name-step',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './name-step.component.html',
  styleUrl: './name-step.component.scss',
})
export class NameStepComponent implements OnInit {
  readonly initialName = input<string | null>(null);
  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly patcher = inject(TenantPatchService);
  protected readonly value = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.value.set(this.initialName() ?? '');
  }

  protected onInput(e: Event): void {
    this.value.set((e.target as HTMLInputElement).value);
  }

  protected onSubmit(): void {
    const v = this.value().trim();
    if (!v) return;
    this.saving.set(true);
    this.error.set(null);
    this.patcher.patch({ name: v }).subscribe({
      next: () => { this.saving.set(false); this.completed.emit(); },
      error: () => { this.saving.set(false); this.error.set('save'); },
    });
  }
}
