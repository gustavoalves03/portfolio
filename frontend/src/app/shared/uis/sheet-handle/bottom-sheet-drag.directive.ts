import { Directive, ElementRef, HostListener, inject } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';

const DISMISS_THRESHOLD_PX = 100;
const DISMISS_VELOCITY = 0.5; // px per ms
const MIN_FLICK_DURATION_MS = 50; // below this, velocity is not a reliable flick signal
const MOBILE_QUERY = '(max-width: 767px)';

@Directive({
  selector: '[appBottomSheetDrag]',
  standalone: true,
})
export class BottomSheetDragDirective {
  private readonly dialogRef = inject(MatDialogRef, { optional: true });
  private readonly host = inject(ElementRef<HTMLElement>);

  private startY = 0;
  private startTime = 0;
  private dragging = false;
  private surface: HTMLElement | null = null;

  @HostListener('pointerdown', ['$event'])
  onPointerDown(event: PointerEvent): void {
    if (!this.dialogRef || !this.isMobile()) return;
    this.surface = this.findSurface();
    if (!this.surface) return;

    this.dragging = true;
    this.startY = event.clientY;
    this.startTime = performance.now();
    this.surface.style.transition = 'none';

    const target = event.target as HTMLElement;
    if (target.setPointerCapture) {
      try {
        target.setPointerCapture(event.pointerId);
      } catch {
        // setPointerCapture can throw in some environments; safe to ignore.
      }
    }
  }

  @HostListener('pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (!this.dragging || !this.surface) return;
    const delta = Math.max(0, event.clientY - this.startY);
    this.surface.style.transform = `translateY(${delta}px)`;
  }

  @HostListener('pointerup', ['$event'])
  @HostListener('pointercancel', ['$event'])
  onPointerUp(event: PointerEvent): void {
    if (!this.dragging || !this.surface) return;
    this.dragging = false;

    const delta = Math.max(0, event.clientY - this.startY);
    const duration = performance.now() - this.startTime;
    const isFlick =
      duration >= MIN_FLICK_DURATION_MS && delta / duration > DISMISS_VELOCITY;

    if (delta > DISMISS_THRESHOLD_PX || isFlick) {
      this.dismiss();
    } else {
      this.snapBack();
    }
  }

  private findSurface(): HTMLElement | null {
    const el = this.host.nativeElement;
    return (
      (el.closest('.mdc-dialog__surface') as HTMLElement | null) ??
      (el.closest('.mat-mdc-dialog-surface') as HTMLElement | null)
    );
  }

  private isMobile(): boolean {
    return (
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia(MOBILE_QUERY).matches
    );
  }

  private snapBack(): void {
    if (!this.surface) return;
    this.surface.style.transition = 'transform 200ms ease-out';
    this.surface.style.transform = 'translateY(0)';
  }

  private dismiss(): void {
    if (!this.surface || !this.dialogRef) return;
    this.surface.style.transition = 'transform 200ms ease-in';
    this.surface.style.transform = 'translateY(100%)';
    setTimeout(() => this.dialogRef!.close(), 200);
  }
}
