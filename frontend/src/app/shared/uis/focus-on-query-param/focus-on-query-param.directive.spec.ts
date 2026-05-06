import { Component, viewChild, ElementRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { FocusOnQueryParamDirective } from './focus-on-query-param.directive';

@Component({
  standalone: true,
  imports: [FocusOnQueryParamDirective],
  template: `
    <div #target appFocusOnQueryParam="name">
      <input id="my-input" />
    </div>
  `,
})
class HostComponent {
  readonly target = viewChild<ElementRef<HTMLDivElement>>('target');
}

describe('FocusOnQueryParamDirective', () => {
  beforeEach(() => {
    // jsdom/karma doesn't implement scrollIntoView
    Element.prototype.scrollIntoView = function () {};
    jasmine.clock().install();
  });

  afterEach(() => {
    jasmine.clock().uninstall();
  });

  function setup(focusValue: string | null): ComponentFixture<HostComponent> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(focusValue ? { focus: focusValue } : {}),
            },
          },
        },
      ],
      imports: [HostComponent],
    });
    const f = TestBed.createComponent(HostComponent);
    f.detectChanges();
    return f;
  }

  it('does nothing when query param is missing', () => {
    const fixture = setup(null);
    jasmine.clock().tick(0);
    const target = fixture.componentInstance.target()?.nativeElement;
    expect(target?.classList.contains('focus-pulse')).toBe(false);
  });

  it('does nothing when query param does not match the directive value', () => {
    const fixture = setup('other');
    jasmine.clock().tick(0);
    const target = fixture.componentInstance.target()?.nativeElement;
    expect(target?.classList.contains('focus-pulse')).toBe(false);
  });

  it('adds focus-pulse class when query param matches', () => {
    const fixture = setup('name');
    jasmine.clock().tick(0); // flush the setTimeout(0)
    const target = fixture.componentInstance.target()?.nativeElement;
    expect(target?.classList.contains('focus-pulse')).toBe(true);
  });

  it('removes focus-pulse class after 2400ms', () => {
    const fixture = setup('name');
    jasmine.clock().tick(0); // flush setTimeout(0) → applyHighlight runs, adds class + schedules removal
    const target = fixture.componentInstance.target()?.nativeElement;
    expect(target?.classList.contains('focus-pulse')).toBe(true);
    jasmine.clock().tick(2400); // flush the removal timer
    expect(target?.classList.contains('focus-pulse')).toBe(false);
  });

  it('focuses the inner input when the host contains one', () => {
    const fixture = setup('name');
    jasmine.clock().tick(0);
    const input = fixture.nativeElement.querySelector('#my-input') as HTMLInputElement;
    expect(document.activeElement).toBe(input);
  });
});
