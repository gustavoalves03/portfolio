import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';
import { LEGAL_LAST_UPDATED } from '../legal.constants';

@Component({
  selector: 'app-cgu-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './cgu-page.component.html',
})
export class CguPageComponent {
  readonly updatedAt = LEGAL_LAST_UPDATED;
  readonly sections: ReadonlyArray<string> = [
    'objet', 'definitions', 'role', 'inscription', 'engagementsUser', 'engagementsPro',
    'contenus', 'pi', 'responsabilite', 'suspension', 'donnees', 'modification',
    'droit', 'contact',
  ];
}
