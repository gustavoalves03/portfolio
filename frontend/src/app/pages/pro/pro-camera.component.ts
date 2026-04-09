import {
  Component,
  inject,
  viewChild,
  ElementRef,
  afterNextRender,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-pro-camera',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  template: `
    <div class="camera-page">
      <input
        #fileInput
        type="file"
        accept="image/*"
        capture="environment"
        class="hidden-input"
        (change)="onFileSelected($event)"
      />
      <div class="camera-fallback">
        <button type="button" class="take-photo-btn" (click)="openCamera()">
          <mat-icon>photo_camera</mat-icon>
          <span>{{ 'pro.camera.takePhoto' | transloco }}</span>
        </button>
      </div>
    </div>
  `,
  styles: `
    .camera-page {
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f4f2;
    }

    .hidden-input {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }

    .camera-fallback {
      text-align: center;
    }

    .take-photo-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 32px 48px;
      background: #fff;
      border: 2px dashed #ddd;
      border-radius: 16px;
      cursor: pointer;
      transition: border-color 150ms, box-shadow 150ms;

      &:hover {
        border-color: #c06;
        box-shadow: 0 2px 8px rgba(192, 0, 102, 0.1);
      }

      mat-icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        color: #c06;
      }

      span {
        font-size: 14px;
        font-weight: 500;
        color: #555;
      }
    }
  `,
})
export class ProCameraComponent {
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

  constructor() {
    afterNextRender(() => {
      // Auto-trigger file input to open camera on mobile
      setTimeout(() => this.openCamera(), 300);
    });
  }

  openCamera(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.fileInput().nativeElement.click();
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      // Navigate to posts page to create a post with the photo
      this.router.navigate(['/pro/posts']);
    } else {
      // User cancelled, go back
      this.router.navigate(['/pro/posts']);
    }
  }
}
