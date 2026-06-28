import {
  Directive,
  ElementRef,
  OnDestroy,
  PLATFORM_ID,
  Renderer2,
  inject,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Adds a show/hide (eye) toggle to a password input.
 *
 * Usage: <input matInput type="password" appPasswordToggle />
 *
 * The directive injects a small button into the input's parent and flips the
 * input's `type` between `password` and `text`. It positions itself absolutely
 * over the field's trailing edge, which works for Material `mat-form-field`
 * (and any wrapper) without needing a `matSuffix` known at compile time.
 */
@Directive({
  selector: 'input[appPasswordToggle]',
  standalone: true,
})
export class PasswordToggleDirective implements OnDestroy {
  private readonly el = inject(ElementRef<HTMLInputElement>);
  private readonly renderer = inject(Renderer2);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private button?: HTMLButtonElement;
  private visible = false;

  ngOnInit(): void {
    // DOM injection + getComputedStyle are browser-only; skip on the server
    // (SSR/prerender). The input still works without the toggle until hydration.
    if (!this.isBrowser) return;

    const input = this.el.nativeElement as HTMLInputElement;

    // Make room for the button so it never overlaps the typed text.
    this.renderer.setStyle(input, 'padding-right', '40px');

    const btn = this.renderer.createElement('button') as HTMLButtonElement;
    this.renderer.setAttribute(btn, 'type', 'button');
    this.renderer.setAttribute(btn, 'tabindex', '-1');
    this.renderer.setAttribute(btn, 'aria-label', 'Afficher/Masquer le mot de passe');
    this.renderer.addClass(btn, 'pwd-toggle-btn');
    this.applyButtonStyles(btn);
    btn.innerHTML = this.eyeIcon(false);

    // Anchor the button to the field. mat-form-field's flex wrapper is a good
    // host; fall back to the input's parent.
    const host =
      input.closest('.mat-mdc-text-field-wrapper') ??
      input.closest('.mat-mdc-form-field-flex') ??
      input.parentElement;
    if (host) {
      const pos = getComputedStyle(host as HTMLElement).position;
      if (pos === 'static' || !pos) {
        this.renderer.setStyle(host, 'position', 'relative');
      }
      this.renderer.appendChild(host, btn);
    }

    this.renderer.listen(btn, 'click', (e: Event) => {
      e.preventDefault();
      e.stopPropagation();
      this.toggle();
    });

    this.button = btn;
  }

  private toggle(): void {
    this.visible = !this.visible;
    const input = this.el.nativeElement as HTMLInputElement;
    this.renderer.setAttribute(input, 'type', this.visible ? 'text' : 'password');
    if (this.button) this.button.innerHTML = this.eyeIcon(this.visible);
  }

  private applyButtonStyles(btn: HTMLButtonElement): void {
    Object.assign(btn.style, {
      position: 'absolute',
      top: '50%',
      right: '8px',
      transform: 'translateY(-50%)',
      zIndex: '2',
      width: '32px',
      height: '32px',
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      border: 'none',
      background: 'transparent',
      cursor: 'pointer',
      padding: '0',
      color: 'rgba(43, 31, 37, 0.55)',
    });
  }

  /** Inline SVG eye / eye-off, so the directive has no icon-font dependency. */
  private eyeIcon(visible: boolean): string {
    return visible
      ? `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>`
      : `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>`;
  }

  ngOnDestroy(): void {
    if (this.button?.parentElement) {
      this.renderer.removeChild(this.button.parentElement, this.button);
    }
  }
}
