import { Component, ElementRef, ViewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SwipeLeftDirective } from './swipe-left.directive';

@Component({
  standalone: true,
  imports: [SwipeLeftDirective],
  template: `<div #host appSwipeLeft (swipeLeftCommitted)="onCommitted()"></div>`,
})
class HostComponent {
  @ViewChild('host', { static: true }) host!: ElementRef<HTMLElement>;
  committedCount = 0;
  onCommitted(): void { this.committedCount++; }
}

function makePointerEvent(type: string, clientX: number): PointerEvent {
  return new PointerEvent(type, { clientX, pointerId: 1, bubbles: true });
}

function setMobile(isMobile: boolean): void {
  spyOn(window, 'matchMedia').and.returnValue({
    matches: isMobile,
    media: '(max-width: 767px)',
    onchange: null,
    addListener: () => {}, removeListener: () => {},
    addEventListener: () => {}, removeEventListener: () => {},
    dispatchEvent: () => false,
  } as MediaQueryList);
}

describe('SwipeLeftDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('emits swipeLeftCommitted when drag distance exceeds 100px to the left on mobile', (done) => {
    setMobile(true);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 300));
    el.dispatchEvent(makePointerEvent('pointermove', 150));
    el.dispatchEvent(makePointerEvent('pointerup', 150));
    setTimeout(() => {
      expect(host.committedCount).toBe(1);
      done();
    }, 250);
  });

  it('does NOT emit when drag distance is below threshold', (done) => {
    setMobile(true);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 300));
    el.dispatchEvent(makePointerEvent('pointermove', 270));
    el.dispatchEvent(makePointerEvent('pointerup', 270));
    setTimeout(() => {
      expect(host.committedCount).toBe(0);
      done();
    }, 250);
  });

  it('emits on flick (high velocity, short distance)', (done) => {
    setMobile(true);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 300));
    setTimeout(() => {
      el.dispatchEvent(makePointerEvent('pointerup', 260));
      setTimeout(() => {
        expect(host.committedCount).toBe(1);
        done();
      }, 250);
    }, 60);
  });

  it('is a no-op on desktop', () => {
    setMobile(false);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 300));
    el.dispatchEvent(makePointerEvent('pointermove', 100));
    el.dispatchEvent(makePointerEvent('pointerup', 100));
    expect(host.committedCount).toBe(0);
  });

  it('does not emit on rightward drag', (done) => {
    setMobile(true);
    const el = host.host.nativeElement;
    el.dispatchEvent(makePointerEvent('pointerdown', 100));
    el.dispatchEvent(makePointerEvent('pointermove', 300));
    el.dispatchEvent(makePointerEvent('pointerup', 300));
    setTimeout(() => {
      expect(host.committedCount).toBe(0);
      done();
    }, 250);
  });
});
