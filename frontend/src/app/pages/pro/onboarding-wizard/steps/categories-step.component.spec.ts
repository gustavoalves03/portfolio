import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../../core/config/api-base-url.token';
import { CategoriesStepComponent } from './categories-step.component';

describe('CategoriesStepComponent', () => {
  let fixture: ComponentFixture<CategoriesStepComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://t' },
      ],
      imports: [CategoriesStepComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(CategoriesStepComponent);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('renders 10 category chips', () => {
    fixture.detectChanges();
    const chips = fixture.nativeElement.querySelectorAll('[data-testid^="category-chip-"]');
    expect(chips.length).toBe(10);
  });

  it('toggles chip selected state on click', () => {
    fixture.detectChanges();
    const chip = fixture.nativeElement.querySelector('[data-testid="category-chip-facial"]');
    chip.click();
    fixture.detectChanges();
    expect(chip.classList.contains('is-selected')).toBeTrue();
    chip.click();
    fixture.detectChanges();
    expect(chip.classList.contains('is-selected')).toBeFalse();
  });

  it('disables next while no chip selected', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="next"]').disabled).toBeTrue();
  });

  it('PATCHes selected chips as comma-separated slugs and emits completed', () => {
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="category-chip-facial"]').click();
    fixture.nativeElement.querySelector('[data-testid="category-chip-hair"]').click();
    fixture.detectChanges();
    let done = false;
    fixture.componentInstance.completed.subscribe(() => done = true);
    fixture.nativeElement.querySelector('[data-testid="next"]').click();
    const req = http.expectOne('http://t/api/pro/tenant');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.categorySlugs).toBe('facial,hair');
    req.flush({});
    expect(done).toBeTrue();
  });
});
