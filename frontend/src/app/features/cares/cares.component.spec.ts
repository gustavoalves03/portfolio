import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideZonelessChangeDetection, signal, computed } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
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
