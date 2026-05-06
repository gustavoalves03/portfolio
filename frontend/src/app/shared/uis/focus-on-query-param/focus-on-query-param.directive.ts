import { Directive, ElementRef, OnInit, PLATFORM_ID, inject, input } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

/**
 * On initialization, if the current route's `?focus=<value>` query param
 * matches `appFocusOnQueryParam`, the host element receives focus + a
 * temporary `.focus-pulse` class and is scrolled into view.
 *
 * Used to guide the pro toward the field they need to fill after
 * clicking an unfinished step in the onboarding indicator.
 *
 * SSR-safe: the highlight only runs in a browser context.
 */
@Directive({
  selector: '[appFocusOnQueryParam]',
  standalone: true,
})
export class FocusOnQueryParamDirective implements OnInit {
  /** The string the `?focus=` query param must equal. */
  readonly appFocusOnQueryParam = input.required<string>();

  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly route = inject(ActivatedRoute);
  private readonly platformId = inject(PLATFORM_ID);

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const focusValue = this.route.snapshot.queryParamMap.get('focus');
    if (focusValue !== this.appFocusOnQueryParam()) return;
    setTimeout(() => this.applyHighlight(), 0);
  }

  private applyHighlight(): void {
    const target = this.el.nativeElement;
    if (target.scrollIntoView) {
      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    const input = target.querySelector('input, textarea, select') as HTMLElement | null;
    (input ?? target).focus();

    target.classList.add('focus-pulse');
    setTimeout(() => target.classList.remove('focus-pulse'), 2400);
  }
}
