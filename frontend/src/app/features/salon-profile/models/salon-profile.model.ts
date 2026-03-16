export interface TenantResponse {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  logoUrl: string | null;
  updatedAt: string | null;
}

export interface UpdateTenantRequest {
  name: string;
  description: string | null;
  logo: string | null; // base64 or null (no change) or "" (remove)
}

export interface PublicCareDto {
  name: string;
  duration: number;
  price: number;
  imageUrls: string[];
}

export interface PublicCategoryDto {
  name: string;
  cares: PublicCareDto[];
}

export interface PublicSalonResponse {
  name: string;
  slug: string;
  description: string | null;
  logoUrl: string | null;
  categories: PublicCategoryDto[];
}
