import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { PublishStepComponent } from './publish-step.component';

describe('PublishStepComponent', () => {
  let fixture: ComponentFixture<PublishStepComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://t' },
      ],
      imports: [PublishStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(PublishStepComponent);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('renders recap with provided name and city', () => {
    fixture.componentRef.setInput('recap', { name: 'Belle de Nuit', addressCity: 'Paris', logoUrl: null, slug: 'belle' });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Belle de Nuit');
    expect(text).toContain('Paris');
  });

  it('preview link points to /salon/<slug>', () => {
    fixture.componentRef.setInput('recap', { name: 'X', addressCity: null, logoUrl: null, slug: 'belle' });
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('[data-testid="preview"]');
    expect(link.getAttribute('href')).toContain('/salon/belle');
  });

  it('clicking publish PUTs and emits completed on success', () => {
    fixture.componentRef.setInput('recap', { name: 'X', addressCity: null, logoUrl: null, slug: 'belle' });
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => done = true);
    fixture.nativeElement.querySelector('[data-testid="publish"]').click();
    const req = http.expectOne('http://t/api/pro/tenant/publish');
    expect(req.request.method).toBe('PUT');
    req.flush(null);
    expect(done).toBeTrue();
  });
});
