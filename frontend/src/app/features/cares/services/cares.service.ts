import { Injectable } from '@angular/core';
import { BaseCrudService } from '../../../core/data/base-crud.service';
import { Care, CreateCareRequest, UpdateCareRequest } from '../models/cares.model';

@Injectable({ providedIn: 'root' })
export class CaresService extends BaseCrudService<Care, CreateCareRequest, UpdateCareRequest> {
  protected readonly basePath = '/api/care';
}
