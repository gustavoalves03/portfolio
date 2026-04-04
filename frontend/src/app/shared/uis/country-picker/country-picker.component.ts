import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { COUNTRIES, Country, POPULAR_COUNTRY_CODES } from './countries';

@Component({
  selector: 'app-country-picker',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatAutocompleteModule, TranslocoPipe],
  template: `
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>{{ 'pro.salon.addressCountry' | transloco }}</mat-label>
      <span matTextPrefix class="flag-prefix">{{ selectedFlag() }}</span>
      <input
        matInput
        [ngModel]="inputText()"
        (ngModelChange)="onInputChange($event)"
        [matAutocomplete]="auto"
        (focus)="onFocus()"
        (blur)="onBlur()"
        name="country"
      />
      <mat-autocomplete
        #auto="matAutocomplete"
        (optionSelected)="onOptionSelected($event)"
        [displayWith]="displayFn"
        autoActiveFirstOption
      >
        @for (country of filteredCountries(); track country.code) {
          <mat-option [value]="country.code">
            <span class="option-flag">{{ country.flag }}</span>
            <span class="option-name">{{ isEnglish() ? country.nameEn : country.name }}</span>
            <span class="option-code">{{ country.code }}</span>
          </mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: `
    :host {
      display: block;
    }
    .full-width {
      width: 100%;
    }
    .flag-prefix {
      font-size: 1.25rem;
      line-height: 1;
      margin-right: 8px;
    }
    .option-flag {
      font-size: 1.25rem;
      margin-right: 8px;
      vertical-align: middle;
    }
    .option-name {
      vertical-align: middle;
    }
    .option-code {
      margin-left: auto;
      padding-left: 12px;
      color: var(--mat-sys-on-surface-variant, #666);
      font-size: 0.8rem;
      font-weight: 500;
    }
    mat-option .mat-option-text,
    mat-option .mdc-list-item__primary-text {
      display: flex;
      align-items: center;
      width: 100%;
    }
  `,
})
export class CountryPickerComponent {
  private readonly transloco = inject(TranslocoService);

  /** ISO country code input (two-way binding support) */
  countryCode = input<string | null>(null);

  /** Emits the selected ISO country code */
  countryCodeChange = output<string | null>();

  /** Current search/display text in the input */
  protected inputText = signal('');

  /** Whether input is focused */
  private focused = signal(false);

  /** Country map for quick lookup */
  private readonly countryMap = new Map<string, Country>(
    COUNTRIES.map((c) => [c.code, c]),
  );

  /** Whether active language is English */
  protected isEnglish = computed(() => this.transloco.getActiveLang() === 'en');

  /** Currently selected country object */
  protected selectedCountry = computed(() => {
    const code = this.countryCode();
    return code ? this.countryMap.get(code.toUpperCase()) ?? null : null;
  });

  /** Flag of the selected country, or a globe when nothing selected */
  protected selectedFlag = computed(() => {
    return this.selectedCountry()?.flag ?? '\u{1F30D}';
  });

  /** Filtered and sorted countries for the dropdown */
  protected filteredCountries = computed(() => {
    const query = this.inputText().toLowerCase().trim();
    const lang = this.isEnglish();

    let results: Country[];

    if (!query) {
      // Show popular countries first, then the rest alphabetically
      const popular = POPULAR_COUNTRY_CODES
        .map((code) => this.countryMap.get(code))
        .filter((c): c is Country => !!c);
      const popularSet = new Set(POPULAR_COUNTRY_CODES);
      const rest = COUNTRIES
        .filter((c) => !popularSet.has(c.code))
        .sort((a, b) => {
          const nameA = lang ? a.nameEn : a.name;
          const nameB = lang ? b.nameEn : b.name;
          return nameA.localeCompare(nameB);
        });
      results = [...popular, ...rest];
    } else {
      results = COUNTRIES.filter((c) => {
        return (
          c.name.toLowerCase().includes(query) ||
          c.nameEn.toLowerCase().includes(query) ||
          c.code.toLowerCase().includes(query)
        );
      }).sort((a, b) => {
        const nameA = lang ? a.nameEn : a.name;
        const nameB = lang ? b.nameEn : b.name;
        return nameA.localeCompare(nameB);
      });
    }

    return results.slice(0, 30);
  });

  /** Display function for the autocomplete — returns the country name */
  protected displayFn = (code: string): string => {
    if (!code) return '';
    const country = this.countryMap.get(code.toUpperCase());
    if (!country) return code;
    return this.transloco.getActiveLang() === 'en' ? country.nameEn : country.name;
  };

  constructor() {
    // Sync external countryCode input to internal inputText
    effect(() => {
      const country = this.selectedCountry();
      if (!this.focused()) {
        this.inputText.set(
          country
            ? this.isEnglish()
              ? country.nameEn
              : country.name
            : '',
        );
      }
    });
  }

  protected onInputChange(value: string): void {
    this.inputText.set(value);
  }

  protected onFocus(): void {
    this.focused.set(true);
    // Select all text on focus for easy replacement
    this.inputText.set('');
  }

  protected onBlur(): void {
    this.focused.set(false);
    // Restore display text if we have a selected country
    const country = this.selectedCountry();
    if (country) {
      this.inputText.set(this.isEnglish() ? country.nameEn : country.name);
    }
  }

  protected onOptionSelected(event: { option: { value: string } }): void {
    const code = event.option.value;
    const country = this.countryMap.get(code);
    if (country) {
      this.inputText.set(this.isEnglish() ? country.nameEn : country.name);
      this.countryCodeChange.emit(code);
    }
  }
}
