export interface Category {
  id: number;
  name: string;
  description: string | null;
}

export interface CreateCategoryRequest {
  name: string;
  description?: string | null;
}

export type UpdateCategoryRequest = CreateCategoryRequest;

export interface DeleteCategoryResponse {
  reassignedCaresCount: number;
}

