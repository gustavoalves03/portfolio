import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideZonelessChangeDetection, signal, computed } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { of } from 'rxjs';

import { CaresComponent } from './cares.component';
import { CaresStore } from './store/cares.store';
import { CategoriesStore } from '../categories/store/categories.store';
import { CaresService } from './services/cares.service';
import { Care, CareStatus } from './models/cares.model';
import { Category } from '../categories/models/categories.model';

const CATEGORY_COLORS = [
  '#f4e1d2',
  '#f9d5d3',
  '#dce8d2',
  '#d5e5f0',
  '#f0dde4',
  '#e8ddd0',
  '#d8e2dc',
  '#f0e6cc',
];

const mockCategories: Category[] = [
  { id: 1, name: 'Visage', description: 'Soins du visage' },
  { id: 2, name: 'Corps', description: 'Soins du corps' },
  { id: 3, name: 'Ongles', description: 'Manucure et pédicure' },
];

const mockCares: Care[] = [
  {
    id: 10,
    name: 'Soin hydratant',
    price: 60,
    description: 'Un soin hydratant pour le visage',
    duration: 45,
    status: CareStatus.ACTIVE,
    category: mockCategories[0],
  },
  {
    id: 11,
    name: 'Massage relaxant',
    price: 80,
    description: 'Massage complet du corps',
    duration: 60,
    status: CareStatus.ACTIVE,
    category: mockCategories[1],
  },
  {
    id: 12,
    name: 'Soin anti-age',
    price: 90,
    description: 'Traitement anti-age premium',
    duration: 75,
    status: CareStatus.INACTIVE,
    category: mockCategories[0],
  },
];

describe('CaresComponent', () => {
  let component: CaresComponent;
  let fixture: ComponentFixture<CaresComponent>;
  let mockCaresStore: any;
  let mockCategoriesStore: any;
  let mockCaresService: jasmine.SpyObj<CaresService>;
  let mockDialog: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    const caresSignal = signal<Care[]>(mockCares);

    mockCaresStore = {
      cares: caresSignal,
      availableCares: computed(() =>
        caresSignal().filter((c) => c.status === CareStatus.ACTIVE)
      ),
      isPending: signal(false),
      error: signal(''),
      getCares: jasmine.createSpy('getCares'),
      createCare: jasmine.createSpy('createCare'),
      updateCare: jasmine.createSpy('updateCare'),
      deleteCare: jasmine.createSpy('deleteCare'),
    };

    mockCategoriesStore = {
      categories: signal<Category[]>(mockCategories),
      isFulfilled: signal(true),
      getProCategories: jasmine.createSpy('getProCategories'),
      createProCategory: jasmine.createSpy('createProCategory'),
      updateProCategory: jasmine.createSpy('updateProCategory'),
      deleteProCategory: jasmine.createSpy('deleteProCategory'),
    };

    mockCaresService = jasmine.createSpyObj('CaresService', ['get']);
    mockCaresService.get.and.returnValue(of(mockCares[0]));

    mockDialog = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [
        CaresComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideTranslocoLocale({
          defaultLocale: 'en-US',
          langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' },
        }),
        { provide: CaresService, useValue: mockCaresService },
        { provide: MatDialog, useValue: mockDialog },
      ],
    })
      .overrideComponent(CaresComponent, {
        set: {
          providers: [
            { provide: CaresStore, useValue: mockCaresStore },
            { provide: CategoriesStore, useValue: mockCategoriesStore },
          ],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(CaresComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render category chips', () => {
    const chips = fixture.nativeElement.querySelectorAll('.category-chip');
    expect(chips.length).toBe(3);
    expect(chips[0].textContent.trim()).toBe('Visage');
    expect(chips[1].textContent.trim()).toBe('Corps');
    expect(chips[2].textContent.trim()).toBe('Ongles');
  });

  it('should toggle filter on chip click', () => {
    expect(component.selectedCategoryId()).toBeNull();

    // Click first chip to select
    component.onSelectCategory(1);
    expect(component.selectedCategoryId()).toBe(1);

    // Click same chip again to deselect
    component.onSelectCategory(1);
    expect(component.selectedCategoryId()).toBeNull();
  });

  it('should filter cares by selected category', () => {
    component.onSelectCategory(1);
    const filtered = component.filteredCares();
    expect(filtered.length).toBe(1);
    expect(filtered[0].name).toBe('Soin hydratant');
    expect(filtered.every((c) => c.category.id === 1)).toBeTrue();
  });

  it('should show all available cares when no category selected', () => {
    expect(component.selectedCategoryId()).toBeNull();
    const filtered = component.filteredCares();
    // Only ACTIVE cares (2 out of 3)
    expect(filtered.length).toBe(2);
    expect(filtered.map((c) => c.name)).toEqual(['Soin hydratant', 'Massage relaxant']);
  });

  it('should call getProCategories on init', () => {
    expect(mockCategoriesStore.getProCategories).toHaveBeenCalled();
  });

  it('should compute correct category color', () => {
    expect(component.getCategoryColor(0)).toBe(CATEGORY_COLORS[0]);
    expect(component.getCategoryColor(1)).toBe(CATEGORY_COLORS[1]);
    expect(component.getCategoryColor(7)).toBe(CATEGORY_COLORS[7]);
    // Wraps around: 8 % 8 = 0
    expect(component.getCategoryColor(8)).toBe(CATEGORY_COLORS[0]);
    expect(component.getCategoryColor(10)).toBe(CATEGORY_COLORS[2]);
  });
});

