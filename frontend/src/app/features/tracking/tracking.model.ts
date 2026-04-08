export type PhotoType = 'BEFORE' | 'AFTER' | 'PROGRESS';

export interface VisitPhotoResponse {
  id: number;
  photoType: PhotoType;
  imageUrl: string;
  imageOrder: number;
  uploadedByName: string | null;
}

export interface VisitRecordResponse {
  id: number;
  clientProfileId: number;
  bookingId: number | null;
  careId: number | null;
  careName: string | null;
  visitDate: string;
  practitionerNotes: string | null;
  productsUsed: string | null;
  satisfactionScore: number | null;
  satisfactionComment: string | null;
  createdAt: string;
  updatedAt: string | null;
  updatedByName: string | null;
  photos: VisitPhotoResponse[];
}

export interface ClientProfileResponse {
  id: number;
  userId: number;
  notes: string | null;
  skinType: string | null;
  hairType: string | null;
  allergies: string | null;
  preferences: string | null;
  consentPhotos: boolean;
  consentPublicShare: boolean;
  consentGivenAt: string | null;
  createdAt: string;
  updatedAt: string | null;
  updatedByName: string | null;
}

export interface ReminderResponse {
  id: number;
  userId: number;
  careId: number | null;
  careName: string | null;
  recommendedDate: string;
  message: string | null;
  sent: boolean;
  createdAt: string;
  createdByName: string | null;
}

export interface ClientHistoryResponse {
  clientName: string;
  clientEmail: string | null;
  profile: ClientProfileResponse;
  visits: VisitRecordResponse[];
  reminders: ReminderResponse[];
}

export interface UpdateProfileRequest {
  notes: string | null;
  skinType: string | null;
  hairType: string | null;
  allergies: string | null;
  preferences: string | null;
}

export interface CreateVisitRequest {
  bookingId: number | null;
  careId: number | null;
  careName: string | null;
  visitDate: string;
  practitionerNotes: string | null;
  productsUsed: string | null;
}

export interface CreateReminderRequest {
  careId: number | null;
  careName: string | null;
  recommendedDate: string;
  message: string | null;
}

export type PermissionDomain = 'PROFILE' | 'VISITS' | 'PHOTOS' | 'REMINDERS';
export type AccessLevel = 'NONE' | 'READ' | 'WRITE';
export type PermissionsMap = Record<PermissionDomain, AccessLevel>;
