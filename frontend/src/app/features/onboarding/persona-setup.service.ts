import { Injectable, inject } from '@angular/core';
import { Observable, catchError, concatMap, defer, from, map, of, toArray } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';
import { CategoriesService } from '../categories/services/categories.service';
import { CaresService } from '../cares/services/cares.service';
import { Category } from '../categories/models/categories.model';
import { CareStatus } from '../cares/models/cares.model';
import { Persona, PersonaCategoryTemplate } from './personas';

export interface PersonaSetupResult {
  /** Number of categories successfully created */
  categoriesCreated: number;
  /** Number of cares successfully created */
  caresCreated: number;
  /** Number of failed individual operations (categories or cares) */
  failures: number;
}

interface CategoryApplyResult {
  category: Category | null;
  caresCreated: number;
  careFailures: number;
}

@Injectable({ providedIn: 'root' })
export class PersonaSetupService {
  private readonly categoriesService = inject(CategoriesService);
  private readonly caresService = inject(CaresService);
  private readonly i18n = inject(TranslocoService);

  /**
   * Apply a persona by creating its categories and cares sequentially.
   *
   * Sequencing matters: each category's id is needed before its cares can be
   * posted. We use concatMap so the order is deterministic and the backend
   * isn't slammed with parallel writes during onboarding. Individual failures
   * are tolerated — they're counted in the result so the UI can warn the user
   * but the rest of the persona still gets applied.
   */
  apply(persona: Persona): Observable<PersonaSetupResult> {
    const initial: PersonaSetupResult = {
      categoriesCreated: 0,
      caresCreated: 0,
      failures: 0,
    };

    return from(persona.categories).pipe(
      concatMap((cat) => this.applyCategory(cat)),
      toArray(),
      map((perCat) =>
        perCat.reduce(
          (acc, r) => ({
            categoriesCreated: acc.categoriesCreated + (r.category ? 1 : 0),
            caresCreated: acc.caresCreated + r.caresCreated,
            failures: acc.failures + (r.category ? 0 : 1) + r.careFailures,
          }),
          initial
        )
      )
    );
  }

  private applyCategory(template: PersonaCategoryTemplate): Observable<CategoryApplyResult> {
    return defer(() =>
      this.categoriesService.createPro({
        name: this.i18n.translate(template.nameKey),
        description: this.i18n.translate(template.descKey),
      })
    ).pipe(
      concatMap<Category, Observable<CategoryApplyResult>>((category) =>
        from(template.cares).pipe(
          concatMap((care) =>
            defer(() =>
              this.caresService.create({
                name: this.i18n.translate(care.nameKey),
                description: this.i18n.translate(care.descKey),
                price: care.priceCents,
                duration: care.durationMinutes,
                status: CareStatus.ACTIVE,
                categoryId: category.id,
              })
            ).pipe(
              map(() => true),
              catchError(() => of(false))
            )
          ),
          toArray(),
          map((results) => ({
            category,
            caresCreated: results.filter((r) => r).length,
            careFailures: results.filter((r) => !r).length,
          }))
        )
      ),
      // Category creation failure: skip its cares and count it as one failure.
      catchError(() =>
        of<CategoryApplyResult>({
          category: null,
          caresCreated: 0,
          careFailures: template.cares.length,
        })
      )
    );
  }
}
