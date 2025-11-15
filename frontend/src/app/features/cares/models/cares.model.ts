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
  base64Data?: string;  // Base64 data to send to backend
}

// Optimized image data for API requests (only essential fields)
export interface CareImageRequest {
  id?: string;
  name: string;
  order: number;
  url?: string;  // Optional: only for display, not sent to backend
  base64Data?: string;  // Only for new images
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
  images?: CareImageRequest[];  // Use optimized type
}

export type UpdateCareRequest = CreateCareRequest;
