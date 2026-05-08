import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { TourOverlayComponent } from './tour-overlay.component';
import { TourService } from '../../../features/onboarding/tour/tour.service';
import { TourStep } from '../../../features/onboarding/tour/tour-step.model';

const NAME_STEP: TourStep = {
  key: 'name',
  readinessFlag: 'name',
  route: '/pro/salon',
  tourStep: 'name',
  titleKey: 'pro.tour.steps.name.title',
  descKey: 'pro.tour.steps.name.desc',
};

describe('TourOverlayComponent', () => {
  let fixture: ComponentFixture<TourOverlayComponent>;
  let tourActive: ReturnType<typeof signal<boolean>>;
  let tourCurrent: ReturnType<typeof signal<TourStep | null>>;
  let tourTransition: ReturnType<typeof signal<boolean>>;
  let stopSpy: jasmine.Spy;

  function setup() {
    tourActive = signal(false);
    tourCurrent = signal<TourStep | null>(null);
    tourTransition = signal(false);
    stopSpy = jasmine.createSpy('stop');
    const tourStub = {
      active: tourActive,
      currentStep: tourCurrent,
      inTransition: tourTransition,
      progress: signal({ done: 0, total: 6 }),
      stop: stopSpy,
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: TourService, useValue: tourStub },
      ],
      imports: [
        TourOverlayComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    // jsdom/karma doesn't implement scrollIntoView
    Element.prototype.scrollIntoView = function () {};
    fixture = TestBed.createComponent(TourOverlayComponent);
    fixture.detectChanges();
  }

  afterEach(() => {
    document.querySelectorAll('[data-tour-step]').forEach((el) => el.remove());
  });

  it('renders nothing while tour is inactive', () => {
    setup();
    expect(fixture.nativeElement.querySelector('.tour-overlay')).toBeNull();
  });

  it('binds to [data-tour-step="name"] when current step is the name step', () => {
    setup();
    const target = document.createElement('div');
    target.setAttribute('data-tour-step', 'name');
    target.style.cssText = 'position:fixed;top:50px;left:60px;width:200px;height:40px';
    document.body.appendChild(target);

    tourActive.set(true);
    tourCurrent.set(NAME_STEP);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.tour-halo')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-tour-bubble')).not.toBeNull();
  });

  it('retries 3 times then stops the tour when target is missing', () => {
    jasmine.clock().install();
    try {
      setup();
      tourActive.set(true);
      tourCurrent.set(NAME_STEP);
      fixture.detectChanges();
      // Each retry waits 500ms; 3 retries = 1500ms total before warn + stop.
      jasmine.clock().tick(500);
      jasmine.clock().tick(500);
      jasmine.clock().tick(500);
      expect(stopSpy).toHaveBeenCalled();
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('cleans up observers and listeners on destroy', () => {
    setup();
    const target = document.createElement('div');
    target.setAttribute('data-tour-step', 'name');
    document.body.appendChild(target);

    tourActive.set(true);
    tourCurrent.set(NAME_STEP);
    fixture.detectChanges();
    const removeSpy = spyOn(window, 'removeEventListener').and.callThrough();
    fixture.destroy();
    expect(removeSpy).toHaveBeenCalledWith('scroll', jasmine.any(Function));
  });
});
