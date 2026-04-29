import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { ProSettingsComponent } from './pro-settings.component';
import { TenantFeaturesService } from '../../core/tenant/tenant-features.service';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

/**
 * Pinned behavior: numeric settings inputs (min advance, max advance, max
 * client hours) silently dropped negative values, so a pro who typed -5 saw
 * the value linger in the input but never persisted. We now route invalid
 * input through a snackbar so the pro understands what happened.
 */
describe('ProSettingsComponent — numeric input guards', () => {
  let component: any;
  let featuresService: any;
  let snackOpen: jasmine.Spy;

  beforeEach(async () => {
    featuresService = {
      employeesEnabled: signal(false),
      annualLeaveDays: signal(25),
      closedOnHolidays: signal(true),
      minAdvanceMinutes: signal(120),
      maxAdvanceDays: signal(90),
      maxClientHoursPerDay: signal(8),
      toggleEmployees: jasmine.createSpy('toggleEmployees'),
      setAnnualLeaveDays: jasmine.createSpy('setAnnualLeaveDays'),
      toggleClosedOnHolidays: jasmine.createSpy('toggleClosedOnHolidays'),
      setMinAdvanceMinutes: jasmine.createSpy('setMinAdvanceMinutes'),
      setMaxAdvanceDays: jasmine.createSpy('setMaxAdvanceDays'),
      setMaxClientHoursPerDay: jasmine.createSpy('setMaxClientHoursPerDay'),
    };

    snackOpen = jasmine.createSpy('open');
    const snackBar = { open: snackOpen } as unknown as MatSnackBar;

    await TestBed.configureTestingModule({
      imports: [
        ProSettingsComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
        { provide: TenantFeaturesService, useValue: featuresService },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProSettingsComponent);
    component = fixture.componentInstance;
    // Replace inject() captures with our mock — see CaresComponent spec for the
    // same pattern. inject() resolves dependencies at construction time, before
    // our spy override has the chance to land.
    component.snackBar = snackBar;
  });

  describe('onMinAdvanceChange', () => {
    it('persists a positive integer', () => {
      component.onMinAdvanceChange('60');
      expect(featuresService.setMinAdvanceMinutes).toHaveBeenCalledWith(60);
      expect(snackOpen).not.toHaveBeenCalled();
    });

    it('persists zero (allowed boundary)', () => {
      component.onMinAdvanceChange('0');
      expect(featuresService.setMinAdvanceMinutes).toHaveBeenCalledWith(0);
      expect(snackOpen).not.toHaveBeenCalled();
    });

    it('rejects negative input AND surfaces a snackbar', () => {
      component.onMinAdvanceChange('-5');
      expect(featuresService.setMinAdvanceMinutes).not.toHaveBeenCalled();
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });

    it('rejects non-numeric input and surfaces a snackbar', () => {
      component.onMinAdvanceChange('abc');
      expect(featuresService.setMinAdvanceMinutes).not.toHaveBeenCalled();
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });

    it('rejects empty string', () => {
      component.onMinAdvanceChange('');
      expect(featuresService.setMinAdvanceMinutes).not.toHaveBeenCalled();
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });
  });

  describe('onMaxAdvanceChange', () => {
    it('persists a positive integer', () => {
      component.onMaxAdvanceChange('30');
      expect(featuresService.setMaxAdvanceDays).toHaveBeenCalledWith(30);
      expect(snackOpen).not.toHaveBeenCalled();
    });

    it('rejects negative and surfaces snackbar', () => {
      component.onMaxAdvanceChange('-1');
      expect(featuresService.setMaxAdvanceDays).not.toHaveBeenCalled();
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });
  });

  describe('onMaxClientHoursChange', () => {
    it('persists a positive integer', () => {
      component.onMaxClientHoursChange('4');
      expect(featuresService.setMaxClientHoursPerDay).toHaveBeenCalledWith(4);
      expect(snackOpen).not.toHaveBeenCalled();
    });

    it('rejects negative and surfaces snackbar', () => {
      component.onMaxClientHoursChange('-3');
      expect(featuresService.setMaxClientHoursPerDay).not.toHaveBeenCalled();
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });

    it('rejects floating-point input by parseInt truncation but still warns on negative', () => {
      // parseInt('-2.5') = -2 → still negative → snackbar
      component.onMaxClientHoursChange('-2.5');
      expect(featuresService.setMaxClientHoursPerDay).not.toHaveBeenCalled();
      expect(snackOpen).toHaveBeenCalledTimes(1);
    });
  });
});
