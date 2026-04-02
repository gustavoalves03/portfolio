export enum AuthProvider {
  LOCAL = 'LOCAL',
  GOOGLE = 'GOOGLE',
  FACEBOOK = 'FACEBOOK',
  APPLE = 'APPLE'
}

export enum Role {
  USER = 'USER',
  ADMIN = 'ADMIN',
  PRO = 'PRO',
  EMPLOYEE = 'EMPLOYEE'
}

export interface User {
  id: number;
  name: string;
  email: string;
  imageUrl?: string;
  provider: AuthProvider;
  role: Role;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: User;
}
