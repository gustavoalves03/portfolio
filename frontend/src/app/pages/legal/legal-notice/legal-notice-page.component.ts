import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';
import { LEGAL_LAST_UPDATED } from '../legal.constants';

@Component({
  selector: 'app-legal-notice-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './legal-notice-page.component.html',
  styleUrl: './legal-notice-page.component.scss',
})
export class LegalNoticePageComponent {
  private readonly titleService = inject(Title);
  private readonly metaService = inject(Meta);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly updatedAt = LEGAL_LAST_UPDATED;
  readonly sections: ReadonlyArray<string> = [
    'editeur', 'directeur', 'hebergeur', 'cdn', 'conception', 'credits', 'signalement',
  ];

  constructor() {
    this.transloco
      .selectTranslate('legal.notice.title')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((title) => {
        this.titleService.setTitle(`${title} · LuxPretty`);
        this.metaService.updateTag({ name: 'description', content: `${title} — LuxPretty` });
      });
  }
}
