import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal, computed } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { of, throwError } from 'rxjs';

import { SalonProfileComponent } from './salon-profile.component';
import { SalonProfileStore } from './store/salon-profile.store';
import { SalonProfileService } from './services/salon-profile.service';
import { TenantResponse, UpdateTenantRequest } from './models/salon-profile.model';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

function makeTenant(overrides: Partial<TenantResponse> = {}): TenantResponse {
  return {
    id: 1,
    name: 'Pretty Face Atelier',
    slug: 'pretty-face-atelier',
    status: 'ACTIVE',
    description: 'Salon test',
    logoUrl: '/uploads/logo.png',
    heroImageUrl: null,
    addressStreet: '1 rue X',
    addressPostalCode: '75001',
    addressCity: 'Paris',
    addressCountry: 'FR',
    phone: '0102030405',
    contactEmail: 'contact@test.fr',
    siret: '12345678901234',
    updatedAt: null,
    employeesEnabled: false,
    closedOnHolidays: false,
    ...overrides,
  };
}

function createMockStore(initialTenant: TenantResponse | null = makeTenant()) {
  const tenantSig = signal<TenantResponse | null>(initialTenant);
  const isPending = signal(false);
  const error = signal<string | null>(null);
  const saveSuccess = signal(false);

  return {
    tenant: tenantSig,
    isPending,
    error,
    isFulfilled: computed(() => !isPending() && !error()),
    saveSuccess,
    loadProfile: jasmine.createSpy('loadProfile'),
    updateProfile: jasmine.createSpy('updateProfile'),
    clearStatus: jasmine.createSpy('clearStatus').and.callFake(() => {
      saveSuccess.set(false);
    }),
    // Escape hatches to drive the component in tests
    _setTenant: (t: TenantResponse | null) => tenantSig.set(t),
    _setPending: (v: boolean) => isPending.set(v),
    _setError: (v: string | null) => error.set(v),
    _setSaveSuccess: (v: boolean) => saveSuccess.set(v),
  };
}

