import { Component, computed, inject, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantPatchService, TenantPatch } from '../../../../features/onboarding/wizard/tenant-patch.service';

@Component({
  selector: 'app-contact-step',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './contact-step.component.html',
  styleUrl: './contact-step.component.scss',
})
export class ContactStepComponent {
  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly patcher = inject(TenantPatchService);

  protected readonly street = signal('');
  protected readonly postalCode = signal('');
  protected readonly city = signal('');
  protected readonly country = signal('FR');
  protected readonly phone = signal('');
  protected readonly email = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canSubmit = computed(() =>
    !!this.street().trim() && !!this.postalCode().trim() &&
    !!this.city().trim() && !!this.country().trim() &&
    (!!this.phone().trim() || !!this.email().trim())
  );

  protected onInput(name: string, e: Event): void {
    const v = (e.target as HTMLInputElement).value;
    switch (name) {
      case 'street': this.street.set(v); break;
      case 'postalCode': this.postalCode.set(v); break;
      case 'city': this.city.set(v); break;
      case 'country': this.country.set(v); break;
      case 'phone': this.phone.set(v); break;
      case 'email': this.email.set(v); break;
    }
  }

  protected onSubmit(): void {
    if (!this.canSubmit()) return;
    this.saving.set(true);
    this.error.set(null);
    const body: TenantPatch = {
      addressStreet: this.street().trim(),
      addressPostalCode: this.postalCode().trim(),
      addressCity: this.city().trim(),
      addressCountry: this.country().trim(),
    };
    if (this.phone().trim()) body.phone = this.phone().trim();
    if (this.email().trim()) body.contactEmail = this.email().trim();
    this.patcher.patch(body).subscribe({
      next: () => { this.saving.set(false); this.completed.emit(); },
      error: () => { this.saving.set(false); this.error.set('save'); },
    });
  }
}
