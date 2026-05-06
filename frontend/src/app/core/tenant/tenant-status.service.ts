import { Injectable, signal } from '@angular/core';

export type TenantStatusValue = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';

/**
 * Lightweight root-scoped store for the current pro's tenant status.
 *
 * Fed by the ProShellComponent (which holds the heavy DashboardStore) via
 * an effect. Consumed by the global SidenavMenu which sits outside the
 * /pro/* route subtree and therefore can't access the dashboard store
 * directly. Stays null until a pro session populates it; logout calls reset.
 */
@Injectable({ providedIn: 'root' })
export class TenantStatusService {
  readonly status = signal<TenantStatusValue | null>(null);

  set(value: TenantStatusValue | null): void {
    this.status.set(value);
  }

  reset(): void {
    this.status.set(null);
  }
}
