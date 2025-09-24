import {Category} from '../../categories/models/categories.model';

export enum CareStatus {
  PENDING = 'PENDING',
  SENT = 'SENT',
  ACCEPTED = 'ACCEPTED',
  REJECTED = 'REJECTED',
  CANCELLED = 'CANCELLED',
  FINISHED = 'FINISHED',
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
  category: Category;
}

export type UpdateCareRequest = CreateCareRequest;
