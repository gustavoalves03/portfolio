export interface SalonClientResponse {
  id: number;
  name: string;
  phone: string;
  email: string | null;
  dateOfBirth: string | null;
  notes: string | null;
  userId: number | null;
  manual: boolean;
  createdAt: string;
  createdByName: string | null;
}

export interface CreateSalonClientRequest {
  name: string;
  phone: string;
  email: string | null;
  dateOfBirth: string | null;
  notes: string | null;
}
