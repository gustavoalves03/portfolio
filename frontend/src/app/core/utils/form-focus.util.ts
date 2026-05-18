import { FormGroup } from '@angular/forms';

/**
 * Marks every control of a FormGroup as touched, then focuses + scrolls to
 * the first invalid control. Call this from a component's onSubmit() when
 * `form.invalid` is true, instead of just bailing out — without it, users
 * see no signal about which field is blocking them.
 *
 * Lookup strategy for the DOM element: scopes the search to the host element
 * (so nested forms don't steal focus), then `formControlName="<name>"` first,
 * `name="<name>"` second. Falls back to the form root if no field matches.
 */
export function focusFirstInvalid(form: FormGroup, host?: HTMLElement): void {
  form.markAllAsTouched();

  for (const [name, control] of Object.entries(form.controls)) {
    if (!control.invalid) continue;

    const root: ParentNode = host ?? document;
    const el =
      root.querySelector<HTMLElement>(`[formControlName="${name}"]`) ??
      root.querySelector<HTMLElement>(`[name="${name}"]`);

    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      // Defer focus until after scroll starts so it doesn't fight the smooth scroll.
      setTimeout(() => el.focus({ preventScroll: true }), 0);
      return;
    }
  }
}
