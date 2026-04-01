# Booking Modal & Care Cards Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the oversized booking dialog (480px, custom calendar) with a compact mat-datepicker modal (360px), and redesign care cards to show a rectangle image on the left with description.

**Architecture:** The booking dialog drops its custom calendar grid in favor of Angular Material's `mat-datepicker` with a `dateFilter` that grays out closed days (fetched from the existing opening-hours API). Care cards switch from vertical stacking to a horizontal layout with image left. Backend adds `description` to the public care DTO.

**Tech Stack:** Angular 20, Angular Material (MatDatepicker, MatFormField), Tailwind CSS, Spring Boot 3.5

---

## File Structure

### Backend (modified files)
- `backend/src/main/java/com/prettyface/app/tenant/web/dto/PublicCareDto.java` — add `description` field
- `backend/src/main/java/com/prettyface/app/tenant/web/mapper/TenantMapper.java` — pass description in `toCareDto()`

### Frontend (modified files)
- `frontend/src/app/features/salon-profile/models/salon-profile.model.ts` — add `description` to `PublicCareDto`
- `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts` — replace calendar with mat-datepicker
- `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.html` — new compact template
- `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.scss` — new styles
- `frontend/src/app/pages/salon/salon-page.component.ts` — change dialog width, add fallback gradient helper
- `frontend/src/app/pages/salon/salon-page.component.html` — new care card markup
- `frontend/src/app/pages/salon/salon-page.component.scss` — new care card styles
- `frontend/public/i18n/fr.json` — add `booking.dialog.selectDate`
- `frontend/public/i18n/en.json` — add `booking.dialog.selectDate`

---

## Task 1: Backend — Add description to PublicCareDto

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/dto/PublicCareDto.java`
- Modify: `backend/src/main/java/com/prettyface/app/tenant/web/mapper/TenantMapper.java`

- [ ] **Step 1: Add description field to PublicCareDto**

Replace the record in `PublicCareDto.java` with:

```java
package com.prettyface.app.tenant.web.dto;

import java.util.List;

public record PublicCareDto(
        Long id,
        String name,
        String description,
        Integer duration,
        Integer price,
        List<String> imageUrls
) {}
```

- [ ] **Step 2: Update TenantMapper.toCareDto() to pass description**

In `TenantMapper.java`, find the `toCareDto` method (around line 63-71) and replace:

```java
return new PublicCareDto(care.getId(), care.getName(), care.getDuration(), care.getPrice(), imageUrls);
```

with:

```java
return new PublicCareDto(care.getId(), care.getName(), care.getDescription(), care.getDuration(), care.getPrice(), imageUrls);
```

- [ ] **Step 3: Verify backend compiles**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS (no output)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/prettyface/app/tenant/web/dto/PublicCareDto.java \
       backend/src/main/java/com/prettyface/app/tenant/web/mapper/TenantMapper.java
git commit -m "feat: add description to PublicCareDto for public salon page"
```

---

## Task 2: Frontend — Update model + i18n + add description to PublicCareDto

**Files:**
- Modify: `frontend/src/app/features/salon-profile/models/salon-profile.model.ts`
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Add description to frontend PublicCareDto**

In `salon-profile.model.ts`, find the `PublicCareDto` interface and add `description`:

```typescript
export interface PublicCareDto {
  id: number;
  name: string;
  description: string;
  duration: number;
  price: number;
  imageUrls: string[];
}
```

- [ ] **Step 2: Add i18n key for date picker label**

In `fr.json`, find the `"booking"` > `"dialog"` section and add:

```json
"selectDate": "Choisir une date"
```

In `en.json`, same location:

```json
"selectDate": "Select a date"
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/salon-profile/models/salon-profile.model.ts \
       frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat: add description to PublicCareDto model and booking date picker i18n"
```

---

## Task 3: Frontend — Rewrite booking dialog with mat-datepicker

