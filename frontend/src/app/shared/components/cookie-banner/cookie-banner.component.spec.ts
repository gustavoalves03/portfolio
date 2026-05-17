import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { CookieBannerComponent } from './cookie-banner.component';
import { CookieBannerService } from './cookie-banner.service';
import { STORAGE_KEY } from './cookie-banner.service';

describe('CookieBannerComponent', () => {
  let fixture: ComponentFixture<CookieBannerComponent>;
  let service: CookieBannerService;

  beforeEach(async () => {
    localStorage.removeItem(STORAGE_KEY);
    await TestBed.configureTestingModule({
      imports: [
        CookieBannerComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: { cookieBanner: { message: 'msg', learnMore: 'more', dismiss: 'ok', ariaLabel: 'aria' } } },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CookieBannerComponent);
    service = TestBed.inject(CookieBannerService);
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.removeItem(STORAGE_KEY);
  });

  it('renders the banner when not dismissed', () => {
    expect(service.dismissed()).toBe(false);
    const el = fixture.nativeElement.querySelector('.cookie-banner');
    expect(el).toBeTruthy();
  });

  it('hides the banner after dismiss() is called', () => {
    service.dismiss();
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('.cookie-banner');
    expect(el).toBeNull();
  });

  it('the dismiss button calls service.dismiss()', () => {
    const spy = spyOn(service, 'dismiss').and.callThrough();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.cookie-banner__btn');
    button.click();
    expect(spy).toHaveBeenCalled();
  });
});
