import { Injectable } from '@angular/core';
import {
  Category,
  CreateCategoryRequest,
  DeleteCategoryResponse,
  UpdateCategoryRequest,
} from '../models/categories.model';
import { BaseCrudService } from '../../../core/data/base-crud.service';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CategoriesService extends BaseCrudService<
  Category,
  CreateCategoryRequest,
  UpdateCategoryRequest
> {
  protected readonly basePath = '/api/categories';

  private get proBaseUrl(): string {
    const a = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return `${a}/api/pro/categories`;
  }

  listAllPro(): Observable<Category[]> {
    return this.http.get<Category[]>(this.proBaseUrl);
  }

  createPro(payload: CreateCategoryRequest): Observable<Category> {
    return this.http.post<Category>(this.proBaseUrl, payload);
  }

  updatePro(id: number, payload: UpdateCategoryRequest): Observable<Category> {
    return this.http.put<Category>(`${this.proBaseUrl}/${id}`, payload);
  }

  deletePro(id: number, reassignTo?: number): Observable<DeleteCategoryResponse> {
    const params: Record<string, string> = {};
    if (reassignTo != null) {
      params['reassignTo'] = reassignTo.toString();
    }
    return this.http.delete<DeleteCategoryResponse>(`${this.proBaseUrl}/${id}`, { params });
  }
}
