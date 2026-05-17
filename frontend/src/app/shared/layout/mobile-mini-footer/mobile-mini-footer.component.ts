import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LpLogoComponent } from '../../uis/lp-logo/lp-logo.component';

@Component({
  selector: 'app-mobile-mini-footer',
  standalone: true,
  imports: [RouterLink, TranslocoPipe, LpLogoComponent],
  templateUrl: './mobile-mini-footer.component.html',
  styleUrl: './mobile-mini-footer.component.scss',
})
export class MobileMiniFooterComponent {}
