import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type LpLogoVariant = 'default' | 'small' | 'with-tagline';

@Component({
  selector: 'app-lp-logo',
  standalone: true,
  templateUrl: './lp-logo.component.html',
  styleUrl: './lp-logo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LpLogoComponent {
  readonly variant = input<LpLogoVariant>('default');
  protected readonly showTagline = computed(() => this.variant() === 'with-tagline');
}
