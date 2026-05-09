# Refonte /pro/availability — Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer la grille 7 colonnes actuelle de `/pro/availability` par une timeline horizontale (PC ≥ 768px) avec édition par popover snap 30 min, et une liste verticale empilée (mobile < 768px). Toggle ouvert/fermé par jour, presets, "copier vers".

**Architecture:** Le composant existant `AvailabilityComponent` reste le **conteneur de page** (état + KPIs + savebar). Trois nouveaux composants enfants : `AvailabilityTimelineComponent` (vue PC), `AvailabilityDayListComponent` (vue mobile), `SlotPopoverComponent` (édition partagée). Le conteneur switche entre les deux vues via `isDesktopSignal()` existant. Le popover utilise CDK Overlay sur PC et MatDialog sur mobile.

**Tech Stack:** Angular 20 standalone components, signals, NgRx SignalStore (existant), `@angular/cdk/overlay`, `@angular/material/dialog`, Transloco i18n (fr/en), Karma/Jasmine pour les tests.

**Référence design :** `docs/superpowers/specs/2026-05-09-availability-timeline-redesign-design.md`

**Référence visuelle :** `.superpowers/brainstorm/21957-1778353765/content/05-availability-final-design.html`

---

## Pré-requis

Avant de démarrer, vérifier que :
- Tu es sur une branche dédiée (idéalement créée via worktree). Si non : `git checkout -b feat/availability-timeline-redesign`.
- Le container `web-dev` tourne (`docker ps | grep web-dev`). Sinon : `docker compose --profile dev up -d frontend-dev`.
- La page `/pro/availability` répond avant tout changement.
- Connaître les fichiers existants à NE PAS toucher :
  - `availability.store.ts` — store NgRx, intact.
  - `availability.service.ts` — service HTTP, intact.
  - `availability.model.ts` — interfaces existantes (`TimeSlot`, `DaySlots`, `OpeningHourRequest`, `OpeningHourResponse`), enrichies en Task 1.

## File Structure (cible finale)

```
frontend/src/app/features/availability/
├── availability.component.ts           ← conteneur, refactoré (Task 9)
├── availability.component.html         ← réécrit (Task 9)
├── availability.component.scss         ← réécrit (Task 9)
├── availability.component.spec.ts      ← adapté (Task 9)
├── availability.store.ts               ← INCHANGÉ
├── availability.service.ts             ← INCHANGÉ
├── availability.model.ts               ← + types (Task 1)
│
├── presets/                            ← nouveau (Task 2)
│   ├── week-presets.ts
│   └── week-presets.spec.ts
│
├── time-utils.ts                       ← nouveau (Task 3)
├── time-utils.spec.ts
│
├── slot-popover/                       ← nouveau (Tasks 4-5)
│   ├── slot-popover.component.ts
│   ├── slot-popover.component.html
│   ├── slot-popover.component.scss
│   └── slot-popover.component.spec.ts
│
├── timeline/                           ← nouveau (Tasks 6-7)
│   ├── availability-timeline.component.ts
│   ├── availability-timeline.component.html
│   ├── availability-timeline.component.scss
│   └── availability-timeline.component.spec.ts
│
└── day-list/                           ← nouveau (Task 8)
    ├── availability-day-list.component.ts
    ├── availability-day-list.component.html
    ├── availability-day-list.component.scss
    └── availability-day-list.component.spec.ts
```

i18n :
- `frontend/public/i18n/fr.json` — bloc `pro.availability` enrichi (Task 9)
- `frontend/public/i18n/en.json` — idem (Task 9)

---

## Task 1: Étendre le modèle de données

**Files:**
- Modify: `frontend/src/app/features/availability/availability.model.ts`

- [ ] **Step 1: Ajouter le type DayOfWeek + WeekSlots + alias commit-friendly**

Édition du fichier. Remplacer le contenu existant par :

```ts
export interface TimeSlot {
  openTime: string; // "09:00"
  closeTime: string; // "18:00"
}

export interface DaySlots {
  dayOfWeek: number; // 1-7
  slots: TimeSlot[];
}

export interface OpeningHourRequest {
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}

export interface OpeningHourResponse {
  id: number;
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}

/** Day of week, 1 = Monday → 7 = Sunday. */
export type DayOfWeek = 1 | 2 | 3 | 4 | 5 | 6 | 7;

/** Whole-week opening slots — array of 7 entries, ordered by dayOfWeek 1→7. */
export type WeekSlots = DaySlots[];
```

- [ ] **Step 2: Vérifier que les tests existants compilent toujours**

Run: `cd frontend && npx --no-install tsc --noEmit -p tsconfig.app.json 2>&1 | grep -E "(error|availability)"`

Expected: aucune erreur. Si la compilation est OK, aucune ligne ne sort.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/availability/availability.model.ts
git commit -m "feat(availability): add DayOfWeek and WeekSlots type aliases"
```

---

## Task 2: Helpers `time-utils.ts` (snap 30 min, format, conversions)

**Files:**
- Create: `frontend/src/app/features/availability/time-utils.ts`
- Create: `frontend/src/app/features/availability/time-utils.spec.ts`

- [ ] **Step 1: Écrire les tests d'abord**

Créer `frontend/src/app/features/availability/time-utils.spec.ts` :

```ts
import {
  HHMM_OPTIONS,
  hhmmToMinutes,
  minutesToHhmm,
  snapTo30,
  positionInRail,
  slotsOverlap,
} from './time-utils';

describe('time-utils', () => {
  describe('HHMM_OPTIONS', () => {
    it('lists all 30-minute steps from 06:00 to 22:00 (33 entries)', () => {
      expect(HHMM_OPTIONS.length).toBe(33);
      expect(HHMM_OPTIONS[0]).toBe('06:00');
      expect(HHMM_OPTIONS[1]).toBe('06:30');
      expect(HHMM_OPTIONS[HHMM_OPTIONS.length - 1]).toBe('22:00');
    });
  });

  describe('hhmmToMinutes', () => {
    it('converts "09:30" to 570', () => {
      expect(hhmmToMinutes('09:30')).toBe(570);
    });
    it('converts "06:00" to 360', () => {
      expect(hhmmToMinutes('06:00')).toBe(360);
    });
    it('returns 0 on malformed input', () => {
      expect(hhmmToMinutes('')).toBe(0);
      expect(hhmmToMinutes('abc')).toBe(0);
    });
  });

  describe('minutesToHhmm', () => {
    it('converts 570 to "09:30"', () => {
      expect(minutesToHhmm(570)).toBe('09:30');
    });
    it('pads single digits', () => {
      expect(minutesToHhmm(60)).toBe('01:00');
    });
  });

  describe('snapTo30', () => {
    it('rounds 09:17 to 09:30', () => {
      expect(snapTo30('09:17')).toBe('09:30');
    });
    it('keeps 09:00 unchanged', () => {
      expect(snapTo30('09:00')).toBe('09:00');
    });
    it('keeps 09:30 unchanged', () => {
      expect(snapTo30('09:30')).toBe('09:30');
    });
    it('rounds 09:45 up to 10:00', () => {
      expect(snapTo30('09:45')).toBe('10:00');
    });
  });

  describe('positionInRail', () => {
    // Rail = 6h → 22h = 16 hours = 960 minutes
    it('positions 09:00 at 18.75% from left', () => {
      expect(positionInRail('09:00')).toBeCloseTo(18.75, 2);
    });
    it('positions 06:00 at 0%', () => {
      expect(positionInRail('06:00')).toBe(0);
    });
    it('clamps 04:00 to 0%', () => {
      expect(positionInRail('04:00')).toBe(0);
    });
    it('clamps 23:00 to 100%', () => {
      expect(positionInRail('23:00')).toBe(100);
    });
  });

  describe('slotsOverlap', () => {
    it('detects overlapping slots', () => {
      expect(slotsOverlap(
        { openTime: '09:00', closeTime: '12:00' },
        { openTime: '11:00', closeTime: '14:00' },
      )).toBe(true);
    });
    it('detects non-overlapping slots (touching is OK)', () => {
      expect(slotsOverlap(
        { openTime: '09:00', closeTime: '12:00' },
        { openTime: '12:00', closeTime: '14:00' },
      )).toBe(false);
    });
    it('detects non-overlapping slots (gap)', () => {
      expect(slotsOverlap(
        { openTime: '09:00', closeTime: '12:00' },
        { openTime: '14:00', closeTime: '18:00' },
      )).toBe(false);
    });
  });
});
```

- [ ] **Step 2: Lancer les tests pour confirmer qu'ils échouent**

Run: `cd frontend && npx --no-install ng test --include='**/time-utils.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -20`

