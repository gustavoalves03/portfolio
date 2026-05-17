import { Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-legal-layout',
  standalone: true,
  imports: [DatePipe, TranslocoPipe],
  templateUrl: './legal-layout.component.html',
  styleUrl: './legal-layout.component.scss',
})
export class LegalLayoutComponent {
  @Input({ required: true }) titleKey!: string;
  @Input({ required: true }) updatedAt!: string;
}
