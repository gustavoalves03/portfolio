import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { ReviewsWidgetComponent } from './reviews-widget.component';

@Component({
  standalone: true,
  imports: [ReviewsWidgetComponent],
  template: `<app-reviews-widget [started]="started()" />`,
})
class HostComponent {
  readonly started = signal(false);
}

describe('ReviewsWidgetComponent', () => {
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

  it('renders 5 stars', () => {
    const stars = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="rev-star"]');
    expect(stars.length).toBe(5);
  });

  it('renders 3 quote elements', () => {
    const quotes = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="rev-quote"]');
    expect(quotes.length).toBe(3);
  });

  it('renders the rating value', () => {
    const value = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="rev-value"]');
    expect(value).not.toBeNull();
  });

  it('toggles is-active on stars when started becomes true', () => {
    fixture.componentInstance.started.set(true);
    fixture.detectChanges();
    const active = (fixture.nativeElement as HTMLElement).querySelectorAll('.rev-star.is-active');
    expect(active.length).toBe(5);
  });
});
