import { Component, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../../../core/auth/auth.service';

@Component({
  selector: 'lp-tenant-switcher',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    MatDividerModule,
    TranslocoModule,
  ],
  templateUrl: './tenant-switcher.component.html',
  styleUrl: './tenant-switcher.component.scss',
})
export class TenantSwitcherComponent {
  private readonly auth = inject(AuthService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);

  protected readonly tenants = this.auth.availableTenants;
  protected readonly activeId = this.auth.activeTenantId;

  protected readonly visible = computed(
    () => this.auth.isAuthenticated() && this.tenants().length >= 1
  );

  protected readonly iconName = computed(() =>
    this.activeId() === null ? 'shopping_bag' : 'store'
  );

  protected readonly label = computed(() => {
    if (this.activeId() === null) {
      return this.i18n.translate('common.clientMode');
    }
    const t = this.tenants().find(t => t.id === this.activeId());
    return t?.name || t?.slug || '—';
  });

  protected switch(tenantId: number | null): void {
    if (tenantId === this.activeId()) {
      return;
    }
    this.auth.switchTenant(tenantId).subscribe({
      next: () => this.auth.navigateByRole(),
      error: () =>
        this.snackbar.open(
          this.i18n.translate('errors.tenantSwitchFailed'),
          undefined,
          { duration: 3000 }
        ),
    });
  }
}
