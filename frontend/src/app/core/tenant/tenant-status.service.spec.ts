import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TenantStatusService } from './tenant-status.service';

describe('TenantStatusService', () => {
  let service: TenantStatusService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(TenantStatusService);
  });

  it('starts with null status', () => {
    expect(service.status()).toBeNull();
  });

  it('set() updates the status signal', () => {
    service.set('DRAFT');
    expect(service.status()).toBe('DRAFT');
    service.set('ACTIVE');
    expect(service.status()).toBe('ACTIVE');
  });

  it('reset() returns the status to null', () => {
    service.set('DRAFT');
    service.reset();
    expect(service.status()).toBeNull();
  });
});
