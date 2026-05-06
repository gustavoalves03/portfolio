import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { OnboardingIndicatorSheetComponent, OnboardingSheetData } from './onboarding-indicator-sheet.component';

describe('OnboardingIndicatorSheetComponent', () => {
  let fixture: ComponentFixture<OnboardingIndicatorSheetComponent>;
  let component: OnboardingIndicatorSheetComponent;
  let dialogRef: jasmine.SpyObj<MatDialogRef<OnboardingIndicatorSheetComponent>>;

  function setup(data: OnboardingSheetData) {
    dialogRef = jasmine.createSpyObj<MatDialogRef<OnboardingIndicatorSheetComponent>>(
      'MatDialogRef',
      ['close']
    );
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
      imports: [
        OnboardingIndicatorSheetComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(OnboardingIndicatorSheetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('exposes steps, progress, and canPublish from injected data', () => {
    setup({
      steps: [
        { key: 'name', done: true, link: '/pro/salon', queryParams: null },
        { key: 'cares', done: false, link: '/pro/cares', queryParams: null },
        { key: 'openingHours', done: false, link: '/pro/planning', queryParams: null },
      ],
      progress: { done: 1, total: 3, nextKey: 'cares', percent: 33 },
      canPublish: false,
      slug: 'demo',
    });
    expect(component.steps.length).toBe(3);
    expect(component.progress.done).toBe(1);
    expect(component.canPublish).toBe(false);
  });

  it('closes the sheet with action="step" and the step key when a step is selected', () => {
    setup({
      steps: [
        { key: 'name', done: false, link: '/pro/salon', queryParams: null },
      ],
      progress: { done: 0, total: 1, nextKey: 'name', percent: 0 },
      canPublish: false,
      slug: 'demo',
    });
    component.onStep('name');
    expect(dialogRef.close).toHaveBeenCalledWith({ action: 'step', stepKey: 'name' });
  });

  it('closes the sheet with action="preview" when preview is selected', () => {
    setup({
      steps: [],
      progress: { done: 0, total: 0, nextKey: null, percent: 0 },
      canPublish: false,
      slug: 'demo',
    });
    component.onPreview();
    expect(dialogRef.close).toHaveBeenCalledWith({ action: 'preview' });
  });

  it('closes the sheet with action="publish" when publish is selected', () => {
    setup({
      steps: [],
      progress: { done: 3, total: 3, nextKey: null, percent: 100 },
      canPublish: true,
      slug: 'demo',
    });
    component.onPublish();
    expect(dialogRef.close).toHaveBeenCalledWith({ action: 'publish' });
  });

  it('closes the sheet with action="dashboard" when back-to-dashboard is selected', () => {
    setup({
      steps: [],
      progress: { done: 0, total: 0, nextKey: null, percent: 0 },
      canPublish: false,
      slug: 'demo',
    });
    component.onDashboard();
    expect(dialogRef.close).toHaveBeenCalledWith({ action: 'dashboard' });
  });
});
