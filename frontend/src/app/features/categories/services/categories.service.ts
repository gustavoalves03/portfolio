import { Injectable } from '@angular/core';
import { Category, CreateCategoryRequest, UpdateCategoryRequest } from '../models/categories.model';
import { BaseCrudService } from '../../../core/data/base-crud.service';

@Injectable({ providedIn: 'root' })
export class CategoriesService extends BaseCrudService<
  Category,
  CreateCategoryRequest,
  UpdateCategoryRequest
> {
  protected readonly basePath = '/api/categories';
}
