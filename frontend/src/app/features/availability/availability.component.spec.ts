import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { AvailabilityComponent } from './availability.component';

const mockTranslations = {
  'pro.availability.title': 'My availability',
  'pro.availability.closed': 'Closed',
  'pro.availability.open': 'Open',
  'pro.availability.addSlot': 'Add time slot',
  'pro.availability.save': 'Save',
  'pro.availability.saveSuccess': 'Hours updated',
  'pro.availability.clickToOpen': 'Click to open',
  'pro.availability.days.1': 'Monday',
  'pro.availability.days.2': 'Tuesday',
  'pro.availability.days.3': 'Wednesday',
  'pro.availability.days.4': 'Thursday',
  'pro.availability.days.5': 'Friday',
  'pro.availability.days.6': 'Saturday',
  'pro.availability.days.7': 'Sunday',
};

describe('AvailabilityComponent', () => {
  let component: AvailabilityComponent;
  let fixture: ComponentFixture<AvailabilityComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AvailabilityComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: { defaultLang: 'en', availableLangs: ['en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideTranslocoLocale({
          defaultLocale: 'en-US',
          langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' },
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AvailabilityComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ── Initial state ──

  it('should have 7 week days', () => {
    expect(component.weekDays).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it('should start with all days closed (empty week)', () => {
    for (const d of component.weekDays) {
      expect(component.isDayClosed(d)).toBeTrue();
    }
  });

  it('should return empty slots for a closed day', () => {
    expect(component.getDaySlots(1)).toEqual([]);
  });

  // ── Opening a day ──

  it('should open a day with a default 09:00-18:00 slot', () => {
    component.openDay(1);
    expect(component.isDayClosed(1)).toBeFalse();
    const slots = component.getDaySlots(1);
    expect(slots.length).toBe(1);
    expect(slots[0].openTime).toBe('09:00');
    expect(slots[0].closeTime).toBe('18:00');
  });

  it('should not affect other days when opening one day', () => {
    component.openDay(1);
    expect(component.isDayClosed(2)).toBeTrue();
    expect(component.isDayClosed(7)).toBeTrue();
  });

  // ── Adding slots ──

  it('should add a slot that starts at the last slot close time', () => {
    component.openDay(1);
    component.addSlot(1);
    const slots = component.getDaySlots(1);
    expect(slots.length).toBe(2);
    expect(slots[1].openTime).toBe('18:00');
    expect(slots[1].closeTime).toBe('18:00');
  });

  it('should add a default slot when day has no slots', () => {
    component.addSlot(2);
    const slots = component.getDaySlots(2);
    expect(slots.length).toBe(1);
    expect(slots[0].openTime).toBe('09:00');
  });

  it('should add multiple slots sequentially', () => {
    component.openDay(3);
    component.addSlot(3);
    component.addSlot(3);
    expect(component.getDaySlots(3).length).toBe(3);
  });

  // ── Removing slots ──

  it('should remove a slot by index', () => {
    component.openDay(1);
    component.addSlot(1);
    expect(component.getDaySlots(1).length).toBe(2);

    component.removeSlot(1, 0);
    expect(component.getDaySlots(1).length).toBe(1);
  });

  it('should mark day as closed when last slot is removed', () => {
    component.openDay(1);
    component.removeSlot(1, 0);
    expect(component.isDayClosed(1)).toBeTrue();
  });

  it('should not affect other days when removing a slot', () => {
    component.openDay(1);
    component.openDay(2);
    component.removeSlot(1, 0);
    expect(component.isDayClosed(2)).toBeFalse();
  });

  // ── Updating slot times ──

  it('should update openTime of a specific slot', () => {
    component.openDay(1);
    component.updateSlotTime(1, 0, 'openTime', '10:00');
    expect(component.getDaySlots(1)[0].openTime).toBe('10:00');
  });

  it('should update closeTime of a specific slot', () => {
    component.openDay(1);
    component.updateSlotTime(1, 0, 'closeTime', '17:00');
    expect(component.getDaySlots(1)[0].closeTime).toBe('17:00');
  });

  it('should only update the targeted slot index', () => {
    component.openDay(1);
    component.addSlot(1);
    component.updateSlotTime(1, 0, 'openTime', '08:00');

    const slots = component.getDaySlots(1);
    expect(slots[0].openTime).toBe('08:00');
    expect(slots[1].openTime).toBe('18:00'); // unchanged
  });

  it('should not affect other days when updating a slot time', () => {
    component.openDay(1);
    component.openDay(2);
    component.updateSlotTime(1, 0, 'openTime', '11:00');

    expect(component.getDaySlots(2)[0].openTime).toBe('09:00'); // unchanged
  });

  // ── Save ──

  it('should call store.saveHours with all open slots', () => {
    component.openDay(1);
    component.openDay(3);
    component.addSlot(1);

    const storeSpy = spyOn(component.store, 'saveHours');
    component.onSave();

    expect(storeSpy).toHaveBeenCalled();
    const args = (storeSpy.calls.mostRecent().args[0] as any[]);
    // Day 1 has 2 slots, day 3 has 1 slot = 3 requests
    expect(args.length).toBe(3);
    expect(args[0].dayOfWeek).toBe(1);
    expect(args[2].dayOfWeek).toBe(3);
  });

  it('should send empty array when all days are closed', () => {
    const storeSpy = spyOn(component.store, 'saveHours');
    component.onSave();
    expect(storeSpy).toHaveBeenCalled();
    expect((storeSpy.calls.mostRecent().args[0] as any[]).length).toBe(0);
  });

  it('should include correct times in save requests', () => {
    component.openDay(5);
    component.updateSlotTime(5, 0, 'openTime', '08:30');
    component.updateSlotTime(5, 0, 'closeTime', '16:30');

    const storeSpy = spyOn(component.store, 'saveHours');
    component.onSave();

    const req = (storeSpy.calls.mostRecent().args[0] as any[])[0];
    expect(req.dayOfWeek).toBe(5);
    expect(req.openTime).toBe('08:30');
    expect(req.closeTime).toBe('16:30');
  });

  // ── Sync from store data ──

  it('should sync week from store hours data', () => {
    // Simulate store returning hours by calling the private method via effect trigger
    // We test by directly invoking the component method
    (component as any).syncFromStoreData([
      { id: 1, dayOfWeek: 1, openTime: '09:00', closeTime: '12:00' },
      { id: 2, dayOfWeek: 1, openTime: '14:00', closeTime: '18:00' },
      { id: 3, dayOfWeek: 5, openTime: '10:00', closeTime: '16:00' },
    ]);

    expect(component.getDaySlots(1).length).toBe(2);
    expect(component.getDaySlots(1)[0].openTime).toBe('09:00');
    expect(component.getDaySlots(1)[1].openTime).toBe('14:00');
    expect(component.getDaySlots(5).length).toBe(1);
    expect(component.isDayClosed(2)).toBeTrue();
    expect(component.isDayClosed(7)).toBeTrue();
  });

  it('should clear previous slots when syncing new data', () => {
    component.openDay(1);
    component.addSlot(1);
    component.addSlot(1);
    expect(component.getDaySlots(1).length).toBe(3);

    (component as any).syncFromStoreData([
      { id: 1, dayOfWeek: 1, openTime: '09:00', closeTime: '12:00' },
    ]);
    expect(component.getDaySlots(1).length).toBe(1);
  });
});