**Files:**
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.ts`
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.html`
- Modify: `frontend/src/app/pages/salon/booking-dialog/booking-dialog.component.scss`
- Modify: `frontend/src/app/pages/salon/salon-page.component.ts` (dialog width only)

- [ ] **Step 1: Rewrite booking-dialog.component.ts**

Replace the entire file content with:

```typescript
import { Component, inject, signal, computed } from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { provideNativeDateAdapter } from '@angular/material/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { AvailabilityService } from '../../../features/availability/availability.service';
import { PublicCareDto, TimeSlot, ClientBookingRequest } from '../../../features/salon-profile/models/salon-profile.model';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalComponent, AuthModalResult } from '../../../shared/modals/auth-modal/auth-modal.component';

export interface BookingDialogData {
  slug: string;
  care: PublicCareDto;
}

@Component({
  selector: 'app-booking-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoPipe,
  ],
  providers: [provideNativeDateAdapter()],
  templateUrl: './booking-dialog.component.html',
  styleUrl: './booking-dialog.component.scss',
})
export class BookingDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<BookingDialogComponent>);
  private readonly data = inject<BookingDialogData>(MAT_DIALOG_DATA);
  private readonly salonService = inject(SalonProfileService);
  private readonly availabilityService = inject(AvailabilityService);
  private readonly authService = inject(AuthService);
  private readonly matDialog = inject(MatDialog);

  readonly care = this.data.care;
  readonly slug = this.data.slug;

  readonly minDate = new Date();
  readonly openDays = signal<Set<number>>(new Set());
  readonly selectedDate = signal<Date | null>(null);
  readonly slots = signal<TimeSlot[]>([]);
  readonly loadingSlots = signal(false);
  readonly selectedSlot = signal<TimeSlot | null>(null);
  readonly submitting = signal(false);
  readonly bookingSuccess = signal(false);
  readonly bookingError = signal<string | null>(null);
  readonly registerJustCompleted = signal(false);

  readonly dateFilter = (date: Date | null): boolean => {
    if (!date) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (date < today) return false;
    const dow = date.getDay() === 0 ? 7 : date.getDay();
    return this.openDays().has(dow);
  };

  constructor() {
    this.loadOpeningHours();
  }

  onDateChange(date: Date | null): void {
    this.selectedDate.set(date);
    this.selectedSlot.set(null);
    if (date) {
      this.loadSlots(date);
    }
  }

  selectSlot(slot: TimeSlot): void {
    this.selectedSlot.set(slot);
  }

  confirm(): void {
    const date = this.selectedDate();
    const slot = this.selectedSlot();
    if (!date || !slot) return;

    if (!this.authService.isAuthenticated()) {
      this.openAuthAndMaybeSubmit();
      return;
    }

    this.submitBooking();
  }

  close(): void {
    this.dialogRef.close();
  }

  formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' \u20AC';
  }

  private loadOpeningHours(): void {
    this.availabilityService.loadPublicHours(this.slug).subscribe({
      next: (hours) => {
        const days = new Set(hours.map((h) => h.dayOfWeek));
        this.openDays.set(days);
      },
      error: () => {
        // If we can't load hours, allow all days
        this.openDays.set(new Set([1, 2, 3, 4, 5, 6, 7]));
      },
    });
  }

  private loadSlots(date: Date): void {
    this.loadingSlots.set(true);
    this.slots.set([]);

    this.salonService.getAvailableSlots(this.slug, this.care.id, this.formatDate(date)).subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loadingSlots.set(false);
      },
      error: () => {
        this.slots.set([]);
        this.loadingSlots.set(false);
      },
    });
  }

  private openAuthAndMaybeSubmit(): void {
    const authRef = this.matDialog.open(AuthModalComponent, { width: '480px' });
    authRef.afterClosed().subscribe((result: AuthModalResult) => {
      if (!result?.authenticated) return;
      if (result.action === 'login') {
        this.submitBooking();
      } else {
        this.registerJustCompleted.set(true);
      }
    });
  }

  private submitBooking(): void {
    this.submitting.set(true);
    this.bookingError.set(null);
    this.registerJustCompleted.set(false);

    const request: ClientBookingRequest = {
      careId: this.care.id,
      appointmentDate: this.formatDate(this.selectedDate()!),
      appointmentTime: this.selectedSlot()!.startTime,
    };

    this.salonService.createBooking(this.slug, request).subscribe({
      next: () => {
        this.submitting.set(false);
        this.bookingSuccess.set(true);
      },
      error: (err) => {
        this.submitting.set(false);
        if (err.status === 409) {
          this.bookingError.set('booking.errors.slotTaken');
          this.loadSlots(this.selectedDate()!);
          this.selectedSlot.set(null);
        } else {
          this.bookingError.set('booking.errors.generic');
        }
      },
    });
  }

  private formatDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
```

