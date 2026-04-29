import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { of } from 'rxjs';

import { ProBookingsComponent } from './pro-bookings.component';
import { BookingsService } from '../../features/bookings/services/bookings.service';
import {
  CareBookingDetailed,
  CareBookingStatus,
} from '../../features/bookings/models/bookings.model';

function booking(
  id: number,
  status: CareBookingStatus,
  price: number,
  duration: number,
  quantity = 1,
): CareBookingDetailed {
  return {
    id,
    user: { id: 1, name: 'Test', email: 't@e.st' },
    care: {
      id: 1,
      name: 'Soin',
      description: '',
      price,
      duration,
      images: [],
    } as any,
    quantity,
    appointmentDate: '2026-05-04',
    appointmentTime: '10:00:00',
    status,
    createdAt: '2026-05-01T12:00:00Z',
    employeeId: null,
    employeeName: null,
    salonClientId: null,
    salonClientName: null,
  };
}

describe('ProBookingsComponent — KPI computations', () => {
  let component: any; // protected fields accessed via runtime
  let bookingsService: jasmine.SpyObj<BookingsService>;

  beforeEach(async () => {
    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', ['listDetailed']);
    bookingsService.listDetailed.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 0, size: 0, number: 0 } as any)
    );

    await TestBed.configureTestingModule({
      imports: [
        ProBookingsComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        provideTranslocoLocale({
          defaultLocale: 'fr-FR',
          langToLocaleMapping: { fr: 'fr-FR' },
        }),
        { provide: BookingsService, useValue: bookingsService },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(null) }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProBookingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ── totalBookings (excludes CANCELLED, keeps NO_SHOW) ──

  it('totalBookings is 0 for an empty list', () => {
    component.bookings.set([]);
    expect(component.totalBookings()).toBe(0);
  });

  it('totalBookings counts CONFIRMED + PENDING + NO_SHOW, but not CANCELLED', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CONFIRMED, 5000, 60),
      booking(2, CareBookingStatus.PENDING, 5000, 60),
      booking(3, CareBookingStatus.NO_SHOW, 5000, 60),
      booking(4, CareBookingStatus.CANCELLED, 5000, 60),
    ]);
    expect(component.totalBookings()).toBe(3);
  });

  it('totalBookings is 0 when every booking is cancelled', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CANCELLED, 5000, 60),
      booking(2, CareBookingStatus.CANCELLED, 5000, 60),
    ]);
    expect(component.totalBookings()).toBe(0);
  });

  // ── estimatedRevenue (excludes CANCELLED + NO_SHOW, multiplies by quantity) ──

  it('estimatedRevenue formats 0 for an empty list', () => {
    component.bookings.set([]);
    expect(component.estimatedRevenue()).toBe('0,00 €');
  });

  it('estimatedRevenue excludes CANCELLED', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CONFIRMED, 5000, 60), // counted
      booking(2, CareBookingStatus.CANCELLED, 5000, 60), // skipped
    ]);
    // 5000 cents = 50,00 €
    expect(component.estimatedRevenue()).toBe('50,00 €');
  });

  it('estimatedRevenue excludes NO_SHOW (no payment expected)', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CONFIRMED, 5000, 60),
      booking(2, CareBookingStatus.NO_SHOW, 5000, 60),
    ]);
    expect(component.estimatedRevenue()).toBe('50,00 €');
  });

  it('estimatedRevenue multiplies care.price by quantity (matches backend convention)', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CONFIRMED, 5000, 60, 3), // 3 × 50 = 150
    ]);
    expect(component.estimatedRevenue()).toBe('150,00 €');
  });

  it('estimatedRevenue covers PENDING (it is expected revenue, not just paid)', () => {
    component.bookings.set([booking(1, CareBookingStatus.PENDING, 4500, 30)]);
    expect(component.estimatedRevenue()).toBe('45,00 €');
  });

  it('estimatedRevenue mixed: only CONFIRMED + PENDING contribute', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CONFIRMED, 5000, 60, 1), //  50,00
      booking(2, CareBookingStatus.PENDING, 4000, 30, 2),   //  80,00
      booking(3, CareBookingStatus.CANCELLED, 9000, 60, 1), //   skipped
      booking(4, CareBookingStatus.NO_SHOW, 9000, 60, 1),   //   skipped
    ]);
    // 130,00 €
    expect(component.estimatedRevenue()).toBe('130,00 €');
  });

  it('estimatedRevenue is 0 when every booking is cancelled or no-show', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CANCELLED, 5000, 60),
      booking(2, CareBookingStatus.NO_SHOW, 5000, 60),
    ]);
    expect(component.estimatedRevenue()).toBe('0,00 €');
  });

  // ── occupiedTime (excludes CANCELLED only — NO_SHOW slot was held) ──

  it('occupiedTime returns "0min" for an empty list', () => {
    component.bookings.set([]);
    expect(component.occupiedTime()).toBe('0min');
  });

  it('occupiedTime excludes CANCELLED only (NO_SHOW kept the slot)', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CONFIRMED, 5000, 60),  // 60 min
      booking(2, CareBookingStatus.NO_SHOW, 5000, 30),    // 30 min — still kept
      booking(3, CareBookingStatus.CANCELLED, 5000, 90),  //   skipped
    ]);
    // 90 min → 1h30
    expect(component.occupiedTime()).toBe('1h30');
  });

  it('occupiedTime formats below 60 min as "Xmin"', () => {
    component.bookings.set([booking(1, CareBookingStatus.CONFIRMED, 5000, 45)]);
    expect(component.occupiedTime()).toBe('45min');
  });

  it('occupiedTime formats whole hours as "Xh" without trailing 00', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CONFIRMED, 5000, 60),
      booking(2, CareBookingStatus.CONFIRMED, 5000, 60),
    ]);
    expect(component.occupiedTime()).toBe('2h');
  });

  it('occupiedTime formats hours+minutes as "XhYY"', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CONFIRMED, 5000, 75),
      booking(2, CareBookingStatus.CONFIRMED, 5000, 60),
    ]);
    expect(component.occupiedTime()).toBe('2h15');
  });

  it('occupiedTime is 0min when every booking is cancelled', () => {
    component.bookings.set([
      booking(1, CareBookingStatus.CANCELLED, 5000, 60),
      booking(2, CareBookingStatus.CANCELLED, 5000, 30),
    ]);
    expect(component.occupiedTime()).toBe('0min');
  });

  // ── reactivity sanity ──

  it('updating bookings() triggers all KPI computations', () => {
    component.bookings.set([booking(1, CareBookingStatus.CONFIRMED, 5000, 60)]);
    expect(component.totalBookings()).toBe(1);
    expect(component.estimatedRevenue()).toBe('50,00 €');
    expect(component.occupiedTime()).toBe('1h');

    // Cancel that booking — KPIs must reset
    component.bookings.update((list: CareBookingDetailed[]) =>
      list.map((b) => ({ ...b, status: CareBookingStatus.CANCELLED }))
    );
    expect(component.totalBookings()).toBe(0);
    expect(component.estimatedRevenue()).toBe('0,00 €');
    expect(component.occupiedTime()).toBe('0min');
  });
  
  // ─────────────────────────────────────────────────────────────
  // Adversarial: large lists, rapid mutations, edge data
  // ─────────────────────────────────────────────────────────────

  describe('adversarial', () => {
    it('rapid status flips settle to the last value', () => {
      component.bookings.set([booking(1, CareBookingStatus.CONFIRMED, 5000, 60)]);
      for (let i = 0; i < 20; i++) {
        component.bookings.update((list: CareBookingDetailed[]) =>
          list.map((x: CareBookingDetailed) => ({
            ...x,
            status: i % 2 === 0 ? CareBookingStatus.CANCELLED : CareBookingStatus.CONFIRMED,
          }))
        );
      }
      // i=19 → odd → CONFIRMED
      expect(component.estimatedRevenue()).toBe('50,00 €');
      expect(component.totalBookings()).toBe(1);
    });

    it('quantity = 0 (corrupt data) contributes zero revenue without crashing', () => {
      component.bookings.set([booking(1, CareBookingStatus.CONFIRMED, 5000, 60, 0)]);
      expect(component.estimatedRevenue()).toBe('0,00 €');
      expect(component.totalBookings()).toBe(1);
    });

    it('rapid replacements: setting bookings() 100 times settles to the final value', () => {
      for (let i = 0; i < 100; i++) {
        component.bookings.set([
          booking(1, CareBookingStatus.CONFIRMED, (i + 1) * 100, 30),
        ]);
      }
      // Final price = 100 × 100 cents = 100 €
      expect(component.estimatedRevenue()).toBe('100,00 €');
    });

    it('alternating set/empty: KPIs follow without lag', () => {
      const sample = [booking(1, CareBookingStatus.CONFIRMED, 5000, 60)];
      for (let i = 0; i < 10; i++) {
        component.bookings.set(i % 2 === 0 ? sample : []);
      }
      // i=9 odd → empty
      expect(component.totalBookings()).toBe(0);
      expect(component.estimatedRevenue()).toBe('0,00 €');
    });

    it('large list (200 mixed bookings) is computed correctly', () => {
      const list: CareBookingDetailed[] = [];
      for (let i = 0; i < 200; i++) {
        const status = [
          CareBookingStatus.CONFIRMED,
          CareBookingStatus.PENDING,
          CareBookingStatus.CANCELLED,
          CareBookingStatus.NO_SHOW,
        ][i % 4];
        list.push(booking(i + 1, status, 5000, 30));
      }
      component.bookings.set(list);
      // 50 CONFIRMED + 50 PENDING + 50 NO_SHOW = 150 non-cancelled
      expect(component.totalBookings()).toBe(150);
      // 100 (CONFIRMED+PENDING) × 5000 cents = 500 000 cents = 5 000 €
      // fr-FR Intl inserts a thin no-break space (U+202F) as the thousands separator,
      // so we just check the components separately to avoid a brittle Unicode pin.
      const rev = component.estimatedRevenue();
      expect(rev).toContain('5');
      expect(rev).toContain('000,00');
      expect(rev).toContain('€');
    });
  });
});