// ─────────────────────────────────────────────────────────────
// Auto-open from query param (?openCreate)
// ─────────────────────────────────────────────────────────────

describe('CaresComponent — auto-open from ?openCreate', () => {
  let onAddCategorySpy: jasmine.Spy;
  let onAddCareSpy: jasmine.Spy;
  let snackOpen: jasmine.Spy;
  let routerNavigate: jasmine.Spy;
  let categoriesSig: ReturnType<typeof signal<Category[]>>;
  let isFulfilledSig: ReturnType<typeof signal<boolean>>;

  function setup(opts: {
    openCreate?: string;
    categories?: Category[];
    isFulfilled?: boolean;
  } = {}): CaresComponent {
    categoriesSig = signal<Category[]>(opts.categories ?? []);
    isFulfilledSig = signal(opts.isFulfilled ?? true);

    const queryMap = opts.openCreate
      ? convertToParamMap({ openCreate: opts.openCreate })
      : convertToParamMap({});
    const route = { snapshot: { queryParamMap: queryMap } } as unknown as ActivatedRoute;

    routerNavigate = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));
    const router = { navigate: routerNavigate } as unknown as Router;

    snackOpen = jasmine.createSpy('open');
    const snack = { open: snackOpen } as unknown as MatSnackBar;

    const dialogRef = { afterClosed: () => of(null) } as MatDialogRef<unknown>;
    const dialog = { open: jasmine.createSpy('open').and.returnValue(dialogRef) } as unknown as MatDialog;

    const caresStore = {
      cares: signal<Care[]>([]),
      availableCares: computed(() => [] as Care[]),
      isPending: signal(false),
      error: signal(''),
      getCares: jasmine.createSpy('getCares'),
      createCare: jasmine.createSpy('createCare'),
      updateCare: jasmine.createSpy('updateCare'),
      deleteCare: jasmine.createSpy('deleteCare'),
    };

    const categoriesStore = {
      categories: categoriesSig,
      isFulfilled: isFulfilledSig,
      getProCategories: jasmine.createSpy('getProCategories'),
      createProCategory: jasmine.createSpy('createProCategory'),
      updateProCategory: jasmine.createSpy('updateProCategory'),
      deleteProCategory: jasmine.createSpy('deleteProCategory'),
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [
        CaresComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideHttpClient(),
        provideTranslocoLocale({
          defaultLocale: 'en-US',
          langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' },
        }),
        { provide: ActivatedRoute, useValue: route },
        { provide: Router, useValue: router },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snack },
        { provide: CaresService, useValue: jasmine.createSpyObj('CaresService', ['get']) },
      ],
    }).overrideComponent(CaresComponent, {
      set: {
        providers: [
          { provide: CaresStore, useValue: caresStore },
          { provide: CategoriesStore, useValue: categoriesStore },
        ],
      },
    });

    const fixture = TestBed.createComponent(CaresComponent);
    const comp = fixture.componentInstance;
    onAddCategorySpy = spyOn(comp, 'onAddCategory').and.callThrough();
    onAddCareSpy = spyOn(comp, 'onAddCare').and.callThrough();
    // Replace the component's snackBar with our mock so we can assert on .open
    // (the inject() in the constructor already grabbed the live one before
    // overrideComponent had a chance, depending on test ordering).
    (comp as unknown as { snackBar: MatSnackBar }).snackBar = snack;
    fixture.detectChanges();
    return comp;
  }

  it('no openCreate param → no modal opened', () => {
    setup();
    expect(onAddCategorySpy).not.toHaveBeenCalled();
    expect(onAddCareSpy).not.toHaveBeenCalled();
    expect(snackOpen).not.toHaveBeenCalled();
  });

  it('openCreate=category → opens category modal', () => {
    setup({ openCreate: 'category' });
    expect(onAddCategorySpy).toHaveBeenCalledTimes(1);
    expect(onAddCareSpy).not.toHaveBeenCalled();
    expect(snackOpen).not.toHaveBeenCalled();
  });

  it('openCreate=care with categories present → opens care modal', () => {
    setup({ openCreate: 'care', categories: [{ id: 1, name: 'Visage', description: '' }] });
    expect(onAddCareSpy).toHaveBeenCalledTimes(1);
    expect(onAddCategorySpy).not.toHaveBeenCalled();
    expect(snackOpen).not.toHaveBeenCalled();
  });

  it('openCreate=care with no categories → opens category modal AND shows snack hint', () => {
    setup({ openCreate: 'care', categories: [] });
    expect(onAddCategorySpy).toHaveBeenCalledTimes(1);
    expect(onAddCareSpy).not.toHaveBeenCalled();
    expect(snackOpen).toHaveBeenCalledTimes(1);
  });

  it('strips the openCreate query param after handling (so refresh does not re-open)', () => {
    setup({ openCreate: 'category' });
    expect(routerNavigate).toHaveBeenCalledTimes(1);
    const args = routerNavigate.calls.mostRecent().args[1] as {
      queryParams: { openCreate: string | null };
      queryParamsHandling: string;
      replaceUrl: boolean;
    };
    expect(args.queryParams.openCreate).toBeNull();
    expect(args.queryParamsHandling).toBe('merge');
    expect(args.replaceUrl).toBeTrue();
  });

  it('unknown openCreate value → ignored, no modal opened', () => {
    setup({ openCreate: 'foobar' });
    expect(onAddCategorySpy).not.toHaveBeenCalled();
    expect(onAddCareSpy).not.toHaveBeenCalled();
  });

  it('does not auto-open while categories are still loading (isFulfilled=false)', () => {
    setup({ openCreate: 'care', isFulfilled: false, categories: [] });
    expect(onAddCategorySpy).not.toHaveBeenCalled();
    expect(onAddCareSpy).not.toHaveBeenCalled();
    // When isFulfilled flips to true the modal opens; we don't drive the effect
    // re-run here (zoneless detectChanges is needed) — covered by the next test.
  });

  it('opens the modal once isFulfilled flips from false to true', () => {
    const comp = setup({ openCreate: 'category', isFulfilled: false, categories: [] });
    expect(onAddCategorySpy).not.toHaveBeenCalled();

    isFulfilledSig.set(true);
    TestBed.flushEffects();

    expect(onAddCategorySpy).toHaveBeenCalledTimes(1);
    // Shouldn't double-open if effect re-runs for any reason
    isFulfilledSig.set(false);
    isFulfilledSig.set(true);
    TestBed.flushEffects();
    expect(onAddCategorySpy).toHaveBeenCalledTimes(1);
    // Touch unused capture so TS doesn't complain
    void comp;
  });
});
