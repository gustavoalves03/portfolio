import {Category} from '../../categories/models/categories.model';

export enum CareStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
}

export interface CareImage {
  id?: string;
  url: string;
  name: string;
  order: number;
  file?: File;
}

export interface Care {
  id: number;
  name: string;
  price: number;
  description: string;
  duration: number;
  status: CareStatus;
  category: Category;
  images?: CareImage[];
}

export interface CreateCareRequest {
  name: string;
  price: number;
  description: string;
  duration: number;
  status: CareStatus;
  categoryId: number;
  images?: CareImage[];
}

export type UpdateCareRequest = CreateCareRequest;
