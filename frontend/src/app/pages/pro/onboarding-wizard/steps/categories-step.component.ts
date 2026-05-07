import { Component, computed, inject, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantPatchService } from '../../../../features/onboarding/wizard/tenant-patch.service';

const CATEGORY_KEYS = [
  'facial', 'hair', 'nails', 'massage', 'lashes',
  'makeup', 'waxing', 'wellness', 'barber', 'rituals',
] as const;

@Component({
  selector: 'app-categories-step',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './categories-step.component.html',
  styleUrl: './categories-step.component.scss',
})
export class CategoriesStepComponent {
  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly patcher = inject(TenantPatchService);

  protected readonly keys = CATEGORY_KEYS;
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canSubmit = computed(() => this.selected().size > 0);

  protected isSelected(key: string): boolean {
    return this.selected().has(key);
  }

  protected toggle(key: string): void {
    const next = new Set(this.selected());
    if (next.has(key)) next.delete(key);
    else next.add(key);
    this.selected.set(next);
  }

  protected onSubmit(): void {
    if (!this.canSubmit()) return;
    this.saving.set(true);
    this.error.set(null);
    const slugs = CATEGORY_KEYS.filter(k => this.selected().has(k)).join(',');
    this.patcher.patch({ categorySlugs: slugs }).subscribe({
      next: () => { this.saving.set(false); this.completed.emit(); },
      error: () => { this.saving.set(false); this.error.set('save'); },
    });
  }
}
