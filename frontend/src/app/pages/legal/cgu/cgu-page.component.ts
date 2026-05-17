import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';

@Component({
  selector: 'app-cgu-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './cgu-page.component.html',
})
export class CguPageComponent {
  readonly updatedAt = '2026-05-17';
  readonly sections: ReadonlyArray<string> = ['placeholder'];
}
