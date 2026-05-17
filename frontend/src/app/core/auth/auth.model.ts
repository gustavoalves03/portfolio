export enum AuthProvider {
  LOCAL = 'LOCAL',
  GOOGLE = 'GOOGLE',
  FACEBOOK = 'FACEBOOK',
  APPLE = 'APPLE',
}

export enum Role {
  PRO = 'PRO',
  EMPLOYEE = 'EMPLOYEE',
  COMMERCIAL = 'COMMERCIAL',
  ADMIN = 'ADMIN',
}

export interface TenantSummary {
  id: number;
  slug: string;
  name: string;
}

export interface User {
  id: number;
  name: string;
  email: string;
  imageUrl?: string;
  provider: AuthProvider;
  roles: Role[];
  activeTenantId: number | null;
  availableTenants: TenantSummary[];
  emailVerified: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: User;
}
