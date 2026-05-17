import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { CookieBannerService } from './cookie-banner.service';

@Component({
  selector: 'app-cookie-banner',
  standalone: true,
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './cookie-banner.component.html',
  styleUrl: './cookie-banner.component.scss',
})
export class CookieBannerComponent {
  readonly service = inject(CookieBannerService);
}
