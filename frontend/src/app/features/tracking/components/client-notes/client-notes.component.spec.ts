import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { ClientNotesComponent } from './client-notes.component';

describe('ClientNotesComponent', () => {
  let fixture: ComponentFixture<ClientNotesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ClientNotesComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              tracking: {
                practitionerNotes: 'Notes de la praticienne',
                modifiedBy: 'Modifié par',
              },
              common: { save: 'Enregistrer', cancel: 'Annuler' },
            },
          },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(ClientNotesComponent);
  });

  it('creates', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('displays notes text when not editing', () => {
    fixture.componentRef.setInput('notes', 'Peau sèche, préfère les soins doux');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.notes-text')?.textContent?.trim()).toBe(
      'Peau sèche, préfère les soins doux',
    );
    expect(host.querySelector('textarea')).toBeFalsy();
  });

  it('shows a dash when notes are null', () => {
    fixture.componentRef.setInput('notes', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.notes-text')?.textContent?.trim()).toBe('—');
  });

  it('shows the edit button only when accessLevel is WRITE', () => {
    fixture.componentRef.setInput('notes', 'hello');
    fixture.componentRef.setInput('accessLevel', 'READ');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.edit-btn')).toBeFalsy();

    fixture.componentRef.setInput('accessLevel', 'WRITE');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.edit-btn')).toBeTruthy();
  });

  it('startEditing populates editValue from notes and toggles editing mode', () => {
    fixture.componentRef.setInput('notes', 'original');
    fixture.componentRef.setInput('accessLevel', 'WRITE');
    fixture.detectChanges();

    fixture.componentInstance.startEditing();
    expect(fixture.componentInstance.editValue).toBe('original');
    expect(fixture.componentInstance.editing()).toBeTrue();

    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('textarea')).toBeTruthy();
  });

  it('save() emits the edit value and exits editing mode', () => {
    fixture.componentRef.setInput('notes', 'n');
    fixture.componentRef.setInput('accessLevel', 'WRITE');
    fixture.detectChanges();

    const emitted: string[] = [];
    fixture.componentInstance.saveNotes.subscribe((v) => emitted.push(v));

    fixture.componentInstance.startEditing();
    fixture.componentInstance.editValue = 'updated content';
    fixture.componentInstance.save();

    expect(emitted).toEqual(['updated content']);
    expect(fixture.componentInstance.editing()).toBeFalse();
  });

  it('shows an audit line when updatedByName is set', () => {
    fixture.componentRef.setInput('notes', 'x');
    fixture.componentRef.setInput('updatedByName', 'Sophie');
    fixture.componentRef.setInput('updatedAt', '2026-03-15T10:00:00');
    fixture.detectChanges();

    const audit = fixture.nativeElement.querySelector('.audit-line') as HTMLElement;
    expect(audit).toBeTruthy();
    expect(audit.textContent).toContain('Sophie');
  });

  it('hides the audit line when updatedByName is null', () => {
    fixture.componentRef.setInput('notes', 'x');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.audit-line')).toBeFalsy();
  });
});
