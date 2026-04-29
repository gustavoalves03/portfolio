import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { LeavesStore } from './leaves.store';
import { LeavesService } from './leaves.service';

/**
 * The store used to surface raw HttpErrorResponse messages
 * ("Http failure response for http://...: 500") to the UI. We now translate
 * those into i18n keys so the user sees a friendly message and the URL leak
 * is gone. These tests pin that behavior so we don't regress.
 */
describe('LeavesStore — error mapping', () => {
  let service: jasmine.SpyObj<LeavesService>;
  let store: InstanceType<typeof LeavesStore>;

  beforeEach(() => {
    service = jasmine.createSpyObj<LeavesService>('LeavesService', [
      'listPending',
      'listHistory',
      'review',
    ]);
    service.listPending.and.returnValue(of([]));
    service.listHistory.and.returnValue(of([]));
    service.review.and.returnValue(of({} as any));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        LeavesStore,
        { provide: LeavesService, useValue: service },
      ],
    });

    store = TestBed.inject(LeavesStore);
  });

  it('loadPending success: error stays null', () => {
    service.listPending.and.returnValue(of([]));
    store.loadPending();
    expect(store.error()).toBeFalsy();
  });

  it('loadPending HTTP 500: maps to i18n key, never the raw URL', () => {
    const err = new HttpErrorResponse({
      status: 500,
      statusText: 'Internal Server Error',
      url: 'http://localhost:8080/api/pro/leaves/pending',
    });
    service.listPending.and.returnValue(throwError(() => err));

    store.loadPending();

    expect(store.error()).toBe('pro.leaves.errors.loadFailed');
    // Must not leak the URL or status code
    expect(store.error()).not.toContain('localhost');
    expect(store.error()).not.toContain('500');
    expect(store.error()).not.toContain('Http failure');
  });

  it('loadPending arbitrary thrown error: same i18n key', () => {
    service.listPending.and.returnValue(throwError(() => new Error('boom')));

    store.loadPending();

    expect(store.error()).toBe('pro.leaves.errors.loadFailed');
  });

  it('loadHistory failure: separate i18n key', () => {
    service.listHistory.and.returnValue(throwError(() => new Error('boom')));

    store.loadHistory(undefined);

    expect(store.error()).toBe('pro.leaves.errors.historyFailed');
  });

  it('review failure: review-specific i18n key', () => {
    service.review.and.returnValue(throwError(() => new Error('boom')));

    store.reviewLeave({ leaveId: 1, dto: { status: 'APPROVED' } as any });

    expect(store.error()).toBe('pro.leaves.errors.reviewFailed');
  });
});
