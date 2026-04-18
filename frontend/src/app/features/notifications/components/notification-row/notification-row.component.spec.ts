import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { NotificationRowComponent } from './notification-row.component';
import { NotificationResponse } from '../../models/notification.model';

function sample(): NotificationResponse {
  return {
    id: 1, tenantSlug: 'test',
    type: 'BOOKING_CREATED' as any,
    category: 'INFO' as any,
    title: 'Nouveau RDV', message: 'Marie D. a réservé un soin',
    referenceId: 42, referenceType: 'BOOKING' as any,
    read: false, createdAt: '2026-04-18T14:30:00Z',
  } as NotificationResponse;
}

describe('NotificationRowComponent', () => {
  let fixture: ComponentFixture<NotificationRowComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        NotificationRowComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { availableLangs: ['fr'], defaultLang: 'fr' },
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationRowComponent);
  });

  it('renders title, message and formatted createdAt', () => {
    fixture.componentRef.setInput('notification', sample());
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.title')?.textContent?.trim()).toBe('Nouveau RDV');
    expect(el.querySelector('.message')?.textContent?.trim()).toBe('Marie D. a réservé un soin');
    expect(el.querySelector('.time')?.textContent?.trim()).toContain('18/04/2026');
  });

  it('emits rowClick on card click', () => {
    fixture.componentRef.setInput('notification', sample());
    const emitted: unknown[] = [];
    fixture.componentInstance.rowClick.subscribe(() => emitted.push(true));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.notif-card') as HTMLElement).click();
    expect(emitted.length).toBe(1);
  });
});
