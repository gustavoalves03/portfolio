import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { VisitCardComponent } from './visit-card.component';
import { VisitRecordResponse } from '../../tracking.model';

function makeVisit(overrides: Partial<VisitRecordResponse> = {}): VisitRecordResponse {
  return {
    id: 1,
    clientProfileId: 100,
    bookingId: 500,
    careId: 7,
    careName: 'Soin visage',
    visitDate: '2026-03-15T10:00:00',
    practitionerNotes: null,
    productsUsed: null,
    satisfactionScore: null,
    satisfactionComment: null,
    createdAt: '2026-03-15T10:00:00',
    updatedAt: null,
    updatedByName: 'Sophie',
    photos: [],
    ...overrides,
  };
}

describe('VisitCardComponent', () => {
  let fixture: ComponentFixture<VisitCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VisitCardComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
    fixture = TestBed.createComponent(VisitCardComponent);
  });

  it('creates with required visit input', () => {
    fixture.componentRef.setInput('visit', makeVisit());
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the care name', () => {
    fixture.componentRef.setInput('visit', makeVisit({ careName: 'Soin hydratant' }));
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.care-name')?.textContent?.trim()).toBe('Soin hydratant');
  });

  it('renders practitioner notes in italics when present', () => {
    fixture.componentRef.setInput(
      'visit',
      makeVisit({ practitionerNotes: 'Peau bien hydratée' }),
    );
    fixture.detectChanges();
    const notesEl = fixture.nativeElement.querySelector('.visit-notes') as HTMLElement;
    expect(notesEl).toBeTruthy();
    expect(notesEl.textContent).toContain('Peau bien hydratée');
  });

  it('omits notes section when practitionerNotes is null', () => {
    fixture.componentRef.setInput('visit', makeVisit({ practitionerNotes: null }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.visit-notes')).toBeFalsy();
  });

  it('renders photos using apiBaseUrl prefix', () => {
    fixture.componentRef.setInput(
      'visit',
      makeVisit({
        photos: [
          { id: 10, photoType: 'BEFORE', imageUrl: '/uploads/a.jpg', imageOrder: 0, uploadedByName: null },
          { id: 11, photoType: 'AFTER', imageUrl: '/uploads/b.jpg', imageOrder: 1, uploadedByName: null },
        ],
      }),
    );
    fixture.componentRef.setInput('apiBaseUrl', 'http://localhost:8080');
    fixture.detectChanges();

    const imgs = fixture.nativeElement.querySelectorAll('img.photo-thumb') as NodeListOf<HTMLImageElement>;
    expect(imgs.length).toBe(2);
    expect(imgs[0].getAttribute('src')).toBe('http://localhost:8080/uploads/a.jpg');
    expect(imgs[0].getAttribute('alt')).toBe('BEFORE');
    expect(imgs[1].getAttribute('alt')).toBe('AFTER');
  });

  it('handles empty photos array gracefully', () => {
    fixture.componentRef.setInput('visit', makeVisit({ photos: [] }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.visit-photos')).toBeFalsy();
  });
});
