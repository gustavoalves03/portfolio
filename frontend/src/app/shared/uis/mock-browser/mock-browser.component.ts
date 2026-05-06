import { Component, input } from '@angular/core';

/**
 * Decorative browser-window frame: rounded shell with three traffic-light
 * dots and a URL bar, projecting any content via <ng-content>. Purely
 * visual — no interactivity.
 */
@Component({
  selector: 'app-mock-browser',
  standalone: true,
  templateUrl: './mock-browser.component.html',
  styleUrl: './mock-browser.component.scss',
})
export class MockBrowserComponent {
  readonly url = input<string>('prettyface.app');
}
