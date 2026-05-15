import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideZonelessChangeDetection } from '@angular/core';
import { AuthService } from './auth.service';
import { AuthProvider, Role, User } from './auth.model';
import { API_BASE_URL } from '../config/api-base-url.token';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
      ],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function setUser(overrides: Partial<User> = {}): void {
    const user: User = {
      id: 1,
      name: 'Sophie',
      email: 'sophie@x.com',
      provider: AuthProvider.LOCAL,
      roles: [Role.PRO],
      activeTenantId: 42,
      availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
      ...overrides,
    };
    (service as any).currentUser.set(user);
  }

  describe('hasRole', () => {
    it('returns false when user has no roles', () => {
      setUser({ roles: [] });
      expect(service.hasRole(Role.PRO)).toBe(false);
    });

    it('returns true when user has the required role', () => {
      setUser({ roles: [Role.PRO] });
      expect(service.hasRole(Role.PRO)).toBe(true);
    });

    it('returns true when user has any of multiple required roles', () => {
      setUser({ roles: [Role.EMPLOYEE] });
      expect(service.hasRole(Role.PRO, Role.ADMIN, Role.EMPLOYEE)).toBe(true);
    });

    it('does NOT auto-promote ADMIN — callers must include it explicitly', () => {
      setUser({ roles: [Role.ADMIN] });
      expect(service.hasRole(Role.PRO)).toBe(false);
      expect(service.hasRole(Role.PRO, Role.ADMIN)).toBe(true);
    });

    it('returns false when there is no current user', () => {
      (service as any).currentUser.set(null);
      expect(service.hasRole(Role.PRO)).toBe(false);
    });
  });

  describe('isClientMode', () => {
    it('is true when activeTenantId is null', () => {
      setUser({ activeTenantId: null });
      expect(service.isClientMode()).toBe(true);
    });

    it('is false when activeTenantId is set', () => {
      setUser({ activeTenantId: 42 });
      expect(service.isClientMode()).toBe(false);
    });
  });

  describe('switchTenant', () => {
    it('POSTs tenantId and updates currentUser', () => {
      setUser({ activeTenantId: 42 });

      service.switchTenant(43).subscribe();

      const req = httpMock.expectOne(r => r.url.endsWith('/api/me/switch-tenant'));
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ tenantId: 43 });
      req.flush({
        accessToken: 'new.jwt',
        tokenType: 'Bearer',
        user: {
          id: 1, name: 'Sophie', email: 'sophie@x.com',
          provider: AuthProvider.LOCAL,
          roles: ['PRO'],
          activeTenantId: 43,
          availableTenants: [
            { id: 42, slug: 'salon-x', name: 'Salon X' },
            { id: 43, slug: 'salon-y', name: 'Salon Y' },
          ],
        },
      });

      expect(service.activeTenantId()).toBe(43);
      expect(service.getToken()).toBe('new.jwt');
    });

    it('accepts null tenantId (client mode)', () => {
      setUser({ activeTenantId: 42 });

      service.switchTenant(null).subscribe();

      const req = httpMock.expectOne(r => r.url.endsWith('/api/me/switch-tenant'));
      expect(req.request.body).toEqual({ tenantId: null });
      req.flush({
        accessToken: 'client.jwt',
        tokenType: 'Bearer',
        user: {
          id: 1, name: 'Sophie', email: 'sophie@x.com',
          provider: AuthProvider.LOCAL,
          roles: [],
          activeTenantId: null,
          availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
        },
      });

      expect(service.isClientMode()).toBe(true);
      expect(service.roles()).toEqual([]);
    });
  });
});
