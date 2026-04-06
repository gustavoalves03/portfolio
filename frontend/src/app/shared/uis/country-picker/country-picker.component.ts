import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { COUNTRIES, Country, POPULAR_COUNTRY_CODES } from './countries';

@Component({
  selector: 'app-country-picker',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatAutocompleteModule, TranslocoPipe],
  template: `
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>{{ 'pro.salon.addressCountry' | transloco }}</mat-label>
      <span matTextPrefix class="flag-prefix">{{ selectedFlag() }}&nbsp;</span>
      <input
        matInput
        [value]="displayText()"
        (input)="onInput($event)"
        [matAutocomplete]="auto"
        name="country"
      />
      <mat-autocomplete
        #auto="matAutocomplete"
        (optionSelected)="onOptionSelected($event)"
        autoActiveFirstOption
      >
        @for (country of filteredCountries(); track country.code) {
          <mat-option [value]="country.code">
            <span class="option-flag">{{ country.flag }}</span>
            <span class="option-name">{{ getName(country) }}</span>
            <span class="option-code">{{ country.code }}</span>
          </mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: `
    :host { display: block; }
    .full-width { width: 100%; }
    .flag-prefix { font-size: 1.25rem; line-height: 1; }
    .option-flag { font-size: 1.25rem; margin-right: 8px; vertical-align: middle; }
    .option-name { vertical-align: middle; }
    .option-code {
      margin-left: auto; padding-left: 12px;
      color: var(--mat-sys-on-surface-variant, #666);
      font-size: 0.8rem; font-weight: 500;
    }
    mat-option .mat-option-text,
    mat-option .mdc-list-item__primary-text {
      display: flex; align-items: center; width: 100%;
    }
  `,
})
export class CountryPickerComponent {
  private readonly transloco = inject(TranslocoService);

  readonly countryCode = input<string | null>(null);
  readonly countryCodeChange = output<string | null>();

  private readonly searchQuery = signal('');
  private readonly isSearching = signal(false);

  private readonly countryMap = new Map<string, Country>(
    COUNTRIES.map((c) => [c.code, c]),
  );

  protected readonly selectedCountry = computed(() => {
    const code = this.countryCode();
    return code ? this.countryMap.get(code.toUpperCase()) ?? null : null;
  });

  protected readonly selectedFlag = computed(() => {
    return this.selectedCountry()?.flag ?? '\u{1F30D}';
  });

  protected readonly displayText = computed(() => {
    if (this.isSearching()) {
      return this.searchQuery();
    }
    const country = this.selectedCountry();
    return country ? this.getName(country) : '';
  });

  protected readonly filteredCountries = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();

    let results: Country[];

    if (!query) {
      const popular = POPULAR_COUNTRY_CODES
        .map((code) => this.countryMap.get(code))
        .filter((c): c is Country => !!c);
      const popularSet = new Set(POPULAR_COUNTRY_CODES);
      const rest = COUNTRIES
        .filter((c) => !popularSet.has(c.code))
        .sort((a, b) => this.getName(a).localeCompare(this.getName(b)));
      results = [...popular, ...rest];
    } else {
      results = COUNTRIES.filter((c) =>
        c.name.toLowerCase().includes(query) ||
        c.nameEn.toLowerCase().includes(query) ||
        c.code.toLowerCase().includes(query),
      ).sort((a, b) => this.getName(a).localeCompare(this.getName(b)));
    }

    return results.slice(0, 30);
  });

  getName(country: Country): string {
    return this.transloco.getActiveLang() === 'en' ? country.nameEn : country.name;
  }

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.isSearching.set(true);
    this.searchQuery.set(value);
  }

  protected onOptionSelected(event: MatAutocompleteSelectedEvent): void {
    const code = event.option.value;
    this.isSearching.set(false);
    this.searchQuery.set('');
    this.countryCodeChange.emit(code);
  }
}
