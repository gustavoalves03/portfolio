import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { TourBubbleComponent } from './tour-bubble.component';
import { TourStep } from '../../../features/onboarding/tour/tour-step.model';

const STEP: TourStep = {
  key: 'logo',
  readinessFlag: 'hasLogo',
  route: '/pro/salon',
  tourStep: 'logo',
  titleKey: 'pro.tour.steps.logo.title',
  descKey: 'pro.tour.steps.logo.desc',
};

const RECT = {
  x: 100,
  y: 200,
  width: 240,
  height: 80,
  top: 200,
  left: 100,
  right: 340,
  bottom: 280,
  toJSON: () => ({}),
} as DOMRect;

describe('TourBubbleComponent', () => {
  let fixture: ComponentFixture<TourBubbleComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        TourBubbleComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(TourBubbleComponent);
  });

  it('shows transition success line when inTransition is true', () => {
    fixture.componentRef.setInput('step', STEP);
    fixture.componentRef.setInput('progress', { done: 3, total: 6 });
    fixture.componentRef.setInput('inTransition', true);
    fixture.componentRef.setInput('targetRect', RECT);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="tour-bubble-success"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="tour-bubble-later"]')).toBeNull();
  });

  it('emits (close) when the "Later" button is clicked', () => {
    fixture.componentRef.setInput('step', STEP);
    fixture.componentRef.setInput('progress', { done: 0, total: 6 });
    fixture.componentRef.setInput('inTransition', false);
    fixture.componentRef.setInput('targetRect', RECT);
    fixture.detectChanges();
    let closed = false;
    fixture.componentInstance.close.subscribe(() => (closed = true));
    fixture.nativeElement.querySelector('[data-testid="tour-bubble-later"]').click();
    expect(closed).toBeTrue();
  });
});
