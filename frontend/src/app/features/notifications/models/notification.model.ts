export interface NotificationResponse {
  id: number;
  type: string;
  category: string;
  title: string;
  message: string;
  referenceId: number;
  referenceType: string;
  read: boolean;
  tenantSlug: string;
  createdAt: string;
}
