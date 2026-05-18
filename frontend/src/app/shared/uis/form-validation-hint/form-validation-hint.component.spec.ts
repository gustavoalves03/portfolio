import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { FormValidationHintComponent } from './form-validation-hint.component';

describe('FormValidationHintComponent', () => {
  let fixture: ComponentFixture<FormValidationHintComponent>;
  let fb: FormBuilder;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        ReactiveFormsModule,
        FormValidationHintComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: { common: { form: { fillRequiredFields: 'Fill required', checkTheseFields: 'Check these fields:', field: { name: 'Name' } } } } },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fb = TestBed.inject(FormBuilder);
    fixture = TestBed.createComponent(FormValidationHintComponent);
  });

  function setForm(form: FormGroup) {
    fixture.componentRef.setInput('form', form);
    fixture.detectChanges();
    // Flush effects so the statusChanges subscription is wired up
    TestBed.flushEffects();
  }

  it('hides the hint when the form is pristine and untouched', () => {
    const form = fb.group({ name: ['', Validators.required] });
    setForm(form);
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.form-validation-hint');
    expect(hint).toBeNull();
  });

  it('hides the hint when the form is valid', () => {
    const form = fb.group({ name: ['Pretty', Validators.required] });
    form.markAllAsTouched();
    setForm(form);
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.form-validation-hint');
    expect(hint).toBeNull();
  });

  it('shows the hint when the form is invalid and touched', () => {
    const form = fb.group({ name: ['', Validators.required] });
    form.markAllAsTouched();
    setForm(form);
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.form-validation-hint');
    expect(hint).not.toBeNull();
  });

  // markAsDirty() alone does not emit statusChanges (only value changes do).
  // We combine markAsDirty() with updateValueAndValidity() to simulate what Angular's
  // ControlValueAccessor does on a real user keystroke: marks dirty AND triggers validation.
  // setForm() has already flushed effects so the statusChanges subscription is active.
  it('shows the hint when the form is invalid and dirty after interaction', () => {
    const form = fb.group({ name: ['', Validators.required] });
    setForm(form);
    // Simulate user clearing the field: Angular sets dirty + triggers status re-evaluation
    form.markAsDirty();
    form.updateValueAndValidity();
    fixture.detectChanges();
    const hint = (fixture.nativeElement as HTMLElement).querySelector('.form-validation-hint');
    expect(hint).not.toBeNull();
  });
});
