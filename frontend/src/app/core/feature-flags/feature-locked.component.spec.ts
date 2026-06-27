import { Component, signal } from '@angular/core';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { FeatureLockedComponent } from './feature-locked.component';
import { FeatureFlagsStore } from './feature-flags.store';

@Component({
  standalone: true,
  imports: [FeatureLockedComponent],
  template: `<lp-feature-locked feature="SHOP"><button>Inner</button></lp-feature-locked>`,
})
class HostComponent {}

describe('FeatureLockedComponent', () => {
  let storeStub: { isEnabled: jasmine.Spy };

  beforeEach(() => {
    storeStub = { isEnabled: jasmine.createSpy('isEnabled') };
    TestBed.configureTestingModule({
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {}, en: {} },
          translocoConfig: { availableLangs: ['fr', 'en'], defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: FeatureFlagsStore, useValue: storeStub },
      ],
    });
  });

  it('shows overlay + grey content when feature disabled', () => {
    storeStub.isEnabled.and.returnValue(signal(false));
    const fx = TestBed.createComponent(HostComponent);
    fx.detectChanges();
    expect(fx.nativeElement.querySelector('[data-testid="upsell-overlay"]')).toBeTruthy();
    expect(fx.nativeElement.querySelector('button')).toBeTruthy(); // content still in DOM
  });

  it('hides overlay when feature enabled', () => {
    storeStub.isEnabled.and.returnValue(signal(true));
    const fx = TestBed.createComponent(HostComponent);
    fx.detectChanges();
    expect(fx.nativeElement.querySelector('[data-testid="upsell-overlay"]')).toBeNull();
    expect(fx.nativeElement.querySelector('button')).toBeTruthy();
  });
});