- [ ] **Step 2: Rewrite booking-dialog.component.html**

Replace the entire file content with:

```html
<div class="booking-dialog">
  <button mat-icon-button class="close-btn" (click)="close()">
    <mat-icon>close</mat-icon>
  </button>

  @if (bookingSuccess()) {
    <!-- Success view -->
    <div class="success-view">
      <mat-icon class="success-icon">check_circle</mat-icon>
      <h2 class="success-title">{{ 'booking.confirm.success' | transloco }}</h2>
      <div class="success-details">
        <p class="success-care">{{ care.name }}</p>
        <p class="success-datetime">
          {{ selectedDate()!.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }) }}
          — {{ selectedSlot()!.startTime }}
        </p>
      </div>
      <button mat-flat-button class="confirm-btn" (click)="close()">
        {{ 'booking.confirm.close' | transloco }}
      </button>
    </div>
  } @else {
    <!-- Booking flow -->
    <h2 class="dialog-title">{{ 'booking.dialog.title' | transloco }}</h2>

    <!-- Care summary -->
    <div class="care-summary">
      <span class="care-name">{{ care.name }}</span>
      <span class="care-meta">{{ formatDuration(care.duration) }} · {{ formatPrice(care.price) }}</span>
    </div>

    <!-- Date picker -->
    <mat-form-field appearance="outline" class="date-field">
      <mat-label>{{ 'booking.dialog.selectDate' | transloco }}</mat-label>
      <input matInput [matDatepicker]="picker" [matDatepickerFilter]="dateFilter" [min]="minDate"
             [value]="selectedDate()" (dateChange)="onDateChange($event.value)" readonly>
      <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
      <mat-datepicker #picker></mat-datepicker>
    </mat-form-field>

    <!-- Slot selection -->
    @if (selectedDate()) {
      <div class="slots-section">
        @if (loadingSlots()) {
          <div class="slots-loading">
            <mat-spinner diameter="24"></mat-spinner>
          </div>
        } @else if (slots().length === 0) {
          <p class="no-slots">{{ 'booking.dialog.noSlots' | transloco }}</p>
        } @else {
          <div class="slots-grid">
            @for (slot of slots(); track slot.startTime) {
              <button
                type="button"
                class="slot-btn"
                [class.selected]="selectedSlot()?.startTime === slot.startTime"
                (click)="selectSlot(slot)"
              >
                {{ slot.startTime }}
              </button>
            }
          </div>
        }
      </div>
    }

    <!-- Error message -->
    @if (bookingError()) {
      <p class="booking-error">{{ bookingError()! | transloco }}</p>
    }

    <!-- Register success hint -->
    @if (registerJustCompleted()) {
      <p class="register-hint">{{ 'booking.confirm.registerDone' | transloco }}</p>
    }

    <!-- Confirm button -->
    @if (selectedSlot()) {
      <button
        mat-flat-button
        class="confirm-btn"
        [disabled]="submitting()"
        (click)="confirm()"
      >
        @if (submitting()) {
          <mat-spinner diameter="20"></mat-spinner>
        } @else {
          {{ 'booking.dialog.confirm' | transloco }} — {{ selectedSlot()!.startTime }}
        }
      </button>
    }
  }
</div>
```

