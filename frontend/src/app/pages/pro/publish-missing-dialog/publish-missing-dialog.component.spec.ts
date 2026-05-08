import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import {
  PublishMissingDialogComponent,
  PublishMissingDialogData,
  PublishMissingDialogResult,
} from './publish-missing-dialog.component';

describe('PublishMissingDialogComponent', () => {
  let fixture: ComponentFixture<PublishMissingDialogComponent>;
  let closeSpy: jasmine.Spy;

  function setup(missing: string[]) {
    closeSpy = jasmine.createSpy('close');
    const data: PublishMissingDialogData = { missing };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close: closeSpy } },
      ],
      imports: [PublishMissingDialogComponent, TranslocoTestingModule.forRoot({
        langs: { en: {} },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      })],
    });
    fixture = TestBed.createComponent(PublishMissingDialogComponent);
    fixture.detectChanges();
  }

  it('renders one item per missing key', () => {
    setup(['hasContact', 'hasLogo']);
    const items = fixture.nativeElement.querySelectorAll('[data-testid^="missing-item-"]');
    expect(items.length).toBe(2);
  });

  it('clicking goTo closes the dialog with the corresponding pro page route', () => {
    setup(['hasContact']);
    const goto = fixture.nativeElement.querySelector('[data-testid="goto"]');
    goto.click();
    expect(closeSpy).toHaveBeenCalledWith({ action: 'goTo', route: '/pro/salon' });
  });

  it('clicking close emits a cancel result', () => {
    setup(['name']);
    fixture.nativeElement.querySelector('[data-testid="close"]').click();
    expect(closeSpy).toHaveBeenCalledWith({ action: 'cancel' });
  });

  it('ignores unknown missing keys (no goto button rendered)', () => {
    setup(['unknownKey']);
    const items = fixture.nativeElement.querySelectorAll('[data-testid^="missing-item-"]');
    // The item should still render but without a goTo (or be skipped entirely — pick one)
    // For this implementation: render the item but disable/hide the goto button.
    expect(items.length).toBe(1);
    const goto = items[0].querySelector('[data-testid="goto"]');
    // Either null (not rendered) or disabled — accept either:
    expect(goto === null || goto.disabled).toBeTrue();
  });
});
