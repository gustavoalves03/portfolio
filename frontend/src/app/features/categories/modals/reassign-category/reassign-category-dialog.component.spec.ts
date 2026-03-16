import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import {
  ReassignCategoryDialogComponent,
  ReassignCategoryDialogData,
} from './reassign-category-dialog.component';

const mockTranslations = {
  'pro.categories.reassign.title': 'Reassign services',
  'pro.categories.reassign.message':
    'This category has {{count}} service(s). Choose a target category.',
  'pro.categories.reassign.select': 'Target category',
  'pro.categories.reassign.confirm': 'Confirm deletion',
  'common.cancel': 'Cancel',
};

const mockData: ReassignCategoryDialogData = {
  categoryId: 1,
  categoryName: 'Visage',
  careCount: 5,
  availableCategories: [
    { id: 1, name: 'Visage', description: '' },
    { id: 2, name: 'Corps', description: '' },
    { id: 3, name: 'Ongles', description: '' },
  ],
};

describe('ReassignCategoryDialogComponent', () => {
  let component: ReassignCategoryDialogComponent;
  let fixture: ComponentFixture<ReassignCategoryDialogComponent>;
  let loader: HarnessLoader;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ReassignCategoryDialogComponent>>;

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [
        ReassignCategoryDialogComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: {
            defaultLang: 'en',
            availableLangs: ['en'],
          },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        { provide: MAT_DIALOG_DATA, useValue: mockData },
        { provide: MatDialogRef, useValue: dialogRefSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReassignCategoryDialogComponent);
    component = fixture.componentInstance;
    loader = TestbedHarnessEnvironment.loader(fixture);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display care count in message', () => {
    const content = fixture.nativeElement.querySelector('mat-dialog-content p');
    expect(content.textContent).toContain('5');
    expect(content.textContent).toContain('service(s)');
  });

  it('should list available categories excluding current', async () => {
    const select = await loader.getHarness(MatSelectHarness);
    await select.open();
    const options = await select.getOptions();
    expect(options.length).toBe(2);
    const texts = await Promise.all(options.map((o) => o.getText()));
    expect(texts).toEqual(['Corps', 'Ongles']);
    expect(texts).not.toContain('Visage');
  });

  it('should disable confirm button when no target selected', async () => {
    const confirmButton = await loader.getHarness(
      MatButtonHarness.with({ text: /Confirm deletion/ })
    );
    expect(await confirmButton.isDisabled()).toBeTrue();
  });

  it('should close with selectedTargetId on confirm', async () => {
    const select = await loader.getHarness(MatSelectHarness);
    await select.open();
    const options = await select.getOptions();
    await options[0].click();

    const confirmButton = await loader.getHarness(
      MatButtonHarness.with({ text: /Confirm deletion/ })
    );
    expect(await confirmButton.isDisabled()).toBeFalse();
    await confirmButton.click();

    expect(dialogRefSpy.close).toHaveBeenCalledWith(2);
  });

  it('should close without result on cancel', async () => {
    const cancelButton = await loader.getHarness(MatButtonHarness.with({ text: /Cancel/ }));
    await cancelButton.click();

    expect(dialogRefSpy.close).toHaveBeenCalledWith();
  });
});
