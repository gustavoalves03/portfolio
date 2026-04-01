# Booking Modal & Care Cards Redesign

## Context

The current booking dialog is too large (480px, full calendar + time slots stacked vertically, ~700px tall). The care cards on the salon page show images as horizontal scrolling thumbnails above the text, with no dedicated image space.

This spec covers two changes on the public salon page (`/salon/:slug`):
1. Replace the booking dialog with a compact date-picker-based modal
2. Redesign care cards to show a rectangle image on the left

## 1. Booking Dialog — Compact Date Picker + Slot Grid

### Layout

**Modal width:** 360px (down from 480px)

**Single screen with three sections:**

1. **Care summary** (top bar): care name + duration + price on one line, beige background `#faf8f6`, rounded
2. **Date input** with `mat-datepicker`: Material date picker input field. Clicking opens the native Material calendar popup. Label: i18n key `booking.dialog.selectDate`
   - `[min]`: today's date
   - `[dateFilter]`: disables days where the salon is closed (based on opening hours fetched at dialog init)
   - When a date is selected → fetch available slots from API
3. **Time slot grid** (4 columns): appears below the date input once a date is selected
   - Loading spinner while fetching
   - "No slots available" message if empty
   - Slots show `startTime` only (e.g., "09:30"), not the `endTime`
   - Selected slot: gradient background `linear-gradient(135deg, #a8385d, #c06)`, white text
4. **Confirm button**: full width, gradient background, disabled until date + slot chosen. Shows "Confirmer HH:mm"

**Success view:** unchanged (check icon + care name + date/time + close button).

### Data Flow

1. **Dialog opens** → fetch `GET /api/salon/{slug}/opening-hours` → extract open day-of-week numbers (1=Mon..7=Sun) → build `dateFilter` function
2. **User picks date** (via Material date picker popup) → fetch `GET /api/salon/{slug}/available-slots?careId=X&date=YYYY-MM-DD` → display slots in 4-column grid
3. **User picks slot** → enable confirm button
4. **Confirm** → same auth gating logic as before (open AuthModal if not logged in) → `POST /api/salon/{slug}/book`

### Opening Hours Model

Add to `salon-profile.model.ts`:
```typescript
export interface OpeningHourResponse {
  dayOfWeek: number; // 1=Monday ... 7=Sunday
  openTime: string;
  closeTime: string;
}
```

The `SalonProfileService` already calls opening-hours via the existing public endpoint. Add a method:
```typescript
getOpeningHours(slug: string): Observable<OpeningHourResponse[]>
```

### Date Filter Logic

```typescript
// openDays = Set of day-of-week numbers where salon has at least one opening hour
// e.g., {1, 2, 3, 4, 5, 6} for Mon-Sat
dateFilter = (date: Date | null): boolean => {
  if (!date) return false;
  const today = new Date(); today.setHours(0,0,0,0);
  if (date < today) return false;
  // JS getDay(): 0=Sun, convert to 1=Mon..7=Sun
  const dow = date.getDay() === 0 ? 7 : date.getDay();
  return this.openDays.has(dow);
};
```

### Removed

- Custom calendar grid (month navigation, 7×6 day grid, weekday headers) — all replaced by `mat-datepicker`
- The `calendarDays` computed signal, `weekDayLabels`, `prevMonth()`, `nextMonth()`, `isSelectedDate()` — no longer needed

### Files Changed

- Modify: `booking-dialog.component.ts` — replace calendar logic with `mat-datepicker` + opening hours fetch
- Modify: `booking-dialog.component.html` — replace calendar grid with `mat-datepicker-toggle` + `mat-datepicker` + 4-column slot grid
- Modify: `booking-dialog.component.scss` — remove calendar styles, update slot grid to 4 columns, adjust dialog size
- Modify: `salon-page.component.ts` — change dialog width from `480px` to `360px`
- Modify: `salon-profile.service.ts` — add `getOpeningHours()` method
- Modify: `salon-profile.model.ts` — add `OpeningHourResponse` interface

## 2. Care Cards — Rectangle Image Left

### Layout

Each care card becomes a horizontal flex container:

```
┌──────────┬─────────────────────────────┐
│          │  Manucure classique         │
│  IMAGE   │  Description courte...      │
│ 130×120  │                             │
│          │  30 min · 25 €    [Réserver]│
└──────────┴─────────────────────────────┘
```

**Image zone** (left):
- Width: 130px, min-height: 120px, `object-fit: cover`
- Border-radius on top-left and bottom-left corners only (`border-radius: 12px 0 0 12px`)
- If care has images → show first image (`care.imageUrls[0]`)
- If no image → gradient fallback background (soft pastel, based on index modulo for variety)

**Content zone** (right):
- Padding: 14px 16px
- Care name: 14px, font-weight 600, color `#333`
- Description: 11px, color `#888`, max 2 lines with `text-overflow: ellipsis` + `-webkit-line-clamp: 2`
- Bottom row (flex, space-between, align-items center):
  - Left: duration (11px, `#888`) + `·` separator + price (13px, font-weight 600, `#c06`)
  - Right: "Réserver" button — gradient fill (`linear-gradient(135deg, #a8385d, #c06)`), white text, border-radius 16px

**Responsive** (< 480px): image becomes full-width banner on top, content below (flex-direction: column).

### Data Requirement

The `PublicCareDto` currently has no `description` field. It needs to be added:
- Backend: add `description` to `PublicCareDto` record and `TenantMapper.toCareDto()`
- Frontend: add `description: string` to `PublicCareDto` interface

### Fallback Gradients

Array of soft gradients for cards without images, cycling by index:
```
['linear-gradient(135deg, #f3d5c0, #e8c4b0)',  // peach
 'linear-gradient(135deg, #d4b5d0, #c8a0c0)',  // lavender
 'linear-gradient(135deg, #b5d4c0, #a0c8b0)',  // mint
 'linear-gradient(135deg, #c0d4f3, #b0c4e8)']  // sky
```

### Files Changed

- Modify: `salon-page.component.html` — rewrite care card markup
- Modify: `salon-page.component.scss` — new card styles with image left layout + responsive
- Modify: `salon-page.component.ts` — add `fallbackGradient(index)` helper method, add `description` to template usage
- Backend modify: `PublicCareDto.java` — add `String description` field
- Backend modify: `TenantMapper.java` — pass `care.getDescription()` to `PublicCareDto`
- Frontend modify: `salon-profile.model.ts` — add `description: string` to `PublicCareDto`

## i18n

Add to both `fr.json` and `en.json`:
```
booking.dialog.selectDate → "Choisir une date" / "Select a date"
```

Existing keys reused: `booking.dialog.title`, `booking.dialog.noSlots`, `booking.dialog.confirm`, `booking.book`, `salon.public.duration`, `salon.public.price`.

## Scope Boundaries

**In scope:**
- Booking dialog: date picker + slot grid + opening hours filter
- Care cards: image left layout + description + gradient fallback
- Backend: add description to PublicCareDto

**Out of scope:**
- Booking dialog success view (unchanged)
- Auth gating flow (unchanged)
- Care image upload (already exists)
- Calendar blocked slots display (handled by API — only returns available slots)
