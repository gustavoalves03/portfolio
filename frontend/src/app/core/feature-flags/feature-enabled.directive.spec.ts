import { Component, signal } from '@angular/core';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FeatureEnabledDirective } from './feature-enabled.directive';
import { FeatureFlagsStore } from './feature-flags.store';

@Component({
  standalone: true,
  imports: [FeatureEnabledDirective],
  template: `<span *lpFeatureEnabled="'SHOP'" data-testid="shop">Shop</span>`,
})
class HostComponent {}

describe('FeatureEnabledDirective', () => {
  let storeStub: { isEnabled: jasmine.Spy };

  beforeEach(() => {
    storeStub = { isEnabled: jasmine.createSpy('isEnabled') };
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: FeatureFlagsStore, useValue: storeStub },
      ],
    });
  });

  it('renders host element when feature is enabled', () => {
    storeStub.isEnabled.and.returnValue(signal(true));
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="shop"]');
    expect(el).toBeTruthy();
  });

  it('removes host element when feature is disabled', () => {
    storeStub.isEnabled.and.returnValue(signal(false));
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement.querySelector('[data-testid="shop"]');
    expect(el).toBeNull();
  });

  it('reactively updates when the signal changes', () => {
    const flag = signal(true);
    storeStub.isEnabled.and.returnValue(flag);
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="shop"]')).toBeTruthy();

    flag.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="shop"]')).toBeNull();
  });
});
