import { Component, computed, inject, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantPatchService } from '../../../../features/onboarding/wizard/tenant-patch.service';

@Component({
  selector: 'app-logo-step',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './logo-step.component.html',
  styleUrl: './logo-step.component.scss',
})
export class LogoStepComponent {
  readonly initialLogoUrl = input<string | null>(null);

  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly patcher = inject(TenantPatchService);

  protected readonly dataUrl = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canSubmit = computed(() => !!this.dataUrl() || !!this.initialLogoUrl());

  protected onFileChange(e: Event): void {
    const files = (e.target as HTMLInputElement).files;
    if (!files || files.length === 0) return;
    this.onFileSelected(files[0]);
  }

  protected onFileSelected(file: File): void {
    if (file.size > 5 * 1024 * 1024 || !file.type.startsWith('image/')) {
      this.error.set('invalidFile');
      return;
    }
    this.error.set(null);
    const reader = new FileReader();
    reader.onload = () => {
      this.dataUrl.set(reader.result as string);
    };
    reader.readAsDataURL(file);
  }

  protected onSubmit(): void {
    if (!this.canSubmit()) return;
    if (!this.dataUrl()) {
      // Already has a logo (initialLogoUrl set), no patch needed
      this.completed.emit();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.patcher.patch({ logo: this.dataUrl()! }).subscribe({
      next: () => {
        this.saving.set(false);
        this.completed.emit();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('save');
      },
    });
  }
}
