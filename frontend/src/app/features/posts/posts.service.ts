import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { PostResponse, RecentPost } from './posts.model';
import { Page } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class PostsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  listPublic(
    slug: string,
    page = 0,
    size = 20,
  ): Observable<Page<PostResponse>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size);
    return this.http.get<Page<PostResponse>>(
      `${this.apiBaseUrl}/api/salon/${slug}/posts`,
      { params },
    );
  }

  listPro(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.apiBaseUrl}/api/pro/posts`);
  }

  createBeforeAfter(data: FormData): Observable<PostResponse> {
    return this.http.post<PostResponse>(
      `${this.apiBaseUrl}/api/pro/posts/before-after`,
      data,
    );
  }

  createPhoto(data: FormData): Observable<PostResponse> {
    return this.http.post<PostResponse>(`${this.apiBaseUrl}/api/pro/posts/photo`, data);
  }

  createCarousel(data: FormData): Observable<PostResponse> {
    return this.http.post<PostResponse>(
      `${this.apiBaseUrl}/api/pro/posts/carousel`,
      data,
    );
  }

  update(id: number, data: FormData): Observable<PostResponse> {
    return this.http.put<PostResponse>(`${this.apiBaseUrl}/api/pro/posts/${id}`, data);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/pro/posts/${id}`);
  }

  listRecentPublic(): Observable<RecentPost[]> {
    return this.http.get<RecentPost[]>(`${this.apiBaseUrl}/api/public/posts/recent`);
  }
}
