import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { CalendarWidgetComponent } from './calendar-widget.component';

@Component({
  standalone: true,
  imports: [CalendarWidgetComponent],
  template: `<app-calendar-widget [started]="started()" />`,
})
class HostComponent {
  readonly started = signal(false);
}

describe('CalendarWidgetComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders 42 cells (7×6 grid)', () => {
    const cells = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="cal-cell"]');
    expect(cells.length).toBe(42);
  });

  it('marks ~25 cells as filled in the data model (regardless of started)', () => {
    const filled = (fixture.nativeElement as HTMLElement).querySelectorAll('.cal-cell.is-filled');
    expect(filled.length).toBeGreaterThan(20);
    expect(filled.length).toBeLessThan(30);
  });

  it('toggles is-active on filled cells when started becomes true', () => {
    fixture.componentInstance.started.set(true);
    fixture.detectChanges();
    const active = (fixture.nativeElement as HTMLElement).querySelectorAll('.cal-cell.is-filled.is-active');
    expect(active.length).toBeGreaterThan(20);
  });

  it('renders the label', () => {
    const label = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="cal-label"]');
    expect(label).not.toBeNull();
  });
});
