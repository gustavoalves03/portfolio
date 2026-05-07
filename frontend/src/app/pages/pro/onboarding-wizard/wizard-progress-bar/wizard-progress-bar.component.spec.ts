import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { WizardProgressBarComponent } from './wizard-progress-bar.component';

describe('WizardProgressBarComponent', () => {
  let fixture: ComponentFixture<WizardProgressBarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        WizardProgressBarComponent,
        TranslocoTestingModule.forRoot({ langs: { en: {} }, translocoConfig: { availableLangs: ['en'], defaultLang: 'en' } }),
      ],
    });
    fixture = TestBed.createComponent(WizardProgressBarComponent);
  });

  it('marks segments before currentIndex as done and emits stepClick on done segments', () => {
    fixture.componentRef.setInput('currentIndex', 3);
    fixture.componentRef.setInput('totalSteps', 7);
    fixture.detectChanges();

    const segments = fixture.nativeElement.querySelectorAll('.wpb-segment');
    expect(segments.length).toBe(7);
    expect(segments[0].classList.contains('is-done')).toBeTrue();
    expect(segments[2].classList.contains('is-done')).toBeTrue();
    expect(segments[3].classList.contains('is-current')).toBeTrue();
    expect(segments[4].classList.contains('is-done')).toBeFalse();
  });
});