describe('SalonProfileComponent', () => {
  let component: SalonProfileComponent;
  let fixture: ComponentFixture<SalonProfileComponent>;
  let mockStore: ReturnType<typeof createMockStore>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;
  let mockService: jasmine.SpyObj<SalonProfileService>;

  async function configure(initialTenant: TenantResponse | null = makeTenant()) {
    mockStore = createMockStore(initialTenant);
    mockSnackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);
    mockService = jasmine.createSpyObj<SalonProfileService>('SalonProfileService', [
      'getProfile',
      'updateProfile',
    ]);
    mockService.getProfile.and.returnValue(of(makeTenant()));
    mockService.updateProfile.and.returnValue(of(makeTenant()));

    await TestBed.configureTestingModule({
      imports: [
        SalonProfileComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              'pro.salon.title': 'Mon salon',
              'pro.salon.name': 'Nom',
              'pro.salon.description': 'Description',
              'pro.salon.saveSuccess': 'Enregistré',
              'pro.salon.saveError': 'Erreur',
              'pro.salon.changeLogo': 'Changer le logo',
              'pro.salon.heroImage': 'Image',
              'pro.salon.addHeroImage': 'Ajouter',
            },
          },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        provideTranslocoLocale({
          defaultLocale: 'fr-FR',
          langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' },
        }),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: SalonProfileService, useValue: mockService },
      ],
    })
      .overrideComponent(SalonProfileComponent, {
        set: { providers: [{ provide: SalonProfileStore, useValue: mockStore }] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(SalonProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('creates and syncs tenant into form signals via effect', async () => {
    await configure();
    expect(component).toBeTruthy();
    // Form signals are populated from the initial tenant
    expect((component as any).name()).toBe('Pretty Face Atelier');
    expect((component as any).description()).toBe('Salon test');
    expect((component as any).addressCity()).toBe('Paris');
    expect((component as any).logoImages().length).toBe(1);
  });

  it('starts with empty form fields when no tenant is present', async () => {
    await configure(null);
    expect((component as any).name()).toBe('');
    expect((component as any).description()).toBe('');
    expect((component as any).logoImages().length).toBe(0);
  });

  it('invalid when name is empty, valid when name is set', async () => {
    await configure();
    // Start with a tenant (name populated) — save should go through
    (component as any).onSave();
    expect(mockStore.updateProfile).toHaveBeenCalledTimes(1);

    // Now clear the name and try again
    mockStore.updateProfile.calls.reset();
    (component as any).name.set('   '); // whitespace only
    (component as any).onSave();
    expect(mockStore.updateProfile).not.toHaveBeenCalled();

    // Set a valid name
    (component as any).name.set('New Salon');
    (component as any).onSave();
    expect(mockStore.updateProfile).toHaveBeenCalledTimes(1);
  });

  it('onSave builds an UpdateTenantRequest from form signals', async () => {
    await configure();
    (component as any).name.set('Renamed');
    (component as any).description.set('Updated desc');
    (component as any).addressCity.set('Lyon');

    (component as any).onSave();

    expect(mockStore.updateProfile).toHaveBeenCalledTimes(1);
    const req = mockStore.updateProfile.calls.mostRecent().args[0] as UpdateTenantRequest;
    expect(req.name).toBe('Renamed');
    expect(req.description).toBe('Updated desc');
    expect(req.addressCity).toBe('Lyon');
    // logo unchanged → null (no-change)
    expect(req.logo).toBeNull();
    // hero unchanged → null
    expect(req.heroImage).toBeNull();
  });

  it('when logo is uploaded onSave sends the data URL as logo', async () => {
    await configure();
    // Simulate user picking a new logo file (ImageManager uses file.url as data URL)
    (component as any).onLogoChange([
      { id: 'new-logo', url: 'data:image/png;base64,XYZ', name: 'new.png', order: 0, file: new Blob() },
    ]);

    (component as any).onSave();

    const req = mockStore.updateProfile.calls.mostRecent().args[0] as UpdateTenantRequest;
    expect(req.logo).toBe('data:image/png;base64,XYZ');
  });

  it('when logo is removed onSave sends empty string', async () => {
    await configure();
    (component as any).removeLogo();
    (component as any).onSave();

    const req = mockStore.updateProfile.calls.mostRecent().args[0] as UpdateTenantRequest;
    expect(req.logo).toBe('');
  });

  it('opens snackbar when saveSuccess flips true and clears the status flag', async () => {
    await configure();
    mockSnackBar.open.calls.reset();

    mockStore._setSaveSuccess(true);
    // effect runs on signal change — flush
    TestBed.tick();
    fixture.detectChanges();

    expect(mockSnackBar.open).toHaveBeenCalled();
    const args = mockSnackBar.open.calls.mostRecent().args;
    expect(args[2]?.duration).toBe(3000);
    expect(mockStore.clearStatus).toHaveBeenCalled();
  });

  it('opens snackbar with error panel class when error signal is set', async () => {
    await configure();
    mockSnackBar.open.calls.reset();

    mockStore._setError('Erreur de sauvegarde');
    TestBed.tick();
    fixture.detectChanges();

    expect(mockSnackBar.open).toHaveBeenCalled();
    const args = mockSnackBar.open.calls.mostRecent().args;
    // panelClass: 'snackbar-error'
    expect(args[2]?.panelClass).toBe('snackbar-error');
    expect(args[2]?.duration).toBe(5000);
  });

  it('shows the loading spinner while pending and no tenant yet', async () => {
    await configure(null);
    mockStore._setPending(true);
    TestBed.tick();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('mat-spinner')).toBeTruthy();
  });

  it('onLogoFileSelected reads the file via FileReader and sets logoImages', async () => {
    await configure();

    // Stub FileReader to fire load with a deterministic data URL
    const originalFR = (globalThis as any).FileReader;
    class StubFR {
      result: string | ArrayBuffer | null = null;
      onload: ((this: FileReader, ev: ProgressEvent<FileReader>) => any) | null = null;
      readAsDataURL(_file: Blob) {
        this.result = 'data:image/png;base64,STUBBED';
        // fire asynchronously to mirror real behavior
        setTimeout(() => this.onload?.call(this as any, {} as any), 0);
      }
    }
    (globalThis as any).FileReader = StubFR as any;

    const file = new Blob(['x'], { type: 'image/png' }) as any;
    (file as any).name = 'logo.png';
    const event = {
      target: { files: [file], value: '' },
    } as unknown as Event;

    (component as any).onLogoFileSelected(event);

    await new Promise((r) => setTimeout(r, 10));
    fixture.detectChanges();

    const images = (component as any).logoImages();
    expect(images.length).toBe(1);
    expect(images[0].url).toBe('data:image/png;base64,STUBBED');

    (globalThis as any).FileReader = originalFR;
  });

  it('onHeroFileSelected reads the file and updates heroImageUrl', async () => {
    await configure();

    const originalFR = (globalThis as any).FileReader;
    class StubFR {
      result: string | ArrayBuffer | null = null;
      onload: ((this: FileReader, ev: ProgressEvent<FileReader>) => any) | null = null;
      readAsDataURL(_file: Blob) {
        this.result = 'data:image/jpeg;base64,HERO';
        setTimeout(() => this.onload?.call(this as any, {} as any), 0);
      }
    }
    (globalThis as any).FileReader = StubFR as any;

    const file = new Blob(['x'], { type: 'image/jpeg' }) as any;
    const event = { target: { files: [file], value: '' } } as unknown as Event;

    (component as any).onHeroFileSelected(event);
    await new Promise((r) => setTimeout(r, 10));
    fixture.detectChanges();

    expect((component as any).heroImageUrl()).toBe('data:image/jpeg;base64,HERO');

    // onSave should now send the data URL as heroImage
    (component as any).onSave();
    const req = mockStore.updateProfile.calls.mostRecent().args[0] as UpdateTenantRequest;
    expect(req.heroImage).toBe('data:image/jpeg;base64,HERO');

    (globalThis as any).FileReader = originalFR;
  });
});

describe('SalonProfileStore (HTTP)', () => {
  let mockService: jasmine.SpyObj<SalonProfileService>;

  beforeEach(() => {
    mockService = jasmine.createSpyObj<SalonProfileService>('SalonProfileService', [
      'getProfile',
      'updateProfile',
    ]);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: SalonProfileService, useValue: mockService },
        SalonProfileStore,
      ],
    });
  });

  it('loadProfile fetches the tenant and populates state', () => {
    mockService.getProfile.and.returnValue(of(makeTenant({ name: 'From API' })));
    const store = TestBed.inject(SalonProfileStore);
    // onInit already triggered loadProfile — re-call to be explicit
    store.loadProfile();
    expect(mockService.getProfile).toHaveBeenCalled();
    expect(store.tenant()?.name).toBe('From API');
  });

  it('loadProfile sets error on HTTP failure', () => {
    mockService.getProfile.and.returnValue(throwError(() => new Error('boom')));
    const store = TestBed.inject(SalonProfileStore);
    store.loadProfile();
    expect(store.error()).toBe('Erreur de chargement du profil');
  });

  it('updateProfile on success updates tenant and sets saveSuccess', () => {
    mockService.getProfile.and.returnValue(of(makeTenant()));
    mockService.updateProfile.and.returnValue(of(makeTenant({ name: 'After Save' })));
    const store = TestBed.inject(SalonProfileStore);

    const req: UpdateTenantRequest = {
      name: 'After Save',
      description: null,
      logo: null,
      heroImage: null,
      addressStreet: null,
      addressPostalCode: null,
      addressCity: null,
      addressCountry: null,
      phone: null,
      contactEmail: null,
      siret: null,
    };
    store.updateProfile(req);

    expect(mockService.updateProfile).toHaveBeenCalledWith(req);
    expect(store.tenant()?.name).toBe('After Save');
    expect(store.saveSuccess()).toBeTrue();
  });

  it('updateProfile on error sets error and does not flip saveSuccess', () => {
    mockService.getProfile.and.returnValue(of(makeTenant()));
    mockService.updateProfile.and.returnValue(throwError(() => new Error('fail')));
    const store = TestBed.inject(SalonProfileStore);

    const req: UpdateTenantRequest = {
      name: 'X',
      description: null, logo: null, heroImage: null,
      addressStreet: null, addressPostalCode: null, addressCity: null, addressCountry: null,
      phone: null, contactEmail: null, siret: null,
    };
    store.updateProfile(req);

    expect(store.error()).toBe('Erreur lors de la sauvegarde');
    expect(store.saveSuccess()).toBeFalse();
  });

  it('clearStatus resets saveSuccess to false', () => {
    mockService.getProfile.and.returnValue(of(makeTenant()));
    mockService.updateProfile.and.returnValue(of(makeTenant()));
    const store = TestBed.inject(SalonProfileStore);

    const req: UpdateTenantRequest = {
      name: 'X',
      description: null, logo: null, heroImage: null,
      addressStreet: null, addressPostalCode: null, addressCity: null, addressCountry: null,
      phone: null, contactEmail: null, siret: null,
    };
    store.updateProfile(req);
    expect(store.saveSuccess()).toBeTrue();

    store.clearStatus();
    expect(store.saveSuccess()).toBeFalse();
  });
});
