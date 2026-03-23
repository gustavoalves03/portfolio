import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { DiscoverPageComponent } from './discover-page.component';
import { of } from 'rxjs';

function createRoute(params: Record<string, string> = {}) {
  return {
    queryParamMap: of(convertToParamMap(params)),
  };
}

describe('DiscoverPageComponent', () => {
  function setup(params: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [
        DiscoverPageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              discover: {
                placeholder: 'Bientôt disponible',
                backHome: 'Retour',
              },
            },
          },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: createRoute(params) },
      ],
    });

    const fixture = TestBed.createComponent(DiscoverPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    const fixture = setup();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show placeholder title', () => {
    const fixture = setup();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Bientôt disponible');
  });

  it('should show category message when category param is present', () => {
    const fixture = setup({ category: 'ongles' });
    expect(fixture.componentInstance.message()).toContain('ongles');
  });

  it('should show search message when q param is present', () => {
    const fixture = setup({ q: 'visage' });
    expect(fixture.componentInstance.message()).toContain('visage');
  });

  it('should show default message when no params', () => {
    const fixture = setup();
    expect(fixture.componentInstance.message()).toContain('Explorez');
  });
});
