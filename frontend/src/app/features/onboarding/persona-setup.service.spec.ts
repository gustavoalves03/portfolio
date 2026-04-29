import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule, TranslocoService } from '@jsverse/transloco';
import { Observable, of, throwError } from 'rxjs';
import { PersonaSetupService } from './persona-setup.service';
import { Persona } from './personas';
import { CategoriesService } from '../categories/services/categories.service';
import { CaresService } from '../cares/services/cares.service';
import { Category } from '../categories/models/categories.model';
import { Care, CareStatus, CreateCareRequest } from '../cares/models/cares.model';

describe('PersonaSetupService', () => {
  let service: PersonaSetupService;
  let categoriesService: jasmine.SpyObj<CategoriesService>;
  let caresService: jasmine.SpyObj<CaresService>;

  const facePersona: Persona = {
    key: 'face',
    icon: 'face',
    categories: [
      {
        nameKey: 'cat.a.name',
        descKey: 'cat.a.desc',
        cares: [
          { nameKey: 'cat.a.c1.name', descKey: 'cat.a.c1.desc', priceCents: 100, durationMinutes: 10 },
          { nameKey: 'cat.a.c2.name', descKey: 'cat.a.c2.desc', priceCents: 200, durationMinutes: 20 },
        ],
      },
      {
        nameKey: 'cat.b.name',
        descKey: 'cat.b.desc',
        cares: [
          { nameKey: 'cat.b.c1.name', descKey: 'cat.b.c1.desc', priceCents: 300, durationMinutes: 30 },
        ],
      },
    ],
  };

  function makeCategory(id: number, name: string): Category {
    return { id, name, description: null };
  }

  function makeCare(id: number): Care {
    return {
      id,
      name: 'mock',
      price: 0,
      description: '',
      duration: 0,
      status: CareStatus.ACTIVE,
      category: makeCategory(0, ''),
    };
  }

  beforeEach(() => {
    categoriesService = jasmine.createSpyObj<CategoriesService>('CategoriesService', ['createPro']);
    caresService = jasmine.createSpyObj<CaresService>('CaresService', ['create']);

    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        PersonaSetupService,
        { provide: CategoriesService, useValue: categoriesService },
        { provide: CaresService, useValue: caresService },
      ],
    });

    service = TestBed.inject(PersonaSetupService);
    // Make translate a passthrough so the spy assertions match the i18n keys.
    spyOn(TestBed.inject(TranslocoService), 'translate').and.callFake(((k: unknown) => k as string) as never);
  });

  it('creates each category then its cares (deterministic order)', (done) => {
    let categoryIdCounter = 0;
    let careIdCounter = 0;
    categoriesService.createPro.and.callFake((req) =>
      of(makeCategory(++categoryIdCounter, req.name))
    );
    caresService.create.and.callFake(() => of(makeCare(++careIdCounter)));

    service.apply(facePersona).subscribe((result) => {
      expect(result.categoriesCreated).toBe(2);
      expect(result.caresCreated).toBe(3);
      expect(result.failures).toBe(0);
      // Categories created in declared order
      const catCalls = categoriesService.createPro.calls.allArgs() as Array<[{ name: string }]>;
      expect(catCalls.map((args) => args[0].name)).toEqual(['cat.a.name', 'cat.b.name']);
      // Cares carry the right categoryId
      const careCalls = caresService.create.calls.allArgs() as Array<[CreateCareRequest]>;
      expect(careCalls.map((args) => args[0].categoryId)).toEqual([1, 1, 2]);
      done();
    });
  });

  it('passes care fields through (price, duration, status=ACTIVE)', (done) => {
    categoriesService.createPro.and.returnValue(of(makeCategory(1, 'A')));
    caresService.create.and.returnValue(of(makeCare(1)));

    const single: Persona = {
      key: 'face',
      icon: 'face',
      categories: [
        {
          nameKey: 'cat.a.name',
          descKey: 'cat.a.desc',
          cares: [
            { nameKey: 'c.name', descKey: 'c.desc', priceCents: 4500, durationMinutes: 30 },
          ],
        },
      ],
    };

    service.apply(single).subscribe(() => {
      const req = caresService.create.calls.mostRecent().args[0];
      expect(req.price).toBe(4500);
      expect(req.duration).toBe(30);
      expect(req.status).toBe(CareStatus.ACTIVE);
      expect(req.name).toBe('c.name'); // passthrough translate
      done();
    });
  });

  it('partial failure: one care fails, rest succeed', (done) => {
    categoriesService.createPro.and.callFake((req) => of(makeCategory(req.name === 'cat.a.name' ? 1 : 2, req.name)));
    let careCallCount = 0;
    caresService.create.and.callFake((): Observable<Care> => {
      careCallCount++;
      if (careCallCount === 2) return throwError(() => new Error('boom'));
      return of(makeCare(careCallCount));
    });

    service.apply(facePersona).subscribe((result) => {
      expect(result.categoriesCreated).toBe(2);
      expect(result.caresCreated).toBe(2);
      expect(result.failures).toBe(1);
      done();
    });
  });

  it('category creation failure: skips that category cares but continues', (done) => {
    let catCallCount = 0;
    categoriesService.createPro.and.callFake((req): Observable<Category> => {
      catCallCount++;
      if (catCallCount === 1) return throwError(() => new Error('boom'));
      return of(makeCategory(catCallCount, req.name));
    });
    caresService.create.and.returnValue(of(makeCare(1)));

    service.apply(facePersona).subscribe((result) => {
      expect(result.categoriesCreated).toBe(1); // second category OK
      expect(result.caresCreated).toBe(1); // only its single care
      // Failures = 1 (category A) + 2 (its cares not posted) = 3
      expect(result.failures).toBe(3);
      // First category's cares must NOT be attempted
      expect(caresService.create).toHaveBeenCalledTimes(1);
      done();
    });
  });

  it('total failure: all categories fail → 0 created, full count of failures', (done) => {
    categoriesService.createPro.and.returnValue(throwError(() => new Error('boom')));

    service.apply(facePersona).subscribe((result) => {
      expect(result.categoriesCreated).toBe(0);
      expect(result.caresCreated).toBe(0);
      // 2 categories failed + 3 cares not posted = 5
      expect(result.failures).toBe(5);
      expect(caresService.create).not.toHaveBeenCalled();
      done();
    });
  });

  it('persona with empty categories array → no calls, all zero', (done) => {
    const empty: Persona = { key: 'face', icon: 'face', categories: [] };

    service.apply(empty).subscribe((result) => {
      expect(result.categoriesCreated).toBe(0);
      expect(result.caresCreated).toBe(0);
      expect(result.failures).toBe(0);
      expect(categoriesService.createPro).not.toHaveBeenCalled();
      expect(caresService.create).not.toHaveBeenCalled();
      done();
    });
  });

  it('category with zero cares → category created, no care calls', (done) => {
    const zeroCares: Persona = {
      key: 'face',
      icon: 'face',
      categories: [{ nameKey: 'cat.a.name', descKey: 'cat.a.desc', cares: [] }],
    };
    categoriesService.createPro.and.returnValue(of(makeCategory(1, 'A')));

    service.apply(zeroCares).subscribe((result) => {
      expect(result.categoriesCreated).toBe(1);
      expect(result.caresCreated).toBe(0);
      expect(result.failures).toBe(0);
      expect(caresService.create).not.toHaveBeenCalled();
      done();
    });
  });
});