Expected: les imports de `./time-utils` échouent (le fichier n'existe pas).

- [ ] **Step 3: Implémenter `time-utils.ts`**

Créer `frontend/src/app/features/availability/time-utils.ts` :

```ts
import { TimeSlot } from './availability.model';

/** Rail boundaries (en minutes depuis minuit). 6h = 360, 22h = 1320. */
export const RAIL_START_MIN = 360;
export const RAIL_END_MIN = 1320;
const RAIL_SPAN = RAIL_END_MIN - RAIL_START_MIN; // 960

/** Liste des heures HH:MM disponibles (snap 30 min) entre 06:00 et 22:00 inclus. */
export const HHMM_OPTIONS: string[] = (() => {
  const out: string[] = [];
  for (let m = RAIL_START_MIN; m <= RAIL_END_MIN; m += 30) {
    out.push(minutesToHhmm(m));
  }
  return out;
})();

/** "09:30" → 570 minutes since midnight. Returns 0 on malformed input. */
export function hhmmToMinutes(value: string): number {
  if (!value || !value.includes(':')) return 0;
  const [h, m] = value.split(':');
  const hn = Number(h);
  const mn = Number(m);
  if (!Number.isFinite(hn) || !Number.isFinite(mn)) return 0;
  return hn * 60 + mn;
}

/** 570 → "09:30". */
export function minutesToHhmm(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/** Rounds an HH:MM value to the nearest 30-min step. */
export function snapTo30(value: string): string {
  const total = hhmmToMinutes(value);
  const snapped = Math.round(total / 30) * 30;
  return minutesToHhmm(snapped);
}

/**
 * Returns the % position of a time on the 6h-22h rail.
 * 06:00 → 0, 22:00 → 100, values outside are clamped.
 */
export function positionInRail(time: string): number {
  const min = hhmmToMinutes(time);
  if (min <= RAIL_START_MIN) return 0;
  if (min >= RAIL_END_MIN) return 100;
  return ((min - RAIL_START_MIN) / RAIL_SPAN) * 100;
}

/**
 * Checks if two slots overlap. Touching at the boundary is NOT an overlap.
 * 09-12 + 12-14 = no overlap. 09-12 + 11-14 = overlap.
 */
export function slotsOverlap(a: TimeSlot, b: TimeSlot): boolean {
  const aStart = hhmmToMinutes(a.openTime);
  const aEnd = hhmmToMinutes(a.closeTime);
  const bStart = hhmmToMinutes(b.openTime);
  const bEnd = hhmmToMinutes(b.closeTime);
  return aStart < bEnd && bStart < aEnd;
}
```

- [ ] **Step 4: Lancer les tests, ils doivent passer**

Run: `cd frontend && npx --no-install ng test --include='**/time-utils.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`

Expected: `Executed N of N SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/availability/time-utils.ts frontend/src/app/features/availability/time-utils.spec.ts
git commit -m "feat(availability): time helpers (snap30, position, overlap)"
```

---

## Task 3: Week presets

**Files:**
- Create: `frontend/src/app/features/availability/presets/week-presets.ts`
- Create: `frontend/src/app/features/availability/presets/week-presets.spec.ts`

- [ ] **Step 1: Écrire les tests**

Créer `frontend/src/app/features/availability/presets/week-presets.spec.ts` :

```ts
import { WEEK_PRESETS, applyPreset } from './week-presets';
import { WeekSlots } from '../availability.model';

const emptyWeek = (): WeekSlots =>
  [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, slots: [] }));

describe('week-presets', () => {
  it('exposes 3 named presets', () => {
    expect(WEEK_PRESETS.map((p) => p.key)).toEqual([
      'fullWeek-9-18',
      'midDayBreak',
      'closeAll',
    ]);
  });

  describe('fullWeek-9-18', () => {
    it('opens Mon-Fri 9-18 and closes weekend', () => {
      const out = applyPreset('fullWeek-9-18', emptyWeek());
      expect(out.find((d) => d.dayOfWeek === 1)!.slots).toEqual([
        { openTime: '09:00', closeTime: '18:00' },
      ]);
      expect(out.find((d) => d.dayOfWeek === 5)!.slots.length).toBe(1);
      expect(out.find((d) => d.dayOfWeek === 6)!.slots).toEqual([]);
      expect(out.find((d) => d.dayOfWeek === 7)!.slots).toEqual([]);
    });
  });

  describe('midDayBreak', () => {
    it('opens Mon-Fri 9-13 + 14-18 and closes weekend', () => {
      const out = applyPreset('midDayBreak', emptyWeek());
      expect(out.find((d) => d.dayOfWeek === 1)!.slots).toEqual([
        { openTime: '09:00', closeTime: '13:00' },
        { openTime: '14:00', closeTime: '18:00' },
      ]);
      expect(out.find((d) => d.dayOfWeek === 6)!.slots).toEqual([]);
    });
  });

  describe('closeAll', () => {
    it('clears every slot', () => {
      const week = emptyWeek();
      week[0].slots = [{ openTime: '09:00', closeTime: '12:00' }];
      const out = applyPreset('closeAll', week);
      expect(out.every((d) => d.slots.length === 0)).toBe(true);
    });
  });

  it('returns a new array (does not mutate input)', () => {
    const input = emptyWeek();
    const out = applyPreset('fullWeek-9-18', input);
    expect(out).not.toBe(input);
    expect(input[0].slots.length).toBe(0);
  });
});
```

- [ ] **Step 2: Lancer les tests, vérifier qu'ils échouent**

Run: `cd frontend && npx --no-install ng test --include='**/week-presets.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -8`

Expected: import errors (`./week-presets`).

- [ ] **Step 3: Implémenter**

Créer `frontend/src/app/features/availability/presets/week-presets.ts` :

```ts
import { WeekSlots } from '../availability.model';

export type WeekPresetKey = 'fullWeek-9-18' | 'midDayBreak' | 'closeAll';

export interface WeekPreset {
  key: WeekPresetKey;
  /** i18n key for the toolbar button label. */
  labelKey: string;
}

export const WEEK_PRESETS: WeekPreset[] = [
  { key: 'fullWeek-9-18', labelKey: 'pro.availability.preset.fullWeek' },
  { key: 'midDayBreak', labelKey: 'pro.availability.preset.midDayBreak' },
  { key: 'closeAll', labelKey: 'pro.availability.preset.closeAll' },
];

export function applyPreset(key: WeekPresetKey, _current: WeekSlots): WeekSlots {
  switch (key) {
    case 'fullWeek-9-18':
      return [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({
        dayOfWeek,
        slots: dayOfWeek <= 5 ? [{ openTime: '09:00', closeTime: '18:00' }] : [],
      }));
    case 'midDayBreak':
      return [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({
        dayOfWeek,
        slots:
          dayOfWeek <= 5
            ? [
                { openTime: '09:00', closeTime: '13:00' },
                { openTime: '14:00', closeTime: '18:00' },
              ]
            : [],
      }));
    case 'closeAll':
      return [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, slots: [] }));
  }
}
```

- [ ] **Step 4: Tests passent**

Run: `cd frontend && npx --no-install ng test --include='**/week-presets.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -8`

Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/features/availability/presets/
git commit -m "feat(availability): week presets (fullWeek/midDayBreak/closeAll)"
```

---

## Task 4: Slot popover — composant + tests

**Files:**
- Create: `frontend/src/app/features/availability/slot-popover/slot-popover.component.ts`
- Create: `frontend/src/app/features/availability/slot-popover/slot-popover.component.html`
- Create: `frontend/src/app/features/availability/slot-popover/slot-popover.component.scss`
- Create: `frontend/src/app/features/availability/slot-popover/slot-popover.component.spec.ts`

- [ ] **Step 1: Écrire les tests**

Créer `frontend/src/app/features/availability/slot-popover/slot-popover.component.spec.ts` :

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SlotPopoverComponent, SlotPopoverData, SlotPopoverResult } from './slot-popover.component';

const translations = {
  'pro.availability.popover.title.create': 'New slot · {{day}}',
  'pro.availability.popover.title.edit': 'Edit · {{day}}',
  'pro.availability.popover.startLabel': 'Start',
  'pro.availability.popover.endLabel': 'End',
  'pro.availability.popover.copyTo': 'Copy to:',
  'pro.availability.popover.copyAll': 'All week',
  'pro.availability.popover.delete': 'Delete',
  'pro.availability.popover.cancel': 'Cancel',
  'pro.availability.popover.confirm': 'Confirm',
  'pro.availability.invalidOverlap': 'This slot overlaps another',
  'pro.availability.days.1': 'Monday',
  'pro.availability.days.2': 'Tuesday',
  'pro.availability.days.3': 'Wednesday',
  'pro.availability.days.4': 'Thursday',
  'pro.availability.days.5': 'Friday',
  'pro.availability.days.6': 'Saturday',
  'pro.availability.days.7': 'Sunday',
};

function setup(data: SlotPopoverData): ComponentFixture<SlotPopoverComponent> {
  TestBed.configureTestingModule({
    imports: [
      SlotPopoverComponent,
      TranslocoTestingModule.forRoot({
        langs: { en: translations },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      }),
    ],
    providers: [provideZonelessChangeDetection()],
  });
  const fixture = TestBed.createComponent(SlotPopoverComponent);
  fixture.componentRef.setInput('data', data);
  fixture.detectChanges();
  return fixture;
}

describe('SlotPopoverComponent', () => {
  it('pre-fills selects from data in create mode', () => {
    const fixture = setup({
      mode: 'create',
      dayOfWeek: 2,
      initialStart: '09:00',
      initialEnd: '12:00',
      otherDays: [1, 3, 4, 5, 6, 7],
      existingSlotsForDay: [],
    });
    const cmp = fixture.componentInstance;
    expect(cmp.start()).toBe('09:00');
    expect(cmp.end()).toBe('12:00');
    expect(cmp.canConfirm()).toBe(true);
  });

  it('disables confirm when start >= end', () => {
    const fixture = setup({
      mode: 'create',
      dayOfWeek: 1,
      initialStart: '12:00',
      initialEnd: '09:00',
      otherDays: [],
      existingSlotsForDay: [],
    });
    expect(fixture.componentInstance.canConfirm()).toBe(false);
  });

  it('detects overlap with existing slots', () => {
    const fixture = setup({
      mode: 'create',
      dayOfWeek: 1,
      initialStart: '11:00',
      initialEnd: '14:00',
      otherDays: [],
      existingSlotsForDay: [{ openTime: '09:00', closeTime: '12:00' }],
    });
    expect(fixture.componentInstance.hasOverlap()).toBe(true);
    expect(fixture.componentInstance.canConfirm()).toBe(false);
  });

  it('emits confirm with the selected times', () => {
    const fixture = setup({
      mode: 'create',
      dayOfWeek: 1,
      initialStart: '09:00',
      initialEnd: '12:00',
      otherDays: [],
      existingSlotsForDay: [],
    });
    let result: SlotPopoverResult | undefined;
    fixture.componentInstance.confirm.subscribe((r) => (result = r));
    fixture.componentInstance.onConfirm();
    expect(result).toEqual({
      action: 'save',
      start: '09:00',
      end: '12:00',
      copyToDays: [],
    });
  });

  it('toggles a day in copyToDays', () => {
    const fixture = setup({
      mode: 'edit',
      dayOfWeek: 1,
      initialStart: '09:00',
      initialEnd: '12:00',
      otherDays: [2, 3, 4, 5],
      existingSlotsForDay: [],
    });
    const cmp = fixture.componentInstance;
    cmp.toggleCopyDay(2);
    cmp.toggleCopyDay(3);
    expect(cmp.copyToDays()).toEqual([2, 3]);
    cmp.toggleCopyDay(2);
    expect(cmp.copyToDays()).toEqual([3]);
  });

  it('emits delete in edit mode only', () => {
    const fixture = setup({
      mode: 'edit',
      dayOfWeek: 1,
      initialStart: '09:00',
      initialEnd: '12:00',
      otherDays: [],
      existingSlotsForDay: [],
    });
    let deleted = false;
    fixture.componentInstance.confirm.subscribe((r) => {
      if (r.action === 'delete') deleted = true;
    });
    fixture.componentInstance.onDelete();
    expect(deleted).toBe(true);
  });
});
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

Run: `cd frontend && npx --no-install ng test --include='**/slot-popover.component.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`

Expected: import errors.

- [ ] **Step 3: Implémenter le composant TS**

Créer `frontend/src/app/features/availability/slot-popover/slot-popover.component.ts` :

```ts
import { Component, EventEmitter, Output, computed, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { DayOfWeek, TimeSlot } from '../availability.model';
import { HHMM_OPTIONS, hhmmToMinutes, slotsOverlap, snapTo30 } from '../time-utils';

export interface SlotPopoverData {
  mode: 'create' | 'edit';
  dayOfWeek: DayOfWeek;
  initialStart: string; // "09:00"
  initialEnd: string;   // "18:00"
  /** Days the user can copy this slot to. Excludes dayOfWeek. */
  otherDays: DayOfWeek[];
  /** Other slots of the same day, used to detect overlap (excludes the slot being edited). */
  existingSlotsForDay: TimeSlot[];
}

export type SlotPopoverResult =
  | { action: 'save'; start: string; end: string; copyToDays: DayOfWeek[] }
  | { action: 'delete' }
  | { action: 'cancel' };

@Component({
  selector: 'app-slot-popover',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoPipe],
  templateUrl: './slot-popover.component.html',
  styleUrl: './slot-popover.component.scss',
})
export class SlotPopoverComponent {
  readonly data = input.required<SlotPopoverData>();
  @Output() readonly confirm = new EventEmitter<SlotPopoverResult>();

  readonly options = HHMM_OPTIONS;

  readonly start = signal<string>('09:00');
  readonly end = signal<string>('18:00');
  readonly copyToDays = signal<DayOfWeek[]>([]);

  constructor() {
    // Sync from input on first change
    queueMicrotask(() => {
      const d = this.data();
      this.start.set(snapTo30(d.initialStart));
      this.end.set(snapTo30(d.initialEnd));
      this.copyToDays.set([]);
    });
  }

  readonly hasOverlap = computed(() => {
    const candidate: TimeSlot = { openTime: this.start(), closeTime: this.end() };
    return this.data().existingSlotsForDay.some((s) => slotsOverlap(candidate, s));
  });

  readonly canConfirm = computed(() => {
    const startMin = hhmmToMinutes(this.start());
    const endMin = hhmmToMinutes(this.end());
    if (startMin >= endMin) return false;
    if (this.hasOverlap()) return false;
    return true;
  });

  toggleCopyDay(day: DayOfWeek): void {
    this.copyToDays.update((cur) =>
      cur.includes(day) ? cur.filter((d) => d !== day) : [...cur, day],
    );
  }

  toggleAllDays(): void {
    const all = this.data().otherDays;
    const current = this.copyToDays();
    if (current.length === all.length) {
      this.copyToDays.set([]);
    } else {
      this.copyToDays.set([...all]);
    }
  }

  onConfirm(): void {
    if (!this.canConfirm()) return;
    this.confirm.emit({
      action: 'save',
      start: this.start(),
      end: this.end(),
      copyToDays: this.copyToDays(),
    });
  }

  onDelete(): void {
    this.confirm.emit({ action: 'delete' });
  }

  onCancel(): void {
    this.confirm.emit({ action: 'cancel' });
  }
}
```

- [ ] **Step 4: Implémenter le template HTML**

Créer `frontend/src/app/features/availability/slot-popover/slot-popover.component.html` :

```html
<div class="popover">
  <h4 class="popover-title">
    @if (data().mode === 'create') {
      {{ 'pro.availability.popover.title.create' | transloco: { day: ('pro.availability.days.' + data().dayOfWeek) | transloco } }}
    } @else {
      {{ 'pro.availability.popover.title.edit' | transloco: { day: ('pro.availability.days.' + data().dayOfWeek) | transloco } }}
    }
  </h4>

  <div class="row">
    <span class="label">{{ 'pro.availability.popover.startLabel' | transloco }}</span>
    <select [ngModel]="start()" (ngModelChange)="start.set($event)">
      @for (opt of options; track opt) {
        <option [value]="opt">{{ opt }}</option>
      }
    </select>
  </div>

  <div class="row">
    <span class="label">{{ 'pro.availability.popover.endLabel' | transloco }}</span>
    <select [ngModel]="end()" (ngModelChange)="end.set($event)">
      @for (opt of options; track opt) {
        <option [value]="opt">{{ opt }}</option>
      }
    </select>
  </div>

  @if (hasOverlap()) {
    <p class="error">{{ 'pro.availability.invalidOverlap' | transloco }}</p>
  }

  @if (data().otherDays.length > 0) {
    <div class="copy-row">
      <span class="copy-label">{{ 'pro.availability.popover.copyTo' | transloco }}</span>
      <div class="copy-days">
        @for (day of data().otherDays; track day) {
          <button
            type="button"
            class="copy-day"
            [class.active]="copyToDays().includes(day)"
            (click)="toggleCopyDay(day)"
          >
            {{ ('pro.availability.days.' + day) | transloco | slice: 0:3 }}
          </button>
        }
        <button type="button" class="copy-all" (click)="toggleAllDays()">
          {{ 'pro.availability.popover.copyAll' | transloco }}
        </button>
      </div>
    </div>
  }

  <div class="actions">
    @if (data().mode === 'edit') {
      <button type="button" class="delete-link" (click)="onDelete()">
        🗑 {{ 'pro.availability.popover.delete' | transloco }}
      </button>
    } @else {
      <span></span>
    }
    <div class="actions-right">
      <button type="button" class="cancel" (click)="onCancel()">
        {{ 'pro.availability.popover.cancel' | transloco }}
      </button>
      <button type="button" class="confirm" [disabled]="!canConfirm()" (click)="onConfirm()">
        {{ 'pro.availability.popover.confirm' | transloco }}
      </button>
    </div>
  </div>
</div>
```

Le `| slice: 0:3` requiert le `SlicePipe` — `CommonModule` l'expose déjà via les imports.

- [ ] **Step 5: Implémenter le SCSS**

Créer `frontend/src/app/features/availability/slot-popover/slot-popover.component.scss` :

```scss
:host {
  display: block;
}

.popover {
  background: #fff;
  border: 1.5px solid var(--mat-sys-primary, #c00066);
  border-radius: 12px;
  padding: 18px;
  min-width: 280px;
  max-width: 360px;
  box-shadow: 0 10px 30px -10px rgba(192, 0, 102, 0.3);
}

.popover-title {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 600;
  color: #2c1c20;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.label {
  width: 50px;
  font-size: 11px;
  color: #6b5560;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

select {
  flex: 1;
  padding: 8px 12px;
  border: 1.5px solid #ead7df;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #2c1c20;
  background: #fbf3f5;
  cursor: pointer;

  &:focus {
    outline: none;
    border-color: var(--mat-sys-primary, #c00066);
  }
}

.error {
  margin: 8px 0 0;
  font-size: 12px;
  color: #b3261e;
}

.copy-row {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #f0dee5;
  font-size: 12px;
  color: #6b5560;
}

.copy-label {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.copy-days {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.copy-day,
.copy-all {
  background: #fff;
  border: 1px dashed var(--mat-sys-primary, #c00066);
  color: var(--mat-sys-primary, #c00066);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
  font-weight: 600;

  &.active {
    background: var(--mat-sys-primary, #c00066);
    color: #fff;
  }
}

.copy-all {
  border-style: solid;
}

.actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f0dee5;
}

.actions-right {
  display: flex;
  gap: 8px;
}

.delete-link {
  background: none;
  border: 0;
  color: #8a727a;
  font-size: 12px;
  cursor: pointer;
  padding: 0;

  &:hover {
    color: #b3261e;
  }
}

.cancel {
  background: #fbf3f5;
  border: 1px solid #ead7df;
  color: #6b5560;
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 12.5px;
  cursor: pointer;
  font-weight: 600;
}

.confirm {
  background: var(--mat-sys-primary, #c00066);
  color: #fff;
  border: 0;
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 12.5px;
  cursor: pointer;
  font-weight: 600;

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}
```

- [ ] **Step 6: Lancer les tests, ils doivent passer**

Run: `cd frontend && npx --no-install ng test --include='**/slot-popover.component.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`

Expected: SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/availability/slot-popover/
git commit -m "feat(availability): slot-popover (selects 30min, copy-to, validation)"
```

---

## Task 5: Service d'ouverture du popover (CDK Overlay sur PC, MatDialog sur mobile)

**Files:**
- Create: `frontend/src/app/features/availability/slot-popover/slot-popover.service.ts`

- [ ] **Step 1: Implémenter le service**

Créer `frontend/src/app/features/availability/slot-popover/slot-popover.service.ts` :

```ts
import { ElementRef, Injectable, inject } from '@angular/core';
import { ConnectedPosition, Overlay, OverlayConfig } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { MatDialog } from '@angular/material/dialog';
import { Observable, Subject, take } from 'rxjs';
import {
  SlotPopoverComponent,
  SlotPopoverData,
  SlotPopoverResult,
} from './slot-popover.component';
import { bottomSheetConfig } from '../../../shared/uis/sheet-handle/bottom-sheet.config';

@Injectable({ providedIn: 'root' })
export class SlotPopoverService {
  private readonly overlay = inject(Overlay);
  private readonly dialog = inject(MatDialog);

  /**
   * Opens the popover positioned next to `anchor` on PC, or as a bottom-sheet
   * on mobile. Returns a stream emitting once with the user's choice and then
   * completing.
   */
  open(
    data: SlotPopoverData,
    anchor: ElementRef<HTMLElement> | null,
    isDesktop: boolean,
  ): Observable<SlotPopoverResult> {
    if (isDesktop && anchor) {
      return this.openOverlay(data, anchor);
    }
    return this.openDialog(data);
  }

  private openOverlay(
    data: SlotPopoverData,
    anchor: ElementRef<HTMLElement>,
  ): Observable<SlotPopoverResult> {
    const positions: ConnectedPosition[] = [
      { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
      { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
    ];
    const positionStrategy = this.overlay
      .position()
      .flexibleConnectedTo(anchor)
      .withPositions(positions)
      .withPush(true);

    const config = new OverlayConfig({
      positionStrategy,
      scrollStrategy: this.overlay.scrollStrategies.reposition(),
      hasBackdrop: true,
      backdropClass: 'slot-popover-backdrop',
    });
    const overlayRef = this.overlay.create(config);
    const portal = new ComponentPortal(SlotPopoverComponent);
    const ref = overlayRef.attach(portal);
    ref.setInput('data', data);

    const result$ = new Subject<SlotPopoverResult>();
    const sub = ref.instance.confirm.subscribe((r) => {
      result$.next(r);
      result$.complete();
      overlayRef.dispose();
    });
    overlayRef.backdropClick().subscribe(() => {
      result$.next({ action: 'cancel' });
      result$.complete();
      overlayRef.dispose();
      sub.unsubscribe();
    });

    return result$.asObservable().pipe(take(1));
  }

  private openDialog(data: SlotPopoverData): Observable<SlotPopoverResult> {
    const ref = this.dialog.open<SlotPopoverComponent, SlotPopoverData, SlotPopoverResult>(
      SlotPopoverComponent,
      bottomSheetConfig({ data, panelClass: 'slot-popover-dialog' }),
    );
    ref.componentRef!.setInput('data', data);

    const result$ = new Subject<SlotPopoverResult>();
    ref.componentInstance.confirm.subscribe((r) => {
      result$.next(r);
      result$.complete();
      ref.close();
    });
    ref.afterClosed().subscribe(() => {
      if (!result$.closed) {
        result$.next({ action: 'cancel' });
        result$.complete();
      }
    });
    return result$.asObservable().pipe(take(1));
  }
}
```

Note : pas de tests dédiés pour ce service (intégration CDK Overlay = test couvert par le composant container en Task 9). Le code reste simple et déterministe.

- [ ] **Step 2: Vérifier que ça compile**

Run: `cd frontend && npx --no-install tsc --noEmit -p tsconfig.app.json 2>&1 | grep -E "(error|slot-popover)" | head -10`

Expected: aucune erreur.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/features/availability/slot-popover/slot-popover.service.ts
git commit -m "feat(availability): slot-popover service (CDK Overlay + MatDialog)"
```

---

## Task 6: Timeline component — TS et tests

**Files:**
- Create: `frontend/src/app/features/availability/timeline/availability-timeline.component.ts`
- Create: `frontend/src/app/features/availability/timeline/availability-timeline.component.spec.ts`

- [ ] **Step 1: Écrire les tests**

Créer `frontend/src/app/features/availability/timeline/availability-timeline.component.spec.ts` :

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { AvailabilityTimelineComponent } from './availability-timeline.component';
import { WeekSlots } from '../availability.model';

const translations = {
  'pro.availability.days.1': 'Mon',
  'pro.availability.days.2': 'Tue',
  'pro.availability.days.3': 'Wed',
  'pro.availability.days.4': 'Thu',
  'pro.availability.days.5': 'Fri',
  'pro.availability.days.6': 'Sat',
  'pro.availability.days.7': 'Sun',
  'pro.availability.closed': 'Closed',
  'pro.availability.timeline.addSlot': 'Add slot',
};

const week: WeekSlots = [
  { dayOfWeek: 1, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 2, slots: [
    { openTime: '09:00', closeTime: '13:00' },
    { openTime: '14:00', closeTime: '18:00' },
  ] },
  { dayOfWeek: 3, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 4, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 5, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 6, slots: [] },
  { dayOfWeek: 7, slots: [] },
];

function setup(weekSlots: WeekSlots): ComponentFixture<AvailabilityTimelineComponent> {
  TestBed.configureTestingModule({
    imports: [
      AvailabilityTimelineComponent,
      TranslocoTestingModule.forRoot({
        langs: { en: translations },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      }),
    ],
    providers: [provideZonelessChangeDetection()],
  });
  const fixture = TestBed.createComponent(AvailabilityTimelineComponent);
  fixture.componentRef.setInput('week', weekSlots);
  fixture.detectChanges();
  return fixture;
}

describe('AvailabilityTimelineComponent', () => {
  it('renders 7 day rows', () => {
    const fixture = setup(week);
    const rows = fixture.nativeElement.querySelectorAll('.timeline-row');
    expect(rows.length).toBe(7);
  });

  it('renders one slot block per slot on Tuesday (2 blocks)', () => {
    const fixture = setup(week);
    const tuesdayRow = fixture.nativeElement.querySelectorAll('.timeline-row')[1];
    const blocks = tuesdayRow.querySelectorAll('.slot-block');
    expect(blocks.length).toBe(2);
  });

  it('renders the closed band on Saturday and Sunday', () => {
    const fixture = setup(week);
    const rows = fixture.nativeElement.querySelectorAll('.timeline-row');
    expect(rows[5].querySelector('.closed-block')).toBeTruthy();
    expect(rows[6].querySelector('.closed-block')).toBeTruthy();
  });

  it('emits slotClick with day, index, and anchor', () => {
    const fixture = setup(week);
    let event: { day: number; slotIndex: number; anchor: HTMLElement } | null = null;
    fixture.componentInstance.slotClick.subscribe((e) => (event = e));

    const firstBlock = fixture.nativeElement.querySelector('.slot-block') as HTMLElement;
    firstBlock.click();
    fixture.detectChanges();

    expect(event).toBeTruthy();
    expect(event!.day).toBe(1);
    expect(event!.slotIndex).toBe(0);
    expect(event!.anchor).toBe(firstBlock);
  });

  it('emits dayToggle on switch click', () => {
    const fixture = setup(week);
    let toggled: number | null = null;
    fixture.componentInstance.dayToggle.subscribe((d) => (toggled = d));

    const firstSwitch = fixture.nativeElement.querySelector('.switch') as HTMLElement;
    firstSwitch.click();
    fixture.detectChanges();

    expect(toggled).toBe(1);
  });

  it('emits addSlotClick on + button', () => {
    const fixture = setup(week);
    let event: { day: number; anchor: HTMLElement } | null = null;
    fixture.componentInstance.addSlotClick.subscribe((e) => (event = e));

    const firstAddBtn = fixture.nativeElement.querySelector('.add-slot-btn') as HTMLElement;
    firstAddBtn.click();
    fixture.detectChanges();

    expect(event).toBeTruthy();
    expect(event!.day).toBe(1);
  });
});
```

- [ ] **Step 2: Lancer les tests, ils doivent échouer**

Run: `cd frontend && npx --no-install ng test --include='**/availability-timeline.component.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -8`

Expected: import errors.

- [ ] **Step 3: Implémenter le composant TS**

Créer `frontend/src/app/features/availability/timeline/availability-timeline.component.ts` :

```ts
import { Component, EventEmitter, Output, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { DayOfWeek, WeekSlots } from '../availability.model';
import { positionInRail } from '../time-utils';

export interface SlotClickEvent {
  day: DayOfWeek;
  slotIndex: number;
  anchor: HTMLElement;
}

export interface AddSlotClickEvent {
  day: DayOfWeek;
  anchor: HTMLElement;
}

@Component({
  selector: 'app-availability-timeline',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  templateUrl: './availability-timeline.component.html',
  styleUrl: './availability-timeline.component.scss',
})
export class AvailabilityTimelineComponent {
  readonly week = input.required<WeekSlots>();

  @Output() readonly slotClick = new EventEmitter<SlotClickEvent>();
  @Output() readonly addSlotClick = new EventEmitter<AddSlotClickEvent>();
  @Output() readonly dayToggle = new EventEmitter<DayOfWeek>();

  /** Hours displayed on the axis: 6, 7, ..., 21 (16 entries). */
  readonly hours = Array.from({ length: 16 }, (_, i) => i + 6);

  readonly daysWithDerived = computed(() =>
    this.week().map((d) => ({
      ...d,
      isClosed: d.slots.length === 0,
      blocks: d.slots.map((s) => ({
        slot: s,
        leftPct: positionInRail(s.openTime),
        widthPct: positionInRail(s.closeTime) - positionInRail(s.openTime),
      })),
    })),
  );

  onSlotClick(event: MouseEvent, day: DayOfWeek, slotIndex: number): void {
    event.stopPropagation();
    this.slotClick.emit({
      day,
      slotIndex,
      anchor: event.currentTarget as HTMLElement,
    });
  }

  onAddSlotClick(event: MouseEvent, day: DayOfWeek): void {
    event.stopPropagation();
    this.addSlotClick.emit({
      day,
      anchor: event.currentTarget as HTMLElement,
    });
  }

  onSwitchClick(day: DayOfWeek): void {
    this.dayToggle.emit(day);
  }
}
```

- [ ] **Step 4: Implémenter le HTML**

Créer `frontend/src/app/features/availability/timeline/availability-timeline.component.html` :

```html
<div class="timeline-card">
  <div class="hours-axis">
    <span class="axis-spacer"></span>
    <div class="hours-axis-track">
      @for (h of hours; track h) {
        <span>{{ h }}</span>
      }
    </div>
  </div>

  @for (day of daysWithDerived(); track day.dayOfWeek) {
    <div class="timeline-row" [class.is-closed]="day.isClosed">
      <div class="day-label">
        <span class="day-label-name">{{ ('pro.availability.days.' + day.dayOfWeek) | transloco }}</span>
        <button
          type="button"
          class="switch"
          [class.off]="day.isClosed"
          [attr.aria-pressed]="!day.isClosed"
          (click)="onSwitchClick($any(day.dayOfWeek))"
        ></button>
      </div>

      <div class="timeline-track">
        @if (day.isClosed) {
          <div class="closed-block">{{ 'pro.availability.closed' | transloco }}</div>
        } @else {
          <div class="hour-grid">
            @for (h of hours; track h) {
              <div></div>
            }
          </div>
          @for (b of day.blocks; track $index) {
            <button
              type="button"
              class="slot-block"
              [style.left.%]="b.leftPct"
              [style.width.%]="b.widthPct"
              (click)="onSlotClick($event, $any(day.dayOfWeek), $index)"
            >
              {{ b.slot.openTime }} — {{ b.slot.closeTime }}
            </button>
          }
        }
      </div>

      <button
        type="button"
        class="add-slot-btn"
        [disabled]="day.isClosed"
        [attr.aria-label]="'pro.availability.timeline.addSlot' | transloco"
        (click)="onAddSlotClick($event, $any(day.dayOfWeek))"
      >+</button>
    </div>
  }
</div>
```

- [ ] **Step 5: Lancer les tests, ils doivent passer**

Run: `cd frontend && npx --no-install ng test --include='**/availability-timeline.component.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`

Expected: SUCCESS (le SCSS sera ajouté à l'étape suivante mais les tests passent en l'absence du fichier SCSS — Angular accepte un styleUrl absent en mode test ? Si non, créer un fichier vide d'abord. Vérifier.)

Si Angular se plaint du SCSS manquant, créer un fichier vide :
```bash
touch frontend/src/app/features/availability/timeline/availability-timeline.component.scss
```
puis relancer les tests.

- [ ] **Step 6: Commit (logique + template, SCSS au prochain task)**

```bash
git add frontend/src/app/features/availability/timeline/availability-timeline.component.ts \
  frontend/src/app/features/availability/timeline/availability-timeline.component.html \
  frontend/src/app/features/availability/timeline/availability-timeline.component.spec.ts \
  frontend/src/app/features/availability/timeline/availability-timeline.component.scss
git commit -m "feat(availability): timeline component (PC view, blocks + axis)"
```

---

## Task 7: Timeline component — SCSS

**Files:**
- Modify (rewrite): `frontend/src/app/features/availability/timeline/availability-timeline.component.scss`

- [ ] **Step 1: Écrire le SCSS**

Remplacer le fichier SCSS vide créé en Task 6 par :

```scss
:host {
  display: block;
}

.timeline-card {
  background: #fff;
  border: 1px solid #ead7df;
  border-radius: 12px;
  padding: 20px 22px;
}

.hours-axis {
  display: grid;
  grid-template-columns: 90px 1fr 36px;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0dee5;
  margin-bottom: 14px;
  font-size: 10.5px;
  color: #8a727a;

  .axis-spacer {
    /* matches .day-label width */
  }
}

.hours-axis-track {
  display: grid;
  grid-template-columns: repeat(16, 1fr);

  span {
    text-align: left;
  }
}

.timeline-row {
  display: grid;
  grid-template-columns: 90px 1fr 36px;
  align-items: center;
  padding: 11px 0;
  border-top: 1px solid #f5e7eb;

  &:first-of-type {
    border-top: 0;
  }
}

.day-label {
  display: flex;
  align-items: center;
  gap: 10px;
}

.day-label-name {
  font-weight: 600;
  font-size: 14px;
  color: #2c1c20;
}

.switch {
  width: 26px;
  height: 16px;
  background: var(--mat-sys-primary, #c00066);
  border: 0;
  border-radius: 999px;
  position: relative;
  cursor: pointer;
  transition: background 0.15s;
  padding: 0;

  &::after {
    content: '';
    position: absolute;
    top: 2px;
    right: 2px;
    width: 12px;
    height: 12px;
    background: #fff;
    border-radius: 50%;
    transition: all 0.15s;
  }

  &.off {
    background: #d9c6ce;

    &::after {
      right: auto;
      left: 2px;
    }
  }
}

.timeline-track {
  position: relative;
  height: 44px;
  background: #fbf3f5;
  border: 1px solid #f0dee5;
  border-radius: 8px;
  overflow: visible;
}

.hour-grid {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: repeat(16, 1fr);
  pointer-events: none;

  div {
    border-right: 1px dashed #f0dee5;

    &:last-child {
      border-right: 0;
    }
  }
}

.slot-block {
  position: absolute;
  top: 4px;
  bottom: 4px;
  background: linear-gradient(135deg, #c00066, #e85a8e);
  border: 0;
  border-radius: 6px;
  padding: 0 10px;
  color: #fff;
  font-size: 11.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 6px -2px rgba(192, 0, 102, 0.5);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &:hover {
    box-shadow: 0 4px 10px -2px rgba(192, 0, 102, 0.6);
    transform: translateY(-1px);
  }

  &:focus-visible {
    outline: 2px solid #fff;
    outline-offset: 2px;
  }
}

.closed-block {
  position: absolute;
  inset: 4px;
  background: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 7px,
    #f9e3ea 7px,
    #f9e3ea 14px
  );
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #b88;
  font-size: 12px;
  font-style: italic;
  pointer-events: none;
}

.add-slot-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1.5px dashed var(--mat-sys-primary, #c00066);
  color: var(--mat-sys-primary, #c00066);
  background: #fff;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 8px;

  &:hover:not(:disabled) {
    background: #fce8ee;
  }

  &:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/features/availability/timeline/availability-timeline.component.scss
git commit -m "style(availability): timeline visual styles"
```

---

## Task 8: Day-list component (vue mobile) — TS, HTML, SCSS, tests

**Files:**
- Create: `frontend/src/app/features/availability/day-list/availability-day-list.component.ts`
- Create: `frontend/src/app/features/availability/day-list/availability-day-list.component.html`
- Create: `frontend/src/app/features/availability/day-list/availability-day-list.component.scss`
- Create: `frontend/src/app/features/availability/day-list/availability-day-list.component.spec.ts`

- [ ] **Step 1: Écrire les tests**

Créer `frontend/src/app/features/availability/day-list/availability-day-list.component.spec.ts` :

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { AvailabilityDayListComponent } from './availability-day-list.component';
import { WeekSlots } from '../availability.model';

const translations = {
  'pro.availability.days.1': 'Monday',
  'pro.availability.days.2': 'Tuesday',
  'pro.availability.days.3': 'Wednesday',
  'pro.availability.days.4': 'Thursday',
  'pro.availability.days.5': 'Friday',
  'pro.availability.days.6': 'Saturday',
  'pro.availability.days.7': 'Sunday',
  'pro.availability.closed': 'Closed',
  'pro.availability.daylist.addPause': 'Add pause',
};

const week: WeekSlots = [
  { dayOfWeek: 1, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 2, slots: [] },
  { dayOfWeek: 3, slots: [] },
  { dayOfWeek: 4, slots: [] },
  { dayOfWeek: 5, slots: [] },
  { dayOfWeek: 6, slots: [] },
  { dayOfWeek: 7, slots: [] },
];

function setup(weekSlots: WeekSlots): ComponentFixture<AvailabilityDayListComponent> {
  TestBed.configureTestingModule({
    imports: [
      AvailabilityDayListComponent,
      TranslocoTestingModule.forRoot({
        langs: { en: translations },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      }),
    ],
    providers: [provideZonelessChangeDetection()],
  });
  const fixture = TestBed.createComponent(AvailabilityDayListComponent);
  fixture.componentRef.setInput('week', weekSlots);
  fixture.detectChanges();
  return fixture;
}

describe('AvailabilityDayListComponent', () => {
  it('renders 7 day cards', () => {
    const fixture = setup(week);
    const cards = fixture.nativeElement.querySelectorAll('.day-card');
    expect(cards.length).toBe(7);
  });

  it('renders the slot summary on Monday', () => {
    const fixture = setup(week);
    const monday = fixture.nativeElement.querySelector('.day-card');
    expect(monday.textContent).toContain('09:00');
    expect(monday.textContent).toContain('18:00');
  });

  it('emits slotClick with anchor', () => {
    const fixture = setup(week);
    let event: { day: number; slotIndex: number; anchor: HTMLElement } | null = null;
    fixture.componentInstance.slotClick.subscribe((e) => (event = e));

    const timeBox = fixture.nativeElement.querySelector('.time-box') as HTMLElement;
    timeBox.click();
    fixture.detectChanges();

    expect(event!.day).toBe(1);
    expect(event!.slotIndex).toBe(0);
  });

  it('emits dayToggle on switch click', () => {
    const fixture = setup(week);
    let toggled: number | null = null;
    fixture.componentInstance.dayToggle.subscribe((d) => (toggled = d));

    const firstSwitch = fixture.nativeElement.querySelector('.switch') as HTMLElement;
    firstSwitch.click();

    expect(toggled).toBe(1);
  });
});
```

- [ ] **Step 2: Implémenter TS**

Créer `frontend/src/app/features/availability/day-list/availability-day-list.component.ts` :

```ts
import { Component, EventEmitter, Output, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { DayOfWeek, WeekSlots } from '../availability.model';
import { SlotClickEvent, AddSlotClickEvent } from '../timeline/availability-timeline.component';

@Component({
  selector: 'app-availability-day-list',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  templateUrl: './availability-day-list.component.html',
  styleUrl: './availability-day-list.component.scss',
})
export class AvailabilityDayListComponent {
  readonly week = input.required<WeekSlots>();

  @Output() readonly slotClick = new EventEmitter<SlotClickEvent>();
  @Output() readonly addSlotClick = new EventEmitter<AddSlotClickEvent>();
  @Output() readonly dayToggle = new EventEmitter<DayOfWeek>();

  isClosed(day: { slots: { openTime: string; closeTime: string }[] }): boolean {
    return day.slots.length === 0;
  }

  onSlotClick(event: MouseEvent, day: DayOfWeek, slotIndex: number): void {
    event.stopPropagation();
    this.slotClick.emit({
      day,
      slotIndex,
      anchor: event.currentTarget as HTMLElement,
    });
  }

  onAddSlotClick(event: MouseEvent, day: DayOfWeek): void {
    event.stopPropagation();
    this.addSlotClick.emit({
      day,
      anchor: event.currentTarget as HTMLElement,
    });
  }

  onSwitchClick(day: DayOfWeek): void {
    this.dayToggle.emit(day);
  }
}
```

- [ ] **Step 3: Implémenter HTML**

Créer `frontend/src/app/features/availability/day-list/availability-day-list.component.html` :

```html
<div class="day-list">
  @for (day of week(); track day.dayOfWeek) {
    <div class="day-card" [class.is-closed]="isClosed(day)">
      <div class="day-header">
        <span class="day-name">{{ ('pro.availability.days.' + day.dayOfWeek) | transloco }}</span>
        <span class="day-summary" [class.closed]="isClosed(day)">
          @if (isClosed(day)) {
            {{ 'pro.availability.closed' | transloco }}
          } @else {
            @for (s of day.slots; track $index; let last = $last) {
              <span>{{ s.openTime }} → {{ s.closeTime }}</span>
              @if (!last) { <span> · </span> }
            }
          }
        </span>
        <button
          type="button"
          class="switch"
          [class.off]="isClosed(day)"
          [attr.aria-pressed]="!isClosed(day)"
          (click)="onSwitchClick($any(day.dayOfWeek))"
        ></button>
      </div>

      @if (!isClosed(day)) {
        @for (slot of day.slots; track $index) {
          <div class="slot-row">
            <button
              type="button"
              class="time-box"
              (click)="onSlotClick($event, $any(day.dayOfWeek), $index)"
            >
              {{ slot.openTime }}
            </button>
            <span class="arrow">→</span>
            <button
              type="button"
              class="time-box"
              (click)="onSlotClick($event, $any(day.dayOfWeek), $index)"
            >
              {{ slot.closeTime }}
            </button>
          </div>
        }
        <button
          type="button"
          class="add-pause"
          (click)="onAddSlotClick($event, $any(day.dayOfWeek))"
        >
          + {{ 'pro.availability.daylist.addPause' | transloco }}
        </button>
      }
    </div>
  }
</div>
```

- [ ] **Step 4: Implémenter SCSS**

Créer `frontend/src/app/features/availability/day-list/availability-day-list.component.scss` :

```scss
:host {
  display: block;
}

.day-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-card {
  background: #fff;
  border: 1px solid #ead7df;
  border-radius: 10px;
  padding: 12px 14px;
}

.day-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.day-name {
  font-weight: 600;
  min-width: 70px;
  color: #2c1c20;
  font-size: 14px;
}

.day-summary {
  color: #6b5560;
  font-size: 12px;
  flex: 1;

  &.closed {
    color: #b88;
    font-style: italic;
  }
}

.switch {
  width: 26px;
  height: 16px;
  background: var(--mat-sys-primary, #c00066);
  border: 0;
  border-radius: 999px;
  position: relative;
  cursor: pointer;
  padding: 0;

  &::after {
    content: '';
    position: absolute;
    top: 2px;
    right: 2px;
    width: 12px;
    height: 12px;
    background: #fff;
    border-radius: 50%;
    transition: all 0.15s;
  }

  &.off {
    background: #d9c6ce;

    &::after {
      right: auto;
      left: 2px;
    }
  }
}

.slot-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding-left: 70px;
}

.time-box {
  background: #fbf3f5;
  border: 1.5px solid #ead7df;
  border-radius: 8px;
  padding: 7px 10px;
  font-size: 13px;
  font-weight: 600;
  min-width: 64px;
  text-align: center;
  color: #2c1c20;
  cursor: pointer;

  &:hover {
    border-color: var(--mat-sys-primary, #c00066);
  }
}

.arrow {
  color: var(--mat-sys-primary, #c00066);
}

.add-pause {
  font-size: 12px;
  color: var(--mat-sys-primary, #c00066);
  margin-top: 8px;
  padding: 4px 10px;
  background: #fce8ee;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  margin-left: 70px;
}
```

- [ ] **Step 5: Lancer les tests**

Run: `cd frontend && npx --no-install ng test --include='**/availability-day-list.component.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -10`

Expected: SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/features/availability/day-list/
git commit -m "feat(availability): day-list component (mobile view)"
```

---

## Task 9: Réécriture du conteneur `AvailabilityComponent` + i18n

**Files:**
- Modify: `frontend/src/app/features/availability/availability.component.ts`
- Modify: `frontend/src/app/features/availability/availability.component.html`
- Modify: `frontend/src/app/features/availability/availability.component.scss`
- Modify: `frontend/src/app/features/availability/availability.component.spec.ts`
- Modify: `frontend/public/i18n/fr.json`
- Modify: `frontend/public/i18n/en.json`

- [ ] **Step 1: Ajouter les nouvelles clés i18n FR**

Dans `frontend/public/i18n/fr.json`, dans le bloc `pro.availability` (déjà existant à la ligne ~635), ajouter ces clés à la suite des clés existantes (avant la fermeture `}` du bloc `availability`) :

```json
      "timeline": {
        "addSlot": "Ajouter un créneau"
      },
      "daylist": {
        "addPause": "Ajouter une pause"
      },
      "popover": {
        "title": {
          "create": "Nouveau créneau · {{day}}",
          "edit": "Modifier · {{day}}"
        },
        "startLabel": "Début",
        "endLabel": "Fin",
        "copyTo": "Copier vers :",
        "copyAll": "Toute la semaine",
        "delete": "Supprimer",
        "cancel": "Annuler",
        "confirm": "Valider"
      },
      "preset": {
        "fullWeek": "Pleine semaine 9—18",
        "midDayBreak": "Avec pause midi",
        "closeAll": "Tout fermer",
        "confirmCloseAll": "Cela va fermer tous les jours de la semaine. Continuer ?"
      },
      "invalidOverlap": "Ce créneau chevauche un autre",
      "toolbar": {
        "summary": "{{open}} h ouvert / semaine"
      }
```

Vérifier la validité JSON :

```bash
node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/fr.json'))" && echo OK
```

Expected: `OK`.

- [ ] **Step 2: Ajouter les mêmes clés EN dans `frontend/public/i18n/en.json`**

Trouver le bloc `pro.availability` correspondant (cherche la section avec `"openDays"` ou similaire) et ajoute :

```json
      "timeline": {
        "addSlot": "Add slot"
      },
      "daylist": {
        "addPause": "Add pause"
      },
      "popover": {
        "title": {
          "create": "New slot · {{day}}",
          "edit": "Edit · {{day}}"
        },
        "startLabel": "Start",
        "endLabel": "End",
        "copyTo": "Copy to:",
        "copyAll": "All week",
        "delete": "Delete",
        "cancel": "Cancel",
        "confirm": "Confirm"
      },
      "preset": {
        "fullWeek": "Full week 9—18",
        "midDayBreak": "With lunch break",
        "closeAll": "Close all",
        "confirmCloseAll": "This will close every day of the week. Continue?"
      },
      "invalidOverlap": "This slot overlaps another",
      "toolbar": {
        "summary": "{{open}} h open / week"
      }
```

Vérifier :

```bash
node -e "JSON.parse(require('fs').readFileSync('frontend/public/i18n/en.json'))" && echo OK
```

- [ ] **Step 3: Réécrire `availability.component.ts`**

Remplacer **intégralement** le contenu de `frontend/src/app/features/availability/availability.component.ts` par :

```ts
import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AvailabilityStore } from './availability.store';
import {
  DayOfWeek,
  OpeningHourRequest,
  OpeningHourResponse,
  TimeSlot,
  WeekSlots,
} from './availability.model';
import { DashboardStore } from '../dashboard/store/dashboard.store';
import { isDesktopSignal } from '../../core/utils/breakpoint.signal';
import { AvailabilityTimelineComponent, SlotClickEvent, AddSlotClickEvent } from './timeline/availability-timeline.component';
import { AvailabilityDayListComponent } from './day-list/availability-day-list.component';
import { SlotPopoverService } from './slot-popover/slot-popover.service';
import { SlotPopoverData } from './slot-popover/slot-popover.component';
import { WEEK_PRESETS, WeekPresetKey, applyPreset } from './presets/week-presets';
import { hhmmToMinutes } from './time-utils';

const DEFAULT_SLOT: TimeSlot = { openTime: '09:00', closeTime: '18:00' };

@Component({
  selector: 'app-availability',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslocoPipe,
    MatSnackBarModule,
    MatIconModule,
    MatButtonModule,
    AvailabilityTimelineComponent,
    AvailabilityDayListComponent,
  ],
  templateUrl: './availability.component.html',
  styleUrl: './availability.component.scss',
  providers: [AvailabilityStore],
})
export class AvailabilityComponent {
  readonly store = inject(AvailabilityStore);
  private readonly dashboardStore = inject(DashboardStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);
  private readonly popover = inject(SlotPopoverService);

  readonly isDesktop = isDesktopSignal();
  readonly weekDays: DayOfWeek[] = [1, 2, 3, 4, 5, 6, 7];
  readonly week = signal<WeekSlots>(this.buildEmptyWeek());
  readonly presets = WEEK_PRESETS;

  constructor() {
    effect(() => {
      const hours = this.store.hours();
      if (hours) {
        this.syncFromStoreData(hours);
      }
    });
    effect(() => {
      if (this.store.saveSuccess()) {
        this.dashboardStore.loadReadiness();
        this.store.clearSaveSuccess();
      }
    });
  }

  // ============ KPIs ============
  readonly openDaysCount = computed(
    () => this.week().filter((d) => d.slots.length > 0).length,
  );

  readonly totalSlotsCount = computed(
    () => this.week().reduce((acc, d) => acc + d.slots.length, 0),
  );

  readonly weeklyTotalMinutes = computed(() =>
    this.week().reduce((acc, d) => {
      return acc + d.slots.reduce((dAcc, s) => {
        const start = hhmmToMinutes(s.openTime);
        const end = hhmmToMinutes(s.closeTime);
        return end > start ? dAcc + (end - start) : dAcc;
      }, 0);
    }, 0),
  );

  formatDuration(min: number): string {
    if (min <= 0) return '0 h';
    const h = Math.floor(min / 60);
    const m = min % 60;
    if (h === 0) return `${m} min`;
    return m === 0 ? `${h} h` : `${h} h ${m.toString().padStart(2, '0')}`;
  }

  // ============ Presets ============
  applyPreset(key: WeekPresetKey): void {
    if (key === 'closeAll') {
      const ok = window.confirm(
        this.i18n.translate('pro.availability.preset.confirmCloseAll'),
      );
      if (!ok) return;
    }
    this.week.set(applyPreset(key, this.week()));
  }

  // ============ Toggle day ============
  onDayToggle(day: DayOfWeek): void {
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== day) return d;
        return d.slots.length === 0
          ? { ...d, slots: [{ ...DEFAULT_SLOT }] }
          : { ...d, slots: [] };
      }),
    );
  }

  // ============ Slot edit / create ============
  onSlotClick(e: SlotClickEvent): void {
    const slot = this.week().find((d) => d.dayOfWeek === e.day)?.slots[e.slotIndex];
    if (!slot) return;
    this.openPopover('edit', e.day, slot.openTime, slot.closeTime, e.slotIndex, e.anchor);
  }

  onAddSlot(e: AddSlotClickEvent): void {
    const day = this.week().find((d) => d.dayOfWeek === e.day);
    if (!day) return;
    const last = day.slots[day.slots.length - 1];
    const start = last ? last.closeTime : '09:00';
    const end = this.bumpHour(start);
    this.openPopover('create', e.day, start, end, null, e.anchor);
  }

  private bumpHour(start: string): string {
    const min = hhmmToMinutes(start);
    const target = Math.min(min + 60, hhmmToMinutes('22:00'));
    const h = Math.floor(target / 60);
    const m = target % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  }

  private openPopover(
    mode: 'create' | 'edit',
    day: DayOfWeek,
    initialStart: string,
    initialEnd: string,
    editIndex: number | null,
    anchor: HTMLElement,
  ): void {
    const otherDays = this.weekDays.filter((d) => d !== day);
    const existingSlots = this.week().find((d) => d.dayOfWeek === day)?.slots ?? [];
    const existingForOverlap =
      editIndex == null
        ? existingSlots
        : existingSlots.filter((_, i) => i !== editIndex);

    const data: SlotPopoverData = {
      mode,
      dayOfWeek: day,
      initialStart,
      initialEnd,
      otherDays,
      existingSlotsForDay: existingForOverlap,
    };

    this.popover.open(data, new ElementRef(anchor), this.isDesktop()).subscribe((result) => {
      if (result.action === 'cancel') return;
      if (result.action === 'delete') {
        if (editIndex == null) return;
        this.removeSlot(day, editIndex);
        return;
      }
      // save
      const newSlot: TimeSlot = { openTime: result.start, closeTime: result.end };
      if (editIndex == null) {
        this.appendSlot(day, newSlot);
      } else {
        this.replaceSlot(day, editIndex, newSlot);
      }
      if (result.copyToDays.length > 0) {
        this.copySlotsToDays(day, result.copyToDays);
      }
    });
  }

  private appendSlot(day: DayOfWeek, slot: TimeSlot): void {
    this.week.update((w) =>
      w.map((d) => (d.dayOfWeek === day ? { ...d, slots: [...d.slots, slot] } : d)),
    );
  }

  private replaceSlot(day: DayOfWeek, index: number, slot: TimeSlot): void {
    this.week.update((w) =>
      w.map((d) =>
        d.dayOfWeek === day
          ? { ...d, slots: d.slots.map((s, i) => (i === index ? slot : s)) }
          : d,
      ),
    );
  }

  private removeSlot(day: DayOfWeek, index: number): void {
    this.week.update((w) =>
      w.map((d) =>
        d.dayOfWeek === day
          ? { ...d, slots: d.slots.filter((_, i) => i !== index) }
          : d,
      ),
    );
  }

  /** Override the slots of every target day with the source day's slots. */
  private copySlotsToDays(sourceDay: DayOfWeek, targets: DayOfWeek[]): void {
    const sourceSlots = this.week().find((d) => d.dayOfWeek === sourceDay)?.slots ?? [];
    this.week.update((w) =>
      w.map((d) =>
        targets.includes(d.dayOfWeek as DayOfWeek)
          ? { ...d, slots: sourceSlots.map((s) => ({ ...s })) }
          : d,
      ),
    );
  }

  // ============ Save ============
  onSave(): void {
    const requests: OpeningHourRequest[] = [];
    for (const day of this.week()) {
      for (const slot of day.slots) {
        requests.push({
          dayOfWeek: day.dayOfWeek,
          openTime: slot.openTime,
          closeTime: slot.closeTime,
        });
      }
    }
    this.store.saveHours(requests);
    this.snackBar.open(this.i18n.translate('pro.availability.saveSuccess'), 'OK', {
      duration: 3000,
    });
  }

  // ============ Sync from store ============
  private syncFromStoreData(hours: OpeningHourResponse[]): void {
    const week = this.buildEmptyWeek();
    for (const h of hours) {
      const day = week.find((d) => d.dayOfWeek === h.dayOfWeek);
      if (day) {
        day.slots.push({ openTime: h.openTime, closeTime: h.closeTime });
      }
    }
    this.week.set(week);
  }

  private buildEmptyWeek(): WeekSlots {
    return this.weekDays.map((d) => ({ dayOfWeek: d, slots: [] }));
  }
}
```

- [ ] **Step 4: Réécrire le HTML**

Remplacer `frontend/src/app/features/availability/availability.component.html` par :

```html
<div class="availability-page" data-tour-step="opening-hours">

  <!-- ============ Page header ============ -->
  <div class="page-header">
    <div class="page-header-titles">
      <span class="page-eyebrow">{{ 'pro.availability.subtitle' | transloco }}</span>
      <h1 class="page-title">{{ 'pro.availability.title' | transloco }}</h1>
    </div>
    <section class="kpis">
      <div class="kpi">
        <div class="kpi-label">{{ 'pro.availability.kpi.openDays' | transloco }}</div>
        <div class="kpi-value">{{ openDaysCount() }}<small>/ 7</small></div>
      </div>
      <div class="kpi">
        <div class="kpi-label">{{ 'pro.availability.kpi.weeklyHours' | transloco }}</div>
        <div class="kpi-value">{{ formatDuration(weeklyTotalMinutes()) }}</div>
      </div>
      <div class="kpi">
        <div class="kpi-label">{{ 'pro.availability.kpi.totalSlots' | transloco }}</div>
        <div class="kpi-value">{{ totalSlotsCount() }}</div>
      </div>
    </section>
  </div>

  <!-- ============ Toolbar (presets) ============ -->
  <div class="toolbar">
    <div class="toolbar-left">
      {{ 'pro.availability.toolbar.summary' | transloco: { open: formatDuration(weeklyTotalMinutes()) } }}
    </div>
    <div class="quick-actions">
      @for (preset of presets; track preset.key) {
        <button type="button" class="quick-btn" (click)="applyPreset(preset.key)">
          {{ preset.labelKey | transloco }}
        </button>
      }
    </div>
  </div>

  <!-- ============ Layout switch ============ -->
  @if (isDesktop()) {
    <app-availability-timeline
      [week]="week()"
      (slotClick)="onSlotClick($event)"
      (addSlotClick)="onAddSlot($event)"
      (dayToggle)="onDayToggle($event)"
    />
  } @else {
    <app-availability-day-list
      [week]="week()"
      (slotClick)="onSlotClick($event)"
      (addSlotClick)="onAddSlot($event)"
      (dayToggle)="onDayToggle($event)"
    />
  }

  <!-- ============ Sticky save bar ============ -->
  <div class="savebar">
    <div class="savebar-inner">
      <div class="savebar-status"></div>
      <div class="savebar-actions">
        <button
          type="button"
          class="save-btn"
          data-testid="availability-save"
          (click)="onSave()"
        >
          {{ 'pro.availability.save' | transloco }}
        </button>
      </div>
    </div>
  </div>
</div>
```

- [ ] **Step 5: Réécrire le SCSS**

Remplacer `frontend/src/app/features/availability/availability.component.scss` par :

```scss
:host {
  display: block;
}

.availability-page {
  padding: 24px;
  max-width: 1280px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 18px;
  gap: 16px;
  flex-wrap: wrap;
}

.page-eyebrow {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--mat-sys-primary, #c00066);
  font-weight: 600;
}

.page-title {
  font-family: 'Cormorant Garamond', Georgia, serif;
  font-size: 30px;
  margin: 4px 0 0;
  color: #2c1c20;
  font-weight: 500;
}

.kpis {
  display: flex;
  gap: 14px;
}

.kpi {
  background: #fff;
  border: 1px solid #ead7df;
  border-radius: 10px;
  padding: 10px 16px;
}

.kpi-label {
  font-size: 10px;
  color: #8a727a;
  text-transform: uppercase;
  letter-spacing: 0.7px;
}

.kpi-value {
  font-size: 22px;
  font-weight: 600;
  color: #2c1c20;

  small {
    font-size: 12px;
    color: #8a727a;
    font-weight: 400;
  }
}

.toolbar {
  background: #fff;
  border: 1px solid #ead7df;
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 14px;
  flex-wrap: wrap;
}

.toolbar-left {
  color: #6b5560;
  font-size: 13px;
}

.quick-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.quick-btn {
  background: #fce8ee;
  border: 0;
  color: var(--mat-sys-primary, #c00066);
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  font-weight: 600;

  &:hover {
    background: #fad5e0;
  }
}

.savebar {
  position: sticky;
  bottom: 0;
  background: rgba(253, 246, 243, 0.96);
  backdrop-filter: blur(8px);
  border-top: 1px solid #ead7df;
  margin: 24px -24px -24px;
  padding: 12px 24px;
}

.savebar-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1280px;
  margin: 0 auto;
}

.save-btn {
  background: linear-gradient(135deg, #c00066, #e85a8e);
  color: #fff;
  border: 0;
  padding: 12px 24px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    opacity: 0.9;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

@media (max-width: 767px) {
  .availability-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .kpis {
    overflow-x: auto;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .quick-actions {
    justify-content: stretch;
  }

  .quick-btn {
    flex: 1;
  }
}
```

- [ ] **Step 6: Adapter le spec existant**

Remplacer le contenu de `frontend/src/app/features/availability/availability.component.spec.ts` par :

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { AvailabilityComponent } from './availability.component';
import { DashboardStore } from '../dashboard/store/dashboard.store';

const mockTranslations = {
  'pro.availability.title': 'My availability',
  'pro.availability.subtitle': 'Weekly hours',
  'pro.availability.closed': 'Closed',
  'pro.availability.save': 'Save',
  'pro.availability.saveSuccess': 'Hours updated',
  'pro.availability.kpi.openDays': 'Open days',
  'pro.availability.kpi.weeklyHours': 'Weekly hours',
  'pro.availability.kpi.totalSlots': 'Total slots',
  'pro.availability.preset.fullWeek': 'Full week',
  'pro.availability.preset.midDayBreak': 'With break',
  'pro.availability.preset.closeAll': 'Close all',
  'pro.availability.preset.confirmCloseAll': 'Sure?',
  'pro.availability.toolbar.summary': '{{open}} open',
  'pro.availability.timeline.addSlot': 'Add slot',
  'pro.availability.daylist.addPause': 'Add pause',
  'pro.availability.days.1': 'Mon',
  'pro.availability.days.2': 'Tue',
  'pro.availability.days.3': 'Wed',
  'pro.availability.days.4': 'Thu',
  'pro.availability.days.5': 'Fri',
  'pro.availability.days.6': 'Sat',
  'pro.availability.days.7': 'Sun',
};

describe('AvailabilityComponent', () => {
  let fixture: ComponentFixture<AvailabilityComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AvailabilityComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideTranslocoLocale({ defaultLocale: 'en-US' }),
        {
          provide: DashboardStore,
          useValue: { loadReadiness: () => {} },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AvailabilityComponent);
    fixture.detectChanges();
  });

  it('renders page header', () => {
    const title = fixture.nativeElement.querySelector('.page-title');
    expect(title?.textContent).toContain('My availability');
  });

  it('renders 3 KPI tiles', () => {
    const kpis = fixture.nativeElement.querySelectorAll('.kpi');
    expect(kpis.length).toBe(3);
  });

  it('renders the toolbar with 3 preset buttons', () => {
    const btns = fixture.nativeElement.querySelectorAll('.quick-btn');
    expect(btns.length).toBe(3);
  });

  it('renders the savebar with save button', () => {
    const btn = fixture.nativeElement.querySelector('[data-testid="availability-save"]');
    expect(btn).toBeTruthy();
  });
});
```

- [ ] **Step 7: Lancer la suite complète d'availability**

Run: `cd frontend && npx --no-install ng test --include='**/features/availability/**/*.spec.ts' --watch=false --browsers=ChromeHeadless 2>&1 | tail -15`

Expected: tous les specs PASS.

- [ ] **Step 8: Build complet pour confirmer qu'il n'y a pas de régression**

Run: `cd frontend && npx --no-install ng build --configuration=development 2>&1 | tail -10`

Expected: `Application bundle generation complete.` sans erreur ni warning bloquant.

- [ ] **Step 9: Tester manuellement dans le navigateur**

1. Vérifier que le container web est OK : `docker logs --since=10s web-dev | grep -E "Application bundle|✘"`
2. Ouvrir http://localhost:4300/pro/availability (Cmd+Shift+R pour hard refresh).
3. Vérifier sur PC (largeur ≥ 768px) :
   - La timeline s'affiche avec axe horaire 6-21.
   - Cliquer un bloc rose → popover s'ouvre, modifier l'heure, valider → le bloc bouge.
   - Cliquer "+" sur un jour ouvert → popover create.
   - Toggle un jour → bascule fermé (bande hachurée) ↔ ouvert (slot par défaut 9-18).
   - Quick presets : "Pleine semaine 9-18" remplit, "Tout fermer" demande confirmation.
4. Réduire la fenêtre < 768px (DevTools responsive) :
   - Bascule vers la day-list.
   - Cliquer une time-box → popover en bottom-sheet.
5. Cliquer "Enregistrer" → snackbar succès.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/app/features/availability/availability.component.ts \
  frontend/src/app/features/availability/availability.component.html \
  frontend/src/app/features/availability/availability.component.scss \
  frontend/src/app/features/availability/availability.component.spec.ts \
  frontend/public/i18n/fr.json frontend/public/i18n/en.json
git commit -m "feat(availability): rewire container to timeline + day-list + popover"
```

---

## Task 10: Verification finale & cleanup

**Files:** aucun nouveau fichier — vérification.

- [ ] **Step 1: Suite de tests complète du projet**

Run: `cd frontend && npx --no-install ng test --watch=false --browsers=ChromeHeadless 2>&1 | tail -15`

Expected: tous les specs PASS, pas de régression dans le reste de l'app.

- [ ] **Step 2: Type-check global**

Run: `cd frontend && npx --no-install tsc --noEmit -p tsconfig.app.json 2>&1 | head -20`

Expected: aucune erreur.

- [ ] **Step 3: Build production**

Run: `cd frontend && npx --no-install ng build 2>&1 | tail -10`

Expected: `Application bundle generation complete.`

- [ ] **Step 4: Vérification logs container**

Run: `docker logs --since=2m web-dev 2>&1 | grep -E "(✘|ERROR|window is not defined)" | head -10`

Expected: rien de nouveau lié à l'availability.

- [ ] **Step 5: Vérification visuelle finale dans le navigateur**

- Charger http://localhost:4300/pro/availability sur PC, mobile (DevTools), entre les deux (resize live).
- Tester tous les flux : create slot, edit slot, delete slot, toggle day, presets, copier-vers, save.

- [ ] **Step 6: Commit final si modifs cosmétiques**

S'il y a eu des micro-fixes pendant la vérif manuelle :

```bash
git add -A && git commit -m "fix(availability): adjustments after manual review"
```

Sinon, rien à committer — le plan est complet.

---

## Self-review

**Spec coverage** :
- ✅ Layout PC ≥ 768px : timeline horizontale → Tasks 6-7
- ✅ Plage 6h→22h : `RAIL_START_MIN`/`RAIL_END_MIN` dans Task 2, axe rendu en Task 6
- ✅ Granularité 30 min : `HHMM_OPTIONS` (Task 2), selects dans le popover (Task 4)
- ✅ Clic = popover (pas de drag-resize) : Task 4 (composant) + Task 5 (service overlay/dialog)
- ✅ Toggle jour : `onDayToggle` dans Task 9, switches dans Tasks 6 et 8
- ✅ Quick presets : Task 3 (logique), Task 9 (toolbar)
- ✅ Copier vers : Task 4 (popover) + `copySlotsToDays` dans Task 9
- ✅ Mobile fallback day-list : Task 8
- ✅ Style palette rose, gradient blocs, hachures fermé : SCSS dans Tasks 7, 8, 9
- ✅ i18n fr/en avec toutes les clés : Task 9
- ✅ Validation overlap : Task 2 (`slotsOverlap`) + Task 4 (`hasOverlap`)
- ✅ Tests unitaires + container : Tasks 2, 3, 4, 6, 8, 9
- ✅ Pas de drag-resize, pas de minute-by-minute : confirmé dans le code (selects only)

**Placeholder scan** : aucune ligne TBD/TODO/"add appropriate handling" trouvée. Tous les tests ont du code complet, toutes les implémentations ont leurs corps.

**Type consistency** :
- `SlotClickEvent`, `AddSlotClickEvent` définis dans `availability-timeline.component.ts` (Task 6) puis importés dans `availability-day-list.component.ts` (Task 8) et `availability.component.ts` (Task 9). ✓
- `SlotPopoverData`, `SlotPopoverResult` définis dans Task 4, utilisés en Tasks 5 et 9. ✓
- `WeekPresetKey` exporté dans Task 3, utilisé en Task 9. ✓
- `DayOfWeek` ajouté en Task 1, utilisé partout. ✓
- `bottomSheetConfig` importé tel qu'il existe dans le projet (`shared/uis/sheet-handle/bottom-sheet.config.ts`), pas de signature size. ✓

**Scope check** : un seul plan, une seule page. Pas de décomposition nécessaire.

---

## Plan complete

Plan written and committed to `docs/superpowers/plans/2026-05-09-availability-timeline-redesign.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — Je dispatche un subagent frais par task, review entre les tasks, itération rapide.

**2. Inline Execution** — J'exécute les tasks dans cette session avec executing-plans, batch execution avec checkpoints.

**Which approach?**
