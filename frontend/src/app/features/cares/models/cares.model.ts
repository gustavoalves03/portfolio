import {Category} from '../../categories/models/categories.model';

export enum CareStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
}

export interface Care {
  id: number;
  name: string;
  price: number;
  description: string;
  duration: number;
  status: CareStatus;
  category: Category;
}

export interface CreateCareRequest {
  name: string;
  price: number;
  description: string;
  duration: number;
  status: CareStatus;
  categoryId: string;
}

export type UpdateCareRequest = CreateCareRequest;
