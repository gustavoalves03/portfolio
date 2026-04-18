import { Component, ViewChild, ElementRef, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { BottomSheetDragDirective } from './bottom-sheet-drag.directive';

@Component({
  standalone: true,
  imports: [BottomSheetDragDirective],
  template: `
    <div class="mdc-dialog__surface">
      <div #handle class="sheet-handle" appBottomSheetDrag></div>
    </div>
  `,
})
class HostComponent {
  @ViewChild('handle', { static: true }) handle!: ElementRef<HTMLElement>;
}

describe('BottomSheetDragDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;
  let dialogRef: jasmine.SpyObj<MatDialogRef<unknown>>;

  function makePointerEvent(type: string, clientY: number): PointerEvent {
    const event = new PointerEvent(type, {
      clientY,
      pointerId: 1,
      bubbles: true,
    });
    return event;
  }

  function setMobileViewport(isMobile: boolean): void {
    spyOn(window, 'matchMedia').and.returnValue({
      matches: isMobile,
      media: '(max-width: 767px)',
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    } as MediaQueryList);
  }

  beforeEach(async () => {
    dialogRef = jasmine.createSpyObj<MatDialogRef<unknown>>('MatDialogRef', ['close']);
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('closes the dialog when drag distance exceeds 100px on mobile', (done) => {
    setMobileViewport(true);
    const el = host.handle.nativeElement;

    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    el.dispatchEvent(makePointerEvent('pointermove', 150));
    el.dispatchEvent(makePointerEvent('pointerup', 250));

    setTimeout(() => {
      expect(dialogRef.close).toHaveBeenCalled();
      done();
    }, 250);
  });

  it('does NOT close when drag distance is below threshold', (done) => {
    setMobileViewport(true);
    const el = host.handle.nativeElement;

    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    el.dispatchEvent(makePointerEvent('pointermove', 130));
    el.dispatchEvent(makePointerEvent('pointerup', 140));

    setTimeout(() => {
      expect(dialogRef.close).not.toHaveBeenCalled();
      done();
    }, 250);
  });

  it('is a no-op on desktop', () => {
    setMobileViewport(false);
    const el = host.handle.nativeElement;

    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    el.dispatchEvent(makePointerEvent('pointermove', 300));
    el.dispatchEvent(makePointerEvent('pointerup', 500));

    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('closes the dialog on a flick gesture (velocity > 0.5 px/ms)', (done) => {
    setMobileViewport(true);
    const el = host.handle.nativeElement;

    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    // Wait > 50ms so MIN_FLICK_DURATION_MS gate passes; move 40px in ~60ms = ~0.67 px/ms (above 0.5)
    setTimeout(() => {
      el.dispatchEvent(makePointerEvent('pointerup', 140));
      // Give the 200ms dismiss animation timer time to fire
      setTimeout(() => {
        expect(dialogRef.close).toHaveBeenCalled();
        done();
      }, 250);
    }, 60);
  });
});
