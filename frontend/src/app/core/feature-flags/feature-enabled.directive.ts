import {
  Directive,
  Input,
  TemplateRef,
  ViewContainerRef,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FeatureFlagsStore } from './feature-flags.store';
import { FeatureKey } from './feature-key';

@Directive({
  selector: '[lpFeatureEnabled]',
  standalone: true,
})
export class FeatureEnabledDirective {
  private readonly tpl = inject(TemplateRef<unknown>);
  private readonly vcr = inject(ViewContainerRef);
  private readonly store = inject(FeatureFlagsStore);

  private readonly key = signal<FeatureKey | null>(null);
  private rendered = false;

  @Input() set lpFeatureEnabled(key: FeatureKey) {
    this.key.set(key);
  }

  constructor() {
    effect(() => {
      const k = this.key();
      if (!k) return;
      const enabled = this.store.isEnabled(k)();
      if (enabled && !this.rendered) {
        this.vcr.createEmbeddedView(this.tpl);
        this.rendered = true;
      } else if (!enabled && this.rendered) {
        this.vcr.clear();
        this.rendered = false;
      }
    });
  }
}
