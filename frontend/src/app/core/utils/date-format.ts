export type DateInput = string | Date | number | null | undefined;

export function formatDate(input: DateInput): string {
  const d = toDate(input);
  if (!d) return '';
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

export function formatDateTime(input: DateInput): string {
  const d = toDate(input);
  if (!d) return '';
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function toYMD(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function addDays(d: Date, n: number): Date {
  const result = new Date(d);
  result.setDate(result.getDate() + n);
  return result;
}

export function parseYMD(ymd: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  const d = new Date(year, month - 1, day);
  if (d.getFullYear() !== year || d.getMonth() !== month - 1 || d.getDate() !== day) return null;
  return d;
}

function toDate(input: DateInput): Date | null {
  if (input === null || input === undefined) return null;
  const d = input instanceof Date ? new Date(input) : new Date(input);
  if (isNaN(d.getTime())) return null;
  return d;
}

function pad(n: number): string {
  return n.toString().padStart(2, '0');
}
