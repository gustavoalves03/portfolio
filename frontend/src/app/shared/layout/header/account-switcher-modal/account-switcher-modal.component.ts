import { Component, computed, inject } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../../../core/auth/auth.service';

interface SwitcherItem {
  key: string;
  tenantId: number | null;
  name: string;
  initials: string;
  active: boolean;
}

@Component({
  selector: 'lp-account-switcher-modal',
  standalone: true,
  imports: [MatDialogModule, MatIconModule, TranslocoModule],
  templateUrl: './account-switcher-modal.component.html',
  styleUrl: './account-switcher-modal.component.scss',
})
export class AccountSwitcherModalComponent {
  private readonly auth = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<AccountSwitcherModalComponent>);
  private readonly snackbar = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);

  protected readonly items = computed<SwitcherItem[]>(() => {
    const activeId = this.auth.activeTenantId();
    const tenants = this.auth.availableTenants();
    const list: SwitcherItem[] = tenants.map((t) => ({
      key: `tenant-${t.id}`,
      tenantId: t.id,
      name: t.name || t.slug,
      initials: this.initials(t.name || t.slug),
      active: activeId === t.id,
    }));
    list.push({
      key: 'client',
      tenantId: null,
      name: this.i18n.translate('common.clientMode'),
      initials: '',
      active: activeId === null,
    });
    return list;
  });

  protected select(item: SwitcherItem): void {
    if (item.active) {
      this.dialogRef.close();
      return;
    }
    this.auth.switchTenant(item.tenantId).subscribe({
      next: () => {
        this.dialogRef.close();
        this.auth.navigateByRole();
      },
      error: () =>
        this.snackbar.open(
          this.i18n.translate('errors.tenantSwitchFailed'),
          undefined,
          { duration: 3000 },
        ),
    });
  }

  protected close(): void {
    this.dialogRef.close();
  }

  private initials(name: string): string {
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
}
