import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { AccessLevel } from '../../tracking.model';

@Component({
  selector: 'app-client-notes',
  standalone: true,
  imports: [FormsModule, DatePipe, MatButtonModule, MatIconModule, TranslocoPipe],
  template: `
    <div class="section">
      <div class="section-header">
        <span class="section-title">{{ 'tracking.practitionerNotes' | transloco }}</span>
        @if (accessLevel() === 'WRITE' && !editing()) {
          <button class="edit-btn" (click)="startEditing()">
            <mat-icon>edit</mat-icon>
          </button>
        }
      </div>
      <div class="notes-card">
        @if (editing()) {
          <textarea [(ngModel)]="editValue" rows="4" class="notes-textarea"></textarea>
          <div class="notes-actions">
            <button mat-button (click)="editing.set(false)">{{ 'common.cancel' | transloco }}</button>
            <button mat-flat-button class="save-btn" (click)="save()">{{ 'common.save' | transloco }}</button>
          </div>
        } @else {
          <div class="notes-text">{{ notes() || '—' }}</div>
        }
        @if (updatedByName()) {
          <div class="audit-line">{{ 'tracking.modifiedBy' | transloco }} {{ updatedByName() }} · {{ updatedAt() | date:'mediumDate' }}</div>
        }
      </div>
    </div>
  `,
  styles: `
    .section { margin-bottom: 12px; }
    .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .section-title { font-size: 13px; font-weight: 600; color: #333; }
    .edit-btn { background: none; border: none; cursor: pointer; color: #9ca3af; mat-icon { font-size: 18px; width: 18px; height: 18px; } }
    .notes-card { background: white; border-radius: 12px; padding: 14px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
    .notes-text { font-size: 13px; color: #4b5563; line-height: 1.5; }
    .notes-textarea { width: 100%; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; font-size: 13px; resize: vertical; font-family: inherit; }
    .notes-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
    .save-btn { background: #c06 !important; color: white !important; border-radius: 8px; }
    .audit-line { font-size: 10px; color: #9ca3af; margin-top: 8px; }
  `
})
export class ClientNotesComponent {
  notes = input<string | null>(null);
  updatedByName = input<string | null>(null);
  updatedAt = input<string | null>(null);
  accessLevel = input<AccessLevel>('WRITE');
  saveNotes = output<string>();

  editing = signal(false);
  editValue = '';

  startEditing() {
    this.editValue = this.notes() ?? '';
    this.editing.set(true);
  }

  save() {
    this.saveNotes.emit(this.editValue);
    this.editing.set(false);
  }
}