- [ ] **Step 3: Rewrite booking-dialog.component.scss**

Replace the entire file content with:

```scss
.booking-dialog {
  padding: 20px;
  position: relative;
}

.close-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  color: #999;
}

.dialog-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin: 0 0 12px;
}

.care-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #faf8f6;
  padding: 10px 14px;
  border-radius: 10px;
  margin-bottom: 16px;
}

.care-name {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.care-meta {
  font-size: 11px;
  color: #999;
}

// Date picker
.date-field {
  width: 100%;
}

// Slots
.slots-section {
  margin-top: 4px;
}

.slots-loading {
  display: flex;
  justify-content: center;
  padding: 16px;
}

.no-slots {
  text-align: center;
  font-size: 13px;
  color: #bbb;
  font-style: italic;
  margin: 12px 0;
}

.slots-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.slot-btn {
  background: #f5f5f5;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 8px 0;
  font-size: 12px;
  color: #333;
  cursor: pointer;
  transition: all 150ms ease;
  text-align: center;

  &:hover {
    border-color: #c06;
    background: #fdf0f4;
  }

  &.selected {
    background: linear-gradient(135deg, #a8385d, #c06);
    color: white;
    border-color: transparent;
    font-weight: 500;
  }
}

.confirm-btn {
  display: block;
  width: 100%;
  margin-top: 16px;
  background: linear-gradient(135deg, #a8385d, #c06) !important;
  color: white !important;
  border-radius: 24px !important;
  padding: 10px 0 !important;
  font-weight: 500 !important;
  font-size: 13px;
}

// Success view
.success-view {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  font-size: 56px;
  width: 56px;
  height: 56px;
  color: #4caf50;
}

.success-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin: 12px 0 8px;
}

.success-details {
  margin: 12px 0 20px;
}

.success-care {
  font-size: 14px;
  font-weight: 500;
  color: #555;
}

.success-datetime {
  font-size: 12px;
  color: #888;
  margin-top: 4px;
}

.booking-error {
  color: #d32f2f;
  font-size: 12px;
  text-align: center;
  margin: 10px 0;
}

.register-hint {
  color: #4caf50;
  font-size: 12px;
  text-align: center;
  margin: 10px 0;
}
```

- [ ] **Step 4: Update dialog width in salon-page.component.ts**

In `salon-page.component.ts`, find the `openBookingDialog` method and change `width: '480px'` to `width: '360px'`:

```typescript
this.dialog.open(BookingDialogComponent, {
  width: '360px',
  disableClose: false,
  data: { slug, care } as BookingDialogData,
});
```

- [ ] **Step 5: Verify frontend compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/salon/booking-dialog/ \
       frontend/src/app/pages/salon/salon-page.component.ts
git commit -m "feat: replace booking dialog calendar with compact mat-datepicker"
```

---

## Task 4: Frontend — Redesign care cards with image left

**Files:**
- Modify: `frontend/src/app/pages/salon/salon-page.component.ts`
- Modify: `frontend/src/app/pages/salon/salon-page.component.html`
- Modify: `frontend/src/app/pages/salon/salon-page.component.scss`

- [ ] **Step 1: Add fallback gradient helper to salon-page.component.ts**

In `salon-page.component.ts`, add these members to the class after the existing `formatPrice` method:

```typescript
private readonly fallbackGradients = [
  'linear-gradient(135deg, #f3d5c0, #e8c4b0)',
  'linear-gradient(135deg, #d4b5d0, #c8a0c0)',
  'linear-gradient(135deg, #b5d4c0, #a0c8b0)',
  'linear-gradient(135deg, #c0d4f3, #b0c4e8)',
];

