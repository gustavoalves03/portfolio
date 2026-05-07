export interface TenantReadiness {
  slug: string;
  name: boolean;
  hasCategory: boolean;
  hasContact: boolean;
  hasLogo: boolean;
  hasActiveCare: boolean;
  hasOpeningHours: boolean;
  canPublish: boolean;
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
}

export interface PublishError {
  message: string;
  missing: string[];
}
