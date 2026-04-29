import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { CreateEmployeeComponent } from './create-employee.component';

/**
 * Pin the autocomplete attributes that prevent the browser from autofilling
 * the new-collaborator form with the logged-in pro's saved credentials.
 */
describe('CreateEmployeeComponent — autofill guards', () => {
  let host: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        CreateEmployeeComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close: () => undefined } },
        { provide: MAT_DIALOG_DATA, useValue: { cares: [] } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CreateEmployeeComponent);
    fixture.detectChanges();
    host = fixture.nativeElement;
  });

  it('the form has autocomplete="off"', () => {
    const form = host.querySelector('form.employee-form') as HTMLFormElement;
    expect(form).toBeTruthy();
    expect(form.getAttribute('autocomplete')).toBe('off');
  });

  it('email input has autocomplete="off" so the browser does not inject the pro email', () => {
    const email = host.querySelector('[data-testid="employee-email"]') as HTMLInputElement;
    expect(email).toBeTruthy();
    expect(email.getAttribute('autocomplete')).toBe('off');
  });

  it('password input has autocomplete="new-password" (the recommended hint)', () => {
    const password = host.querySelector('[data-testid="employee-password"]') as HTMLInputElement;
    expect(password).toBeTruthy();
    expect(password.getAttribute('autocomplete')).toBe('new-password');
    // sanity: keep the password input type
    expect(password.getAttribute('type')).toBe('password');
  });

  it('name input also opts out of autocomplete', () => {
    const name = host.querySelector('[data-testid="employee-name"]') as HTMLInputElement;
    expect(name).toBeTruthy();
    expect(name.getAttribute('autocomplete')).toBe('off');
  });
});
