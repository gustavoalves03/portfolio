/**
 * Anti-typo email suggestion utility (mailcheck-style).
 *
 * Compares the email's domain against a list of common providers using
 * Levenshtein distance. If a known domain is within edit-distance 2 of
 * the typed domain (and not equal), we return a suggested correction.
 *
 * Pure function, no dependencies. See email-mailcheck.util.spec.ts.
 */
const COMMON_DOMAINS = [
  'gmail.com',
  'hotmail.com',
  'outlook.com',
  'yahoo.com',
  'yahoo.fr',
  'orange.fr',
  'free.fr',
  'live.fr',
  'wanadoo.fr',
  'sfr.fr',
  'icloud.com',
  'protonmail.com',
  'me.com',
];

export function suggestEmail(email: string): string | null {
  if (!email || !email.includes('@')) return null;
  const [local, domain] = email.toLowerCase().split('@');
  if (!local || !domain) return null;
  if (COMMON_DOMAINS.includes(domain)) return null;

  for (const known of COMMON_DOMAINS) {
    if (domain !== known && levenshtein(domain, known) <= 2) {
      return `${local}@${known}`;
    }
  }
  return null;
}

function levenshtein(a: string, b: string): number {
  if (a.length === 0) return b.length;
  if (b.length === 0) return a.length;
  const matrix: number[][] = [];
  for (let i = 0; i <= b.length; i++) matrix[i] = [i];
  for (let j = 0; j <= a.length; j++) matrix[0][j] = j;
  for (let i = 1; i <= b.length; i++) {
    for (let j = 1; j <= a.length; j++) {
      if (b.charAt(i - 1) === a.charAt(j - 1)) {
        matrix[i][j] = matrix[i - 1][j - 1];
      } else {
        matrix[i][j] = Math.min(
          matrix[i - 1][j - 1] + 1, // substitution
          matrix[i][j - 1] + 1, // insertion
          matrix[i - 1][j] + 1, // deletion
        );
      }
    }
  }
  return matrix[b.length][a.length];
}
