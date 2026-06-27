import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { FeatureFlagsStore } from './feature-flags.store';
import { FeatureKey } from './feature-key';

@Component({
  selector: 'lp-feature-locked',
  standalone: true,
  imports: [RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './feature-locked.component.html',
  styleUrls: ['./feature-locked.component.scss'],
})
export class FeatureLockedComponent {
  readonly feature = input.required<FeatureKey>();
  private readonly store = inject(FeatureFlagsStore);

  readonly enabled = computed(() => this.store.isEnabled(this.feature())());
  readonly upsellKey = computed(() => `features.locked.${this.feature().toLowerCase()}`);
}
