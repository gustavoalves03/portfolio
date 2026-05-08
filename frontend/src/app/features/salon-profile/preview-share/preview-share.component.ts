import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PreviewTokenService } from '../services/preview-token.service';
import { PreviewTokenResponse } from '../models/preview-token.model';

@Component({
  selector: 'app-preview-share',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe],
  templateUrl: './preview-share.component.html',
  styleUrl: './preview-share.component.scss',
})
export class PreviewShareComponent {
  private readonly tokenService = inject(PreviewTokenService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected readonly tokens = signal<PreviewTokenResponse[]>([]);
  protected readonly creating = signal(false);

  protected readonly activeTokens = computed(() => this.tokens().filter((t) => !t.revokedAt));

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.tokenService.list().subscribe({
      next: (tokens) => this.tokens.set(tokens),
    });
  }

  protected onGenerate(): void {
    if (this.creating()) return;
    this.creating.set(true);
    this.tokenService.create().subscribe({
      next: (token) => {
        this.tokens.update((current) => [token, ...current]);
        this.creating.set(false);
      },
      error: () => this.creating.set(false),
    });
  }

  protected onCopy(token: PreviewTokenResponse): void {
    const url = window.location.origin + token.shareUrl;
    navigator.clipboard.writeText(url).then(() => {
      this.snackBar.open(this.transloco.translate('salon.previewShare.copied'), undefined, {
        duration: 2000,
      });
    });
  }

  protected onRevoke(token: PreviewTokenResponse): void {
    const confirmed = window.confirm(this.transloco.translate('salon.previewShare.revokeConfirm'));
    if (!confirmed) return;
    this.tokenService.revoke(token.id).subscribe({
      next: () => {
        this.tokens.update((current) =>
          current.map((t) =>
            t.id === token.id ? { ...t, revokedAt: new Date().toISOString() } : t,
          ),
        );
        this.snackBar.open(this.transloco.translate('salon.previewShare.revoked'), undefined, {
          duration: 2000,
        });
      },
    });
  }

  protected formattedDate(iso: string): string {
    try {
      return new Date(iso).toLocaleDateString();
    } catch {
      return iso;
    }
  }
}
