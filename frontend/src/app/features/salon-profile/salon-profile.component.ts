import { Component, inject, signal, effect, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ImageManager, ManagedImage } from '../../shared/uis/image-manager/image-manager.component';
import { SalonProfileStore } from './store/salon-profile.store';
import { UpdateTenantRequest } from './models/salon-profile.model';

@Component({
  selector: 'app-salon-profile',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
    ImageManager,
  ],
  providers: [SalonProfileStore],
  templateUrl: './salon-profile.component.html',
  styleUrl: './salon-profile.component.scss',
})
export class SalonProfileComponent {
  protected readonly store = inject(SalonProfileStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected name = signal('');
  protected description = signal('');
  protected logoImages = signal<ManagedImage[]>([]);
  protected slug = signal('');

  // Track if logo changed (to decide null vs base64 in request)
  private logoChanged = false;

  protected readonly descriptionTextLength = computed(() => {
    const text = this.description()?.replace(/<[^>]*>/g, '') ?? '';
    return text.length;
  });

  readonly MAX_DESCRIPTION_LENGTH = 10000;

  constructor() {
    // Sync store tenant to form fields
    effect(() => {
      const tenant = this.store.tenant();
      if (tenant) {
        this.name.set(tenant.name);
        this.slug.set(tenant.slug);
        this.description.set(tenant.description ?? '');
        if (tenant.logoUrl) {
          this.logoImages.set([{
            id: 'existing-logo',
            url: tenant.logoUrl,
            name: 'logo',
            order: 0,
          }]);
        } else {
          this.logoImages.set([]);
        }
        this.logoChanged = false;
      }
    });

    // Show snackbar on save success
    effect(() => {
      if (this.store.saveSuccess()) {
        this.snackBar.open(
          this.transloco.translate('pro.salon.saveSuccess'),
          undefined,
          { duration: 3000 }
        );
        this.store.clearStatus();
      }
    });

    // Show snackbar on error
    effect(() => {
      const error = this.store.error();
      if (error) {
        this.snackBar.open(
          this.transloco.translate('pro.salon.saveError'),
          undefined,
          { duration: 5000, panelClass: 'snackbar-error' }
        );
      }
    });
  }

  protected onLogoChange(images: ManagedImage[]): void {
    this.logoImages.set(images);
    this.logoChanged = true;
  }

  protected onSave(): void {
    if (!this.name().trim()) return;

    let logo: string | null = null;

    if (this.logoChanged) {
      const images = this.logoImages();
      if (images.length === 0) {
        logo = ''; // Remove logo
      } else if (images[0].base64Data || images[0].file) {
        // New image — use base64Data (set by ImageManager via FileReader)
        logo = images[0].url; // url contains the data URL from FileReader
      }
    }

    const request: UpdateTenantRequest = {
      name: this.name().trim(),
      description: this.description() || null,
      logo,
    };

    this.store.updateProfile(request);
  }
}
