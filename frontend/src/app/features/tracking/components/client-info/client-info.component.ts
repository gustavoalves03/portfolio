import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { ClientProfileResponse, AccessLevel, UpdateProfileRequest } from '../../tracking.model';

@Component({
  selector: 'app-client-info',
  standalone: true,
  imports: [FormsModule, DatePipe, MatButtonModule, MatIconModule, TranslocoPipe],
  template: `
    <div class="section">
      <div class="section-header">
        <span class="section-title">{{ 'tracking.clientInfo' | transloco }}</span>
        @if (accessLevel() === 'WRITE' && !editing()) {
          <button class="edit-btn" (click)="startEditing()">
            <mat-icon>edit</mat-icon>
          </button>
        }
      </div>
      <div class="info-card">
        @if (editing()) {
          <div class="edit-grid">
            <div class="field">
              <label>{{ 'tracking.skinType' | transloco }}</label>
              <input [(ngModel)]="editSkinType" class="field-input" />
            </div>
            <div class="field">
              <label>{{ 'tracking.hairType' | transloco }}</label>
              <input [(ngModel)]="editHairType" class="field-input" />
            </div>
            <div class="field">
              <label>{{ 'tracking.allergiesAlert' | transloco }}</label>
              <input [(ngModel)]="editAllergies" class="field-input" />
            </div>
            <div class="field">
              <label>{{ 'tracking.preferences' | transloco }}</label>
              <input [(ngModel)]="editPreferences" class="field-input" />
            </div>
          </div>
          <div class="edit-actions">
            <button mat-button (click)="editing.set(false)">{{ 'common.cancel' | transloco }}</button>
            <button mat-flat-button class="save-btn" (click)="save()">{{ 'common.save' | transloco }}</button>
          </div>
        } @else {
          <div class="info-grid">
            <div class="info-item">
              <div class="info-label">{{ 'tracking.skinType' | transloco }}</div>
              <div class="info-value">{{ profile().skinType || '—' }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">{{ 'tracking.hairType' | transloco }}</div>
              <div class="info-value">{{ profile().hairType || '—' }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">{{ 'tracking.preferences' | transloco }}</div>
              <div class="info-value">{{ profile().preferences || '—' }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">{{ 'tracking.reminder' | transloco }}</div>
              <div class="info-value reminder">—</div>
            </div>
          </div>
        }
        @if (profile().updatedByName) {
          <div class="audit-line">{{ 'tracking.modifiedBy' | transloco }} {{ profile().updatedByName }} · {{ profile().updatedAt | date:'mediumDate' }}</div>
        }
      </div>
    </div>
  `,
  styles: `
    .section { margin-bottom: 12px; }
    .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .section-title { font-size: 13px; font-weight: 600; color: #333; }
    .edit-btn { background: none; border: none; cursor: pointer; color: #9ca3af; mat-icon { font-size: 18px; width: 18px; height: 18px; } }
    .info-card { background: white; border-radius: 12px; padding: 14px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
    .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .info-label { font-size: 10px; color: #9ca3af; text-transform: uppercase; }
    .info-value { font-size: 13px; color: #333; font-weight: 500; margin-top: 2px; }
    .info-value.reminder { color: #c06; }
    .edit-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .field label { font-size: 10px; color: #9ca3af; text-transform: uppercase; display: block; margin-bottom: 4px; }
    .field-input { width: 100%; border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px; font-size: 13px; font-family: inherit; }
    .edit-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
    .save-btn { background: #c06 !important; color: white !important; border-radius: 8px; }
    .audit-line { font-size: 10px; color: #9ca3af; margin-top: 8px; }
  `
})
export class ClientInfoComponent {
  profile = input.required<ClientProfileResponse>();
  accessLevel = input<AccessLevel>('WRITE');
  saveInfo = output<UpdateProfileRequest>();

  editing = signal(false);
  editSkinType = '';
  editHairType = '';
  editAllergies = '';
  editPreferences = '';

  startEditing() {
    const p = this.profile();
    this.editSkinType = p.skinType ?? '';
    this.editHairType = p.hairType ?? '';
    this.editAllergies = p.allergies ?? '';
    this.editPreferences = p.preferences ?? '';
    this.editing.set(true);
  }

  save() {
    this.saveInfo.emit({
      notes: null,
      skinType: this.editSkinType,
      hairType: this.editHairType,
      allergies: this.editAllergies,
      preferences: this.editPreferences,
    });
    this.editing.set(false);
  }
}
