import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { VisitRecordResponse } from '../../tracking.model';

@Component({
  selector: 'app-visit-card',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="visit-card">
      <div class="visit-top">
        <span class="care-name">{{ visit().careName }}</span>
      </div>
      <div class="visit-meta">{{ visit().visitDate | date:'mediumDate' }} · {{ visit().updatedByName ?? '' }}</div>
      @if (visit().practitionerNotes) {
        <div class="visit-notes">"{{ visit().practitionerNotes }}"</div>
      }
      @if (visit().photos.length > 0) {
        <div class="visit-photos">
          @for (photo of visit().photos; track photo.id) {
            <img [src]="apiBaseUrl() + photo.imageUrl" [alt]="photo.photoType" class="photo-thumb" />
          }
        </div>
      }
    </div>
  `,
  styles: `
    .visit-card {
      background: white;
      border-radius: 12px;
      padding: 14px;
      box-shadow: 0 1px 4px rgba(0,0,0,0.06);
    }
    .visit-top { display: flex; justify-content: space-between; align-items: center; }
    .care-name { font-size: 14px; font-weight: 600; color: #1a1a2e; }
    .visit-meta { font-size: 12px; color: #6b7280; margin-top: 4px; }
    .visit-notes { font-size: 12px; color: #4b5563; margin-top: 6px; font-style: italic; }
    .visit-photos { display: flex; gap: 6px; margin-top: 8px; }
    .photo-thumb { width: 48px; height: 48px; border-radius: 8px; object-fit: cover; background: #f3f4f6; }
  `
})
export class VisitCardComponent {
  visit = input.required<VisitRecordResponse>();
  apiBaseUrl = input<string>('');
}
