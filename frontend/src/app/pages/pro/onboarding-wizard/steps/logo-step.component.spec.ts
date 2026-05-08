import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { LogoStepComponent } from './logo-step.component';

describe('LogoStepComponent', () => {
  let fixture: ComponentFixture<LogoStepComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://t' },
      ],
      imports: [
        LogoStepComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(LogoStepComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('disables next when no logo provided AND initialLogoUrl is null', () => {
    fixture.componentRef.setInput('initialLogoUrl', null);
    fixture.detectChanges();
    const nextBtn = fixture.nativeElement.querySelector('[data-testid="next"]') as HTMLButtonElement;
    expect(nextBtn.disabled).toBeTrue();
  });

  it('reads file as data URL on file selection', async () => {
    fixture.componentRef.setInput('initialLogoUrl', null);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('[data-testid="file-input"]') as HTMLInputElement;
    const file = new File([new Uint8Array([1, 2, 3])], 'logo.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));
    await new Promise(r => setTimeout(r, 50));
    fixture.detectChanges();
    expect(fixture.componentInstance['dataUrl']()).toMatch(/^data:image\/png;base64,/);
  });

  it('PATCHes logo and emits completed on success', async () => {
    fixture.componentRef.setInput('initialLogoUrl', null);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('[data-testid="file-input"]') as HTMLInputElement;
    const file = new File([new Uint8Array([1, 2, 3])], 'logo.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));
    await new Promise(r => setTimeout(r, 50));
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => (done = true));
    fixture.nativeElement.querySelector('[data-testid="next"]').click();
    const req = http.expectOne('http://t/api/pro/tenant');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.logo).toMatch(/^data:image\/png;base64,/);
    req.flush({});
    expect(done).toBeTrue();
  });
});
