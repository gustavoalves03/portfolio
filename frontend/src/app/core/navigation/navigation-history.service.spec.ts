import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { NavigationHistoryService } from './navigation-history.service';

describe('NavigationHistoryService', () => {
  let events$: Subject<unknown>;
  let routerMock: { events: Subject<unknown> };

  beforeEach(() => {
    events$ = new Subject<unknown>();
    routerMock = { events: events$ };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Router, useValue: routerMock },
        NavigationHistoryService,
      ],
    });
  });

  it('reports no internal history before any navigation', () => {
    const svc = TestBed.inject(NavigationHistoryService);
    expect(svc.hasInternalHistory()).toBeFalse();
  });

  it('reports no internal history after exactly one NavigationEnd', () => {
    const svc = TestBed.inject(NavigationHistoryService);
    events$.next(new NavigationEnd(1, '/a', '/a'));
    expect(svc.hasInternalHistory()).toBeFalse();
  });

  it('reports internal history after two or more NavigationEnd events', () => {
    const svc = TestBed.inject(NavigationHistoryService);
    events$.next(new NavigationEnd(1, '/a', '/a'));
    events$.next(new NavigationEnd(2, '/b', '/b'));
    expect(svc.hasInternalHistory()).toBeTrue();
  });

  it('ignores non-NavigationEnd events', () => {
    const svc = TestBed.inject(NavigationHistoryService);
    events$.next({ type: 'random' });
    events$.next({ type: 'other' });
    expect(svc.hasInternalHistory()).toBeFalse();
  });
});
