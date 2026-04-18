import { Directive, ElementRef, HostListener, inject, output } from '@angular/core';

const DISMISS_THRESHOLD_PX = 100;
const DISMISS_VELOCITY = 0.5;
const MIN_FLICK_DURATION_MS = 50;
const MOBILE_QUERY = '(max-width: 767px)';

@Directive({
  selector: '[appSwipeLeft]',
  standalone: true,
})
export class SwipeLeftDirective {
  readonly swipeLeftCommitted = output<void>();

  private readonly host = inject(ElementRef<HTMLElement>);

  private startX = 0;
  private startTime = 0;
  private dragging = false;

  @HostListener('pointerdown', ['$event'])
  onPointerDown(event: PointerEvent): void {
    if (!this.isMobile()) return;
    this.dragging = true;
    this.startX = event.clientX;
    this.startTime = performance.now();
    const el = this.host.nativeElement;
    el.style.transition = 'none';
    try { (event.target as HTMLElement).setPointerCapture?.(event.pointerId); } catch { /* ignore */ }
  }

  @HostListener('pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (!this.dragging) return;
    const delta = Math.min(0, event.clientX - this.startX);
    this.host.nativeElement.style.transform = `translateX(${delta}px)`;
  }

  @HostListener('pointerup', ['$event'])
  @HostListener('pointercancel', ['$event'])
  onPointerUp(event: PointerEvent): void {
    if (!this.dragging) return;
    this.dragging = false;
    const delta = Math.min(0, event.clientX - this.startX);
    const absDelta = Math.abs(delta);
    const duration = performance.now() - this.startTime;
    const velocity = duration >= MIN_FLICK_DURATION_MS ? absDelta / duration : 0;
    const el = this.host.nativeElement;

    if (absDelta > DISMISS_THRESHOLD_PX || velocity > DISMISS_VELOCITY) {
      el.style.transition = 'transform 200ms ease-in';
      el.style.transform = 'translateX(-100%)';
      setTimeout(() => this.swipeLeftCommitted.emit(), 200);
    } else {
      el.style.transition = 'transform 200ms ease-out';
      el.style.transform = 'translateX(0)';
    }
  }

  private isMobile(): boolean {
    return typeof window !== 'undefined'
      && typeof window.matchMedia === 'function'
      && window.matchMedia(MOBILE_QUERY).matches;
  }
}
