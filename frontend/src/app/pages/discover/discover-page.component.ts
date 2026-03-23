import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';

@Component({
  selector: 'app-discover-page',
  standalone: true,
  imports: [TranslocoPipe, RouterLink],
  template: `
    <section class="flex flex-col items-center justify-center min-h-[60vh] px-6 text-center">
      <div class="mb-6">
        <span class="text-5xl">🔍</span>
      </div>
      <h1 class="text-2xl font-light tracking-wide text-neutral-800 mb-3">
        {{ 'discover.placeholder' | transloco }}
      </h1>
      <p class="text-neutral-500 font-light max-w-md mb-8">
        {{ message() }}
      </p>
      <a
        routerLink="/"
        class="text-sm text-rose-400 hover:text-rose-500 underline underline-offset-4 transition-colors duration-150"
      >
        {{ 'discover.backHome' | transloco }}
      </a>
    </section>
  `,
})
export class DiscoverPageComponent {
  private route = inject(ActivatedRoute);

  private params = toSignal(
    this.route.queryParamMap.pipe(
      map((params) => ({
        category: params.get('category'),
        q: params.get('q'),
      }))
    ),
    { initialValue: { category: null as string | null, q: null as string | null } }
  );

  message = computed(() => {
    const { category, q } = this.params();
    if (category) return `Découvrez les salons ${category}`;
    if (q) return `Résultats pour « ${q} »`;
    return 'Explorez tous les salons de beauté près de chez vous';
  });
}
