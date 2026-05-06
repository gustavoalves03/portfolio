import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Renders a small inline hint when its bound FormGroup is invalid AND the user
 * has interacted with it (touched or dirty). Defaults to the
 * `common.form.fillRequiredFields` i18n key.
 *
 * Place it next to a submit button: when the button is disabled because the
 * form is invalid, this hint explains why.
 *
 * Note: FormGroup is not natively reactive to signals. We bump a tick signal
 * whenever the form's status changes so the `visible` computed re-evaluates.
 * Calling markAsDirty() alone (without a value change) won't emit statusChanges,
 * so the hint responds to actual field interactions, which is the primary use case.
 */
@Component({
  selector: 'app-form-validation-hint',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <p class="form-validation-hint" role="status">
        <mat-icon aria-hidden="true">info</mat-icon>
        <span>{{ message() | transloco }}</span>
      </p>
    }
  `,
  styleUrl: './form-validation-hint.component.scss',
})
export class FormValidationHintComponent {
  readonly form = input.required<FormGroup>();
  readonly message = input<string>('common.form.fillRequiredFields');

  private readonly tick = signal(0);

  protected readonly visible = computed(() => {
    void this.tick();
    const f = this.form();
    return f.invalid && (f.touched || f.dirty);
  });

  constructor() {
    effect((onCleanup) => {
      const f = this.form();
      const sub = f.statusChanges.subscribe(() => this.tick.update((n) => n + 1));
      onCleanup(() => sub.unsubscribe());
    });
  }
}
