import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { PostResponse } from './posts.model';

@Injectable({ providedIn: 'root' })
export class PostsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  listPublic(slug: string): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.apiBaseUrl}/api/salon/${slug}/posts`);
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

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/pro/posts/${id}`);
  }
}
