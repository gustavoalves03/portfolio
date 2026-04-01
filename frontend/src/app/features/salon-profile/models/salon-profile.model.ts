export interface TenantResponse {
  id: number;
  name: string;
  slug: string;
  status?: string;
  description: string | null;
  logoUrl: string | null;
  heroImageUrl: string | null;
  addressStreet: string | null;
  addressPostalCode: string | null;
  addressCity: string | null;
  phone: string | null;
  contactEmail: string | null;
  siret: string | null;
  updatedAt: string | null;
}

export interface UpdateTenantRequest {
  name: string;
  description: string | null;
  logo: string | null; // base64 or null (no change) or "" (remove)
  heroImage: string | null;
  addressStreet: string | null;
  addressPostalCode: string | null;
  addressCity: string | null;
  phone: string | null;
  contactEmail: string | null;
  siret: string | null;
}

export interface PublicCareDto {
  id: number;
  name: string;
  description: string;
  duration: number;
  price: number;
  imageUrls: string[];
}

export interface TimeSlot {
  startTime: string;
  endTime: string;
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
  heroImageUrl: string | null;
  categories: PublicCategoryDto[];
}

export interface ClientBookingRequest {
  careId: number;
  appointmentDate: string;  // "yyyy-MM-dd"
  appointmentTime: string;  // "HH:mm"
}

export interface ClientBookingResponse {
  bookingId: number;
  careName: string;
  carePrice: number;
  careDuration: number;
  appointmentDate: string;
  appointmentTime: string;
  status: string;
  salonName: string;
}
