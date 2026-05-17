import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';
import { LEGAL_LAST_UPDATED } from '../legal.constants';

@Component({
  selector: 'app-cgv-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './cgv-page.component.html',
})
export class CgvPageComponent {
  readonly updatedAt = LEGAL_LAST_UPDATED;
  readonly sections: ReadonlyArray<string> = [
    'objet', 'b2b', 'service', 'prix', 'essai', 'duree', 'annulation',
    'defautPaiement', 'evolution', 'sla', 'responsabilite', 'donnees',
    'forceMajeure', 'droit',
  ];
}
