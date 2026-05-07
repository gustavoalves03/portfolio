import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { NameStepComponent } from './name-step.component';

describe('NameStepComponent', () => {
  let fixture: ComponentFixture<NameStepComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://t' },
      ],
      imports: [NameStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(NameStepComponent);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('disables next while empty', () => {
    fixture.componentRef.setInput('initialName', null);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="next"]');
    expect(btn.disabled).toBeTrue();
  });

  it('PATCHes the name and emits completed on success', async () => {
    fixture.componentRef.setInput('initialName', null);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input');
    input.value = 'Belle de Nuit';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => done = true);

    fixture.nativeElement.querySelector('[data-testid="next"]').click();
    const req = http.expectOne('http://t/api/pro/tenant');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ name: 'Belle de Nuit' });
    req.flush({});
    expect(done).toBeTrue();
  });
});
