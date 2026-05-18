import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { AbstractControl, FormGroup } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { inject } from '@angular/core';

/**
 * Inline hint shown next to a submit button when the bound FormGroup is invalid
 * AND the user has interacted with it. Lists the specific fields that are
 * blocking submission, so the user knows what to fix.
 *
 * Field labels are looked up via i18n key `common.form.field.<controlName>`,
 * falling back to the raw control name if no translation exists.
 *
 * Cross-field errors at the FormGroup level (e.g. passwordMismatch) are picked
 * up via i18n key `common.form.crossError.<errorCode>` and listed last.
 */
@Component({
  selector: 'app-form-validation-hint',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <p class="form-validation-hint" role="status" aria-live="polite">
        <mat-icon aria-hidden="true">info</mat-icon>
        <span>
          {{ 'common.form.checkTheseFields' | transloco }}
          <strong>{{ invalidFieldLabels() }}</strong>
        </span>
      </p>
    }
  `,
  styleUrl: './form-validation-hint.component.scss',
})
export class FormValidationHintComponent {
  readonly form = input.required<FormGroup>();

  private readonly transloco = inject(TranslocoService);
  private readonly tick = signal(0);

  protected readonly visible = computed(() => {
    void this.tick();
    const f = this.form();
    return f.invalid && (f.touched || f.dirty);
  });

  protected readonly invalidFieldLabels = computed(() => {
    void this.tick();
    const f = this.form();
    const labels: string[] = [];

    for (const [name, control] of Object.entries(f.controls)) {
      if (this.isControlInvalid(control)) {
        labels.push(this.labelFor(name));
      }
    }

    for (const errorCode of Object.keys(f.errors ?? {})) {
      const key = `common.form.crossError.${errorCode}`;
      const translated = this.transloco.translate(key);
      if (translated && translated !== key) {
        labels.push(translated);
      }
    }

    return labels.join(', ');
  });

  constructor() {
    effect((onCleanup) => {
      const f = this.form();
      const sub = f.statusChanges.subscribe(() => this.tick.update((n) => n + 1));
      onCleanup(() => sub.unsubscribe());
    });
  }

  private isControlInvalid(control: AbstractControl): boolean {
    return control.invalid && (control.touched || control.dirty);
  }

  private labelFor(controlName: string): string {
    const key = `common.form.field.${controlName}`;
    const translated = this.transloco.translate(key);
    return translated && translated !== key ? translated : controlName;
  }
}
