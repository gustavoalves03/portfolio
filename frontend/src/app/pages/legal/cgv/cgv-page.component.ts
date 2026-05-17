import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';
import { LEGAL_LAST_UPDATED } from '../legal.constants';

@Component({
  selector: 'app-cgv-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './cgv-page.component.html',
})
export class CgvPageComponent {
  private readonly titleService = inject(Title);
  private readonly metaService = inject(Meta);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly updatedAt = LEGAL_LAST_UPDATED;
  readonly sections: ReadonlyArray<string> = [
    'objet', 'b2b', 'service', 'prix', 'essai', 'duree', 'annulation',
    'defautPaiement', 'evolution', 'sla', 'responsabilite', 'donnees',
    'forceMajeure', 'droit',
  ];

  constructor() {
    this.transloco
      .selectTranslate('legal.cgv.title')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((title) => {
        this.titleService.setTitle(`${title} · LuxPretty`);
        this.metaService.updateTag({ name: 'description', content: `${title} — LuxPretty` });
      });
  }
}
