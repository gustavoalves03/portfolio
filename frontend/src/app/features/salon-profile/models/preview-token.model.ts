export interface PreviewTokenResponse {
  id: number;
  token: string;
  /** Relative path like "/salon/demo?preview=<token>". The UI prepends location.origin to render the absolute share link. */
  shareUrl: string;
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
}
