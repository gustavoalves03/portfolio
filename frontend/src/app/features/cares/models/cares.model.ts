import {Category} from '../../categories/models/categories.model';
import { ManagedImage } from '../../../shared/uis/image-manager/image-manager.component';

export enum CareStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
}

export interface CareImage extends ManagedImage {}

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
  /**
   * The backend returns `categoryId` (Long). Older code paths read
   * `care.category.id` — both fields are optional so existing fixtures
   * keep typechecking; runtime code reads whichever is present.
   */
  categoryId?: number;
  category?: Category;
  displayOrder?: number;
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
