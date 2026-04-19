import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PostsService } from './posts.service';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

describe('PostsService', () => {
  let service: PostsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://test' },
      ],
    });
    service = TestBed.inject(PostsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listPublic sends page and size query params', () => {
    service.listPublic('my-salon', 2, 15).subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === 'http://test/api/salon/my-salon/posts' &&
        r.params.get('page') === '2' &&
        r.params.get('size') === '15',
    );
    req.flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 15,
      first: true,
      last: true,
      empty: true,
    });
  });

  it('listPublic defaults to page=0 size=20', () => {
    service.listPublic('my-salon').subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === 'http://test/api/salon/my-salon/posts' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20',
    );
    req.flush({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
      first: true,
      last: true,
      empty: true,
    });
  });
});
