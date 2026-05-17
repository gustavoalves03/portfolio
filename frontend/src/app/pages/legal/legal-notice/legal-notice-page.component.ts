import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LegalLayoutComponent } from '../legal-layout/legal-layout.component';

@Component({
  selector: 'app-legal-notice-page',
  standalone: true,
  imports: [LegalLayoutComponent, TranslocoPipe],
  templateUrl: './legal-notice-page.component.html',
  styleUrl: './legal-notice-page.component.scss',
})
export class LegalNoticePageComponent {
  readonly updatedAt = '2026-05-17';
  readonly sections: ReadonlyArray<string> = ['placeholder'];
}