protected fallbackGradient(index: number): string {
  return this.fallbackGradients[index % this.fallbackGradients.length];
}
```

- [ ] **Step 2: Rewrite care card markup in salon-page.component.html**

Find the care card section inside the `@for (care of category.cares; track care.name)` loop and replace the entire `<div class="care-card">...</div>` block with:

```html
<div class="care-card">
  <div class="care-image-wrapper"
       [style.background]="care.imageUrls.length > 0 ? 'none' : fallbackGradient($index)">
    @if (care.imageUrls.length > 0) {
      <img [src]="care.imageUrls[0]" [alt]="care.name" class="care-image" />
    }
  </div>
  <div class="care-content">
    <div class="care-name">{{ care.name }}</div>
    @if (care.description) {
      <div class="care-description">{{ care.description }}</div>
    }
    <div class="care-bottom">
      <div class="care-meta">
        <span class="care-duration">{{ formatDuration(care.duration) }}</span>
        <span class="care-separator">·</span>
        <span class="care-price">{{ formatPrice(care.price) }}</span>
      </div>
      <button mat-flat-button class="book-btn" (click)="onBook(care)">
        {{ 'booking.book' | transloco }}
      </button>
    </div>
  </div>
</div>
```

- [ ] **Step 3: Replace care card styles in salon-page.component.scss**

Find and replace the `.care-list`, `.care-card`, `.care-images`, `.care-image`, `.care-info`, `.care-name`, `.care-details`, and `.book-btn` style blocks with:

```scss
.care-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.care-card {
  display: flex;
  border-radius: 12px;
  background: white;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  min-height: 120px;

  @media (max-width: 480px) {
    flex-direction: column;
  }
}

.care-image-wrapper {
  width: 130px;
  min-width: 130px;
  display: flex;
  align-items: center;
  justify-content: center;

  @media (max-width: 480px) {
    width: 100%;
    min-width: unset;
    height: 140px;
  }
}

.care-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.care-content {
  flex: 1;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
}

.care-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.care-description {
  font-size: 11px;
  color: #888;
  margin-top: 3px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.care-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 8px;
}

.care-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.care-duration {
  font-size: 11px;
  color: #888;
}

.care-separator {
  font-size: 11px;
  color: #888;
}

.care-price {
  font-size: 13px;
  font-weight: 600;
  color: #c06;
}

.book-btn {
  border-radius: 16px !important;
  font-size: 11px !important;
  padding: 4px 16px !important;
  background: linear-gradient(135deg, #a8385d, #c06) !important;
  color: white !important;
  line-height: 1.5 !important;
}
```

- [ ] **Step 4: Verify frontend compiles**

Run: `cd frontend && npx ng build --configuration=development 2>&1 | tail -10`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/salon/salon-page.component.ts \
       frontend/src/app/pages/salon/salon-page.component.html \
       frontend/src/app/pages/salon/salon-page.component.scss
git commit -m "feat: redesign care cards with image left and description"
```

---

## Task 5: Visual smoke test

- [ ] **Step 1: Start backend if not running**

Run: `cd backend && ./mvnw spring-boot:run`

- [ ] **Step 2: Verify salon API returns description**

Run: `curl -s http://localhost:8080/api/salon/sophie-martin | python3 -c "import sys,json; d=json.load(sys.stdin); c=d['categories'][0]['cares'][0]; print(c.get('name'), '|', c.get('description'))"`

Expected: care name and description printed (e.g., `Soin hydratant visage | Soin profond hydratant pour peaux sèches et déshydratées`)

- [ ] **Step 3: Open salon page in browser**

Open `http://localhost:4300/salon/sophie-martin` and verify:
- Care cards show image (or gradient fallback) on the left, name + description + price on the right
- Clicking "Réserver" opens a compact dialog (360px wide)
- Dialog shows date picker input, clicking opens Material calendar popup
- Closed days are greyed out in the calendar
- Selecting a date shows 4-column time slot grid
- Selecting a slot + clicking confirm completes the booking flow
