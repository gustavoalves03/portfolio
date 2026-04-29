import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
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

  // ─────────────────────────────────────────────────────────────
  // Adversarial: spam loadPending, switchMap cancellation
  // ─────────────────────────────────────────────────────────────

  describe('adversarial', () => {
    it('spam loadPending: only the latest response wins (switchMap cancels older inflight)', () => {
      const first = new Subject<any[]>();
      const second = new Subject<any[]>();
      service.listPending.and.returnValues(
        first.asObservable(),
        second.asObservable(),
      );

      store.loadPending();
      store.loadPending(); // second click cancels first

      // Resolve the first AFTER it was cancelled — should not flip state.
      first.next([{ id: 99 }] as any);
      first.complete();
      expect(store.pendingLeaves()).toEqual([]);

      // Resolve the second — this is the one that wins.
      second.next([{ id: 1 }] as any);
      second.complete();
      expect(store.pendingLeaves().length).toBe(1);
      expect((store.pendingLeaves()[0] as any).id).toBe(1);
    });

    it('spam loadPending: every dispatch is honored, no infinite loop', () => {
      service.listPending.and.returnValue(of([]));
      const before = service.listPending.calls.count(); // onInit fired once
      for (let i = 0; i < 10; i++) {
        store.loadPending();
      }
      expect(service.listPending.calls.count() - before).toBe(10);
    });

    it('error then success on the same store: latest result clears the previous error', () => {
      service.listPending.and.returnValue(throwError(() => new Error('boom')));
      store.loadPending();
      expect(store.error()).toBe('pro.leaves.errors.loadFailed');

      service.listPending.and.returnValue(of([{ id: 1 } as any]));
      store.loadPending();
      // setFulfilled clears the error
      expect(store.error()).toBeFalsy();
      expect(store.pendingLeaves().length).toBe(1);
    });
  });
});
