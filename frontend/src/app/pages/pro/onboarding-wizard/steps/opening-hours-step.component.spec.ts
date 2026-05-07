import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { OpeningHoursStepComponent } from './opening-hours-step.component';

describe('OpeningHoursStepComponent', () => {
  let fixture: ComponentFixture<OpeningHoursStepComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://t' },
      ],
      imports: [OpeningHoursStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(OpeningHoursStepComponent);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('renders 7 day rows', () => {
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('[data-testid^="day-row-"]');
    expect(rows.length).toBe(7);
  });

  it('disables next while no day is open', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="next"]').disabled).toBeTrue();
  });

  it('preset "weekdays 9-19" fills 5 weekdays and leaves weekend closed', () => {
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="preset-weekdays"]').click();
    fixture.detectChanges();
    // 5 weekday rows should now show "open"
    const opens = fixture.nativeElement.querySelectorAll('[data-testid^="day-row-"].is-open');
    expect(opens.length).toBe(5);
    expect(fixture.nativeElement.querySelector('[data-testid="next"]').disabled).toBeFalse();
  });

  it('submits open rows via PUT and emits completed', () => {
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="preset-weekdays"]').click();
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => done = true);
    fixture.nativeElement.querySelector('[data-testid="next"]').click();
    const req = http.expectOne('http://t/api/pro/opening-hours');
    expect(req.request.method).toBe('PUT');
    const body = req.request.body as Array<{ dayOfWeek: number; openTime: string; closeTime: string }>;
    expect(body.length).toBe(5);
    expect(body[0]).toEqual({ dayOfWeek: 1, openTime: '09:00', closeTime: '19:00' });
    expect(body[4].dayOfWeek).toBe(5);
    req.flush([]);
    expect(done).toBeTrue();
  });
});
