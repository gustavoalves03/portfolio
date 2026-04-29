import { PERSONAS, findPersona } from './personas';

describe('PERSONAS data', () => {
  it('exposes exactly 4 personas', () => {
    expect(PERSONAS.length).toBe(4);
  });

  it('persona keys are unique', () => {
    const keys = PERSONAS.map((p) => p.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('persona keys are stable identifiers', () => {
    expect(PERSONAS.map((p) => p.key)).toEqual(['face', 'body', 'nails', 'hair']);
  });

  it('every persona has at least one category', () => {
    for (const p of PERSONAS) {
      expect(p.categories.length).withContext(`persona=${p.key}`).toBeGreaterThan(0);
    }
  });

  it('every category has at least one care', () => {
    for (const p of PERSONAS) {
      for (const cat of p.categories) {
        expect(cat.cares.length)
          .withContext(`persona=${p.key} cat=${cat.nameKey}`)
          .toBeGreaterThan(0);
      }
    }
  });

  it('all prices are strictly positive', () => {
    for (const p of PERSONAS) {
      for (const cat of p.categories) {
        for (const care of cat.cares) {
          expect(care.priceCents)
            .withContext(`persona=${p.key} care=${care.nameKey}`)
            .toBeGreaterThan(0);
        }
      }
    }
  });

  it('all durations are strictly positive', () => {
    for (const p of PERSONAS) {
      for (const cat of p.categories) {
        for (const care of cat.cares) {
          expect(care.durationMinutes)
            .withContext(`persona=${p.key} care=${care.nameKey}`)
            .toBeGreaterThan(0);
        }
      }
    }
  });

  it('every i18n key starts with the expected prefix', () => {
    const prefix = 'pro.dashboard.quickstart.persona';
    for (const p of PERSONAS) {
      for (const cat of p.categories) {
        expect(cat.nameKey.startsWith(prefix)).toBeTrue();
        expect(cat.descKey.startsWith(prefix)).toBeTrue();
        for (const care of cat.cares) {
          expect(care.nameKey.startsWith(prefix)).toBeTrue();
          expect(care.descKey.startsWith(prefix)).toBeTrue();
        }
      }
    }
  });

  it('every persona has a Material icon', () => {
    for (const p of PERSONAS) {
      expect(p.icon).toBeTruthy();
      expect(typeof p.icon).toBe('string');
    }
  });

  describe('findPersona', () => {
    it('returns the matching persona', () => {
      expect(findPersona('face')?.key).toBe('face');
      expect(findPersona('hair')?.key).toBe('hair');
    });

    it('returns undefined for an unknown key', () => {
      expect(findPersona('unknown')).toBeUndefined();
      expect(findPersona('')).toBeUndefined();
    });
  });
});
