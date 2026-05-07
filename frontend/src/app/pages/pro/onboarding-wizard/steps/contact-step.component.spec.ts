import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { ContactStepComponent } from './contact-step.component';

describe('ContactStepComponent', () => {
  let fixture: ComponentFixture<ContactStepComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://t' },
      ],
      imports: [ContactStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(ContactStepComponent);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function setField(name: string, value: string) {
    const el = fixture.nativeElement.querySelector(`[name="${name}"]`);
    el.value = value;
    el.dispatchEvent(new Event('input'));
  }

  it('disables next while address incomplete', () => {
    fixture.detectChanges();
    setField('street', '1 rue X');
    setField('city', 'Paris');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="next"]').disabled).toBeTrue();
  });

  it('disables next while neither phone nor email', () => {
    fixture.detectChanges();
    setField('street', '1 rue X');
    setField('postalCode', '75011');
    setField('city', 'Paris');
    setField('country', 'FR');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="next"]').disabled).toBeTrue();
  });

  it('PATCHes with all fields when valid', () => {
    fixture.detectChanges();
    setField('street', '1 rue X');
    setField('postalCode', '75011');
    setField('city', 'Paris');
    setField('country', 'FR');
    setField('phone', '0102030405');
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => done = true);
    fixture.nativeElement.querySelector('[data-testid="next"]').click();
    const req = http.expectOne('http://t/api/pro/tenant');
    expect(req.request.body).toEqual(jasmine.objectContaining({
      addressStreet: '1 rue X',
      addressPostalCode: '75011',
      addressCity: 'Paris',
      addressCountry: 'FR',
      phone: '0102030405',
    }));
    req.flush({});
    expect(done).toBeTrue();
  });
});
