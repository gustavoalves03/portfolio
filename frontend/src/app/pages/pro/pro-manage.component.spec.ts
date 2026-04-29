import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { ProManageComponent } from './pro-manage.component';

/**
 * Pinned behavior: /pro/manage used to flash raw i18n keys
 * (pro.manage.title, ...) on slow navigations. The fix gates the template
 * on an i18nReady signal that flips to true once selectTranslation emits.
 */
describe('ProManageComponent — i18n flash guard', () => {
  it('renders the cards once translations have resolved', () => {
    TestBed.configureTestingModule({
      imports: [
        ProManageComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
      ],
    });

    const fixture = TestBed.createComponent(ProManageComponent);
    fixture.detectChanges();

    // TranslocoTestingModule emits synchronously, so by the first detectChanges
    // i18nReady should already be true.
    expect((fixture.componentInstance as any).i18nReady()).toBeTrue();
    const cards = fixture.nativeElement.querySelectorAll('.manage-card');
    expect(cards.length).toBe(6);
  });

  it('exposes the 6 management cards in stable order', () => {
    TestBed.configureTestingModule({
      imports: [
        ProManageComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
      ],
    });

    const fixture = TestBed.createComponent(ProManageComponent);
    const paths = (fixture.componentInstance as any).cards.map((c: any) => c.path);
    expect(paths).toEqual([
      '/pro/planning',
      '/pro/employees',
      '/pro/cares',
      '/pro/dashboard',
      '/pro/settings/history',
      '/pro/settings',
    ]);
  });
});
