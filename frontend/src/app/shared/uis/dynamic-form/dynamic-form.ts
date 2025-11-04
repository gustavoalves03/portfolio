import { Component, input, OnInit, inject } from '@angular/core';
import { FormGroup, ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { DynamicFormConfig, FormFieldConfig } from '../../models/form-field.model';

/**
 * Composant de formulaire dynamique
 *
 * Usage:
 * <dynamic-form [config]="formConfig" [formGroup]="myForm" />
 */
@Component({
  selector: 'dynamic-form',
  standalone: true,
  styleUrl: 'dynamic-form.scss',
  templateUrl: 'dynamic-form.html',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule
  ],
})
export class DynamicForm implements OnInit {
  private fb = inject(FormBuilder);

  // Configuration des champs
  config = input.required<DynamicFormConfig>();

  // FormGroup fourni par le parent
  formGroup = input.required<FormGroup>();

  ngOnInit(): void {
    this.buildForm();
  }

  private buildForm(): void {
    const group: any = {};

    this.config().rows.forEach(row => {
      row.fields.forEach(field => {
        const validators = this.getValidators(field);
        const defaultValue = this.getDefaultValue(field);
        group[field.name] = [{ value: defaultValue, disabled: field.disabled }, validators];
      });
    });

    // Patch les controls dans le FormGroup fourni
    Object.keys(group).forEach(key => {
      if (!this.formGroup().contains(key)) {
        this.formGroup().addControl(key, this.fb.control(group[key][0], group[key][1]));
      }
    });
  }

  private getValidators(field: FormFieldConfig) {
    const validators = [];

    if (field.required) {
      validators.push(Validators.required);
    }
    if (field.minLength) {
      validators.push(Validators.minLength(field.minLength));
    }
    if (field.maxLength) {
      validators.push(Validators.maxLength(field.maxLength));
    }
    if (field.min !== undefined) {
      validators.push(Validators.min(field.min));
    }
    if (field.max !== undefined) {
      validators.push(Validators.max(field.max));
    }
    if (field.pattern) {
      validators.push(Validators.pattern(field.pattern));
    }
    if (field.type === 'email') {
      validators.push(Validators.email);
    }

    return validators;
  }

  private getDefaultValue(field: FormFieldConfig): any {
    if (field.type === 'number') return 0;
    if (field.type === 'select' && field.options?.length) return field.options[0].value;
    return '';
  }

  getControl(fieldName: string): AbstractControl | null {
    return this.formGroup().get(fieldName);
  }

  hasError(fieldName: string): boolean {
    const control = this.getControl(fieldName);
    return !!(control?.invalid && control?.touched);
  }

  getErrorMessage(fieldName: string): string {
    const control = this.getControl(fieldName);
    if (!control?.errors) return '';

    if (control.errors['required']) {
      return 'Ce champ est requis';
    }
    if (control.errors['minlength']) {
      return `Minimum ${control.errors['minlength'].requiredLength} caractères`;
    }
    if (control.errors['maxlength']) {
      return `Maximum ${control.errors['maxlength'].requiredLength} caractères`;
    }
    if (control.errors['min']) {
      return `La valeur minimale est ${control.errors['min'].min}`;
    }
    if (control.errors['max']) {
      return `La valeur maximale est ${control.errors['max'].max}`;
    }
    if (control.errors['email']) {
      return 'Email invalide';
    }
    if (control.errors['pattern']) {
      return 'Format invalide';
    }

    return 'Erreur de validation';
  }

  getFieldWidth(field: FormFieldConfig): string {
    return field.width === 'half' ? 'half-width' : 'full-width';
  }
}
