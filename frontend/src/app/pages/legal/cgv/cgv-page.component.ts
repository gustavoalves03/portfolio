import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';

@Component({
  selector: 'app-cgv-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './cgv-page.component.html',
})
export class CgvPageComponent {
  readonly updatedAt = '2026-05-17';
  readonly sections: ReadonlyArray<string> = ['placeholder'];
}
