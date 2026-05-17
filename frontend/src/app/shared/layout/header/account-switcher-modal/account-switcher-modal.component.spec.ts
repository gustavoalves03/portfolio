import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';

import { AccountSwitcherModalComponent } from './account-switcher-modal.component';
import { AuthService } from '../../../../core/auth/auth.service';
import { TenantSummary } from '../../../../core/auth/auth.model';

describe('AccountSwitcherModalComponent', () => {
  function setup(
    tenants: TenantSummary[],
    activeTenantId: number | null,
  ): {
    cmp: AccountSwitcherModalComponent;
    auth: jasmine.SpyObj<AuthService>;
    dialogRef: jasmine.SpyObj<MatDialogRef<AccountSwitcherModalComponent>>;
  } {
    const auth = jasmine.createSpyObj<AuthService>(
      'AuthService',
      ['switchTenant', 'navigateByRole'],
      {
        availableTenants: () => tenants,
        activeTenantId: () => activeTenantId,
      } as never,
    );
    auth.switchTenant.and.returnValue(of({} as never) as never);

    const dialogRef = jasmine.createSpyObj<MatDialogRef<AccountSwitcherModalComponent>>(
      'MatDialogRef',
      ['close'],
    );

    TestBed.configureTestingModule({
      imports: [
        AccountSwitcherModalComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        { provide: AuthService, useValue: auth },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });

    const fixture = TestBed.createComponent(AccountSwitcherModalComponent);
    fixture.detectChanges();
    return { cmp: fixture.componentInstance, auth, dialogRef };
  }

  it('lists every tenant plus a Client Mode entry', () => {
    const { cmp } = setup(
      [
        { id: 1, slug: 'salon-a', name: 'Salon A' },
        { id: 2, slug: 'salon-b', name: 'Salon B' },
      ],
      1,
    );
    const items = (cmp as unknown as { items: () => unknown[] }).items();
    expect(items.length).toBe(3);
  });

  it('marks the active tenant in the list', () => {
    const { cmp } = setup(
      [{ id: 1, slug: 'salon-a', name: 'Salon A' }],
      1,
    );
    const items = (cmp as unknown as { items: () => Array<{ active: boolean; tenantId: number | null }> }).items();
    const active = items.find((i) => i.active);
    expect(active?.tenantId).toBe(1);
  });

  it('marks Client Mode active when activeTenantId is null', () => {
    const { cmp } = setup([{ id: 1, slug: 'salon-a', name: 'Salon A' }], null);
    const items = (cmp as unknown as { items: () => Array<{ active: boolean; tenantId: number | null }> }).items();
    const active = items.find((i) => i.active);
    expect(active?.tenantId).toBeNull();
  });
});
