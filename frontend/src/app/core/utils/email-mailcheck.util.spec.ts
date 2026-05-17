import { suggestEmail } from './email-mailcheck.util';

describe('suggestEmail', () => {
  it('returns null for valid common domains', () => {
    expect(suggestEmail('user@gmail.com')).toBeNull();
    expect(suggestEmail('user@hotmail.com')).toBeNull();
    expect(suggestEmail('user@outlook.com')).toBeNull();
    expect(suggestEmail('user@orange.fr')).toBeNull();
  });

  it('suggests gmail.com for common gmail typos', () => {
    expect(suggestEmail('user@gmai.com')).toBe('user@gmail.com');
    expect(suggestEmail('user@gmial.com')).toBe('user@gmail.com');
    expect(suggestEmail('user@gnail.com')).toBe('user@gmail.com');
    expect(suggestEmail('user@gmal.com')).toBe('user@gmail.com');
  });

  it('suggests hotmail.com for hotmail typos', () => {
    expect(suggestEmail('user@hotmal.com')).toBe('user@hotmail.com');
    expect(suggestEmail('user@hotmial.com')).toBe('user@hotmail.com');
    expect(suggestEmail('user@hotnail.com')).toBe('user@hotmail.com');
  });

  it('suggests outlook.com for outlook typos', () => {
    expect(suggestEmail('user@outlok.com')).toBe('user@outlook.com');
    expect(suggestEmail('user@outloo.com')).toBe('user@outlook.com');
  });

  it('suggests orange.fr / free.fr for FR provider typos', () => {
    expect(suggestEmail('user@orang.fr')).toBe('user@orange.fr');
    expect(suggestEmail('user@freer.fr')).toBe('user@free.fr');
  });

  it('returns null for completely different domains', () => {
    expect(suggestEmail('user@randomcompany.io')).toBeNull();
    expect(suggestEmail('user@mybusiness.dev')).toBeNull();
  });

  it('returns null for empty / malformed input', () => {
    expect(suggestEmail('')).toBeNull();
    expect(suggestEmail('not-an-email')).toBeNull();
    expect(suggestEmail('@gmail.com')).toBeNull();
    expect(suggestEmail('user@')).toBeNull();
  });

  it('is case-insensitive on the domain', () => {
    expect(suggestEmail('User@Gmai.COM')).toBe('user@gmail.com');
  });
});
