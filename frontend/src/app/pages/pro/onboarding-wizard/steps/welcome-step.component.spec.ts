import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { WelcomeStepComponent } from './welcome-step.component';

describe('WelcomeStepComponent', () => {
  it('emits next on primary CTA click', () => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [WelcomeStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    const fixture: ComponentFixture<WelcomeStepComponent> = TestBed.createComponent(WelcomeStepComponent);
    let emitted = false;
    fixture.componentInstance.completed.subscribe(() => emitted = true);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="welcome-cta"]').click();
    expect(emitted).toBeTrue();
  });
});
