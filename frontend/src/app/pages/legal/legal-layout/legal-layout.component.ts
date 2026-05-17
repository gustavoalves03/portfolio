import { Component, computed, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

@Component({
  selector: 'app-legal-layout',
  standalone: true,
  imports: [DatePipe, TranslocoPipe],
  templateUrl: './legal-layout.component.html',
  styleUrl: './legal-layout.component.scss',
})
export class LegalLayoutComponent {
  private readonly transloco = inject(TranslocoService);

  readonly titleKey = input.required<string>();
  readonly updatedAt = input.required<string>();
  readonly activeLocale = computed(() => (this.transloco.getActiveLang() === 'fr' ? 'fr' : 'en'));
}
