/**
 * Configuration générique pour les champs de formulaire dynamiques
 */

export type FormFieldType = 'text' | 'textarea' | 'number' | 'select' | 'date' | 'email';

export interface FormFieldOption {
  label: string;
  value: any;
}

export interface FormFieldConfig {
  name: string;
  label: string;
  type: FormFieldType;
  placeholder?: string;
  icon?: string;
  suffix?: string;
  rows?: number; // Pour textarea
  step?: number; // Pour number
  min?: number; // Pour number
  max?: number; // Pour number
  options?: FormFieldOption[]; // Pour select
  required?: boolean;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  width?: 'full' | 'half'; // Largeur du champ
  disabled?: boolean;
  autocomplete?: string;
}

export interface FormRowConfig {
  fields: FormFieldConfig[];
}

export interface DynamicFormConfig {
  rows: FormRowConfig[];
}
