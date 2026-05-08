import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { PersonaSetupService } from '../../../../features/onboarding/persona-setup.service';
import { CaresStepComponent } from './cares-step.component';

describe('CaresStepComponent', () => {
  let fixture: ComponentFixture<CaresStepComponent>;
  let personaSpy: jasmine.SpyObj<PersonaSetupService>;

  beforeEach(() => {
    personaSpy = jasmine.createSpyObj<PersonaSetupService>('PersonaSetupService', ['apply']);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: 'http://t' },
        { provide: PersonaSetupService, useValue: personaSpy },
      ],
      imports: [CaresStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(CaresStepComponent);
  });

  it('renders 4 persona cards', () => {
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('[data-testid^="persona-card-"]');
    expect(cards.length).toBe(4);
  });

  it('clicking a persona card calls personaSetup.apply and emits completed on success', () => {
    personaSpy.apply.and.returnValue(of({ categoriesCreated: 1, caresCreated: 5, failures: 0 }));
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => done = true);
    fixture.nativeElement.querySelector('[data-testid="persona-card-face"]').click();
    fixture.detectChanges();
    expect(personaSpy.apply).toHaveBeenCalled();
    expect(personaSpy.apply.calls.mostRecent().args[0].key).toBe('face');
    expect(done).toBeTrue();
  });

  it('manual link navigates to /pro/cares', () => {
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('[data-testid="manual-link"]');
    expect(link.getAttribute('href')).toContain('/pro/cares');
  });
});
