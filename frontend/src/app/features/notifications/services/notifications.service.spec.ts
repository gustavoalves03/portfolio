import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NotificationsService } from './notifications.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

describe('NotificationsService', () => {
  let service: NotificationsService;
  let httpTesting: HttpTestingController;
  const apiUrl = 'http://localhost:8080';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: apiUrl },
      ],
    });
    service = TestBed.inject(NotificationsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('list calls GET /api/notifications with params', () => {
    service.list(false, 0, 20).subscribe();
    const req = httpTesting.expectOne((r) => r.url === `${apiUrl}/api/notifications`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('read')).toBe('false');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [], totalElements: 0, totalPages: 0 });
  });

  it('unreadCount calls GET /api/notifications/unread/count', () => {
    service.unreadCount().subscribe((count) => {
      expect(count).toBe(5);
    });
    const req = httpTesting.expectOne(`${apiUrl}/api/notifications/unread/count`);
    expect(req.request.method).toBe('GET');
    req.flush(5);
  });

  it('markAsRead calls PATCH /api/notifications/{id}/read', () => {
    service.markAsRead(42).subscribe();
    const req = httpTesting.expectOne(`${apiUrl}/api/notifications/42/read`);
    expect(req.request.method).toBe('PATCH');
    req.flush(null);
  });
});
