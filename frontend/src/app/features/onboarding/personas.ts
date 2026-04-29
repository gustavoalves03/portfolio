/**
 * Persona templates for the "quick start" onboarding flow.
 *
 * Each persona ships a small starter set of categories and cares so a brand-new
 * pro can publish in a few clicks. Names/descriptions are i18n keys resolved
 * at creation time using the user's current language — once persisted in DB
 * the pro can edit them freely.
 */

export interface PersonaCareTemplate {
  /** i18n key resolving to the care's display name */
  nameKey: string;
  /** i18n key resolving to a one-line description */
  descKey: string;
  /** Price in cents */
  priceCents: number;
  /** Duration in minutes */
  durationMinutes: number;
}

export interface PersonaCategoryTemplate {
  /** i18n key resolving to the category's display name */
  nameKey: string;
  /** i18n key resolving to a one-line description */
  descKey: string;
  cares: PersonaCareTemplate[];
}

export interface Persona {
  /** Stable identifier, also used as i18n key suffix */
  key: 'face' | 'body' | 'nails' | 'hair';
  /** Material icon name */
  icon: string;
  categories: PersonaCategoryTemplate[];
}

const PREFIX = 'pro.dashboard.quickstart.persona';

export const PERSONAS: readonly Persona[] = [
  {
    key: 'face',
    icon: 'face_retouching_natural',
    categories: [
      {
        nameKey: `${PREFIX}.face.cat.facial.name`,
        descKey: `${PREFIX}.face.cat.facial.desc`,
        cares: [
          {
            nameKey: `${PREFIX}.face.cat.facial.cares.hydra.name`,
            descKey: `${PREFIX}.face.cat.facial.cares.hydra.desc`,
            priceCents: 6500,
            durationMinutes: 60,
          },
          {
            nameKey: `${PREFIX}.face.cat.facial.cares.deep.name`,
            descKey: `${PREFIX}.face.cat.facial.cares.deep.desc`,
            priceCents: 8500,
            durationMinutes: 75,
          },
          {
            nameKey: `${PREFIX}.face.cat.facial.cares.express.name`,
            descKey: `${PREFIX}.face.cat.facial.cares.express.desc`,
            priceCents: 4500,
            durationMinutes: 30,
          },
        ],
      },
      {
        nameKey: `${PREFIX}.face.cat.brows.name`,
        descKey: `${PREFIX}.face.cat.brows.desc`,
        cares: [
          {
            nameKey: `${PREFIX}.face.cat.brows.cares.shape.name`,
            descKey: `${PREFIX}.face.cat.brows.cares.shape.desc`,
            priceCents: 2500,
            durationMinutes: 20,
          },
          {
            nameKey: `${PREFIX}.face.cat.brows.cares.tint.name`,
            descKey: `${PREFIX}.face.cat.brows.cares.tint.desc`,
            priceCents: 3500,
            durationMinutes: 30,
          },
        ],
      },
    ],
  },
  {
    key: 'body',
    icon: 'spa',
    categories: [
      {
        nameKey: `${PREFIX}.body.cat.massage.name`,
        descKey: `${PREFIX}.body.cat.massage.desc`,
        cares: [
          {
            nameKey: `${PREFIX}.body.cat.massage.cares.relax.name`,
            descKey: `${PREFIX}.body.cat.massage.cares.relax.desc`,
            priceCents: 7500,
            durationMinutes: 60,
          },
          {
            nameKey: `${PREFIX}.body.cat.massage.cares.deep.name`,
            descKey: `${PREFIX}.body.cat.massage.cares.deep.desc`,
            priceCents: 9500,
            durationMinutes: 90,
          },
        ],
      },
      {
        nameKey: `${PREFIX}.body.cat.scrub.name`,
        descKey: `${PREFIX}.body.cat.scrub.desc`,
        cares: [
          {
            nameKey: `${PREFIX}.body.cat.scrub.cares.full.name`,
            descKey: `${PREFIX}.body.cat.scrub.cares.full.desc`,
            priceCents: 6500,
            durationMinutes: 45,
          },
        ],
      },
    ],
  },
  {
    key: 'nails',
    icon: 'colorize',
    categories: [
      {
        nameKey: `${PREFIX}.nails.cat.manicure.name`,
        descKey: `${PREFIX}.nails.cat.manicure.desc`,
        cares: [
          {
            nameKey: `${PREFIX}.nails.cat.manicure.cares.classic.name`,
            descKey: `${PREFIX}.nails.cat.manicure.cares.classic.desc`,
            priceCents: 3000,
            durationMinutes: 45,
          },
          {
            nameKey: `${PREFIX}.nails.cat.manicure.cares.gel.name`,
            descKey: `${PREFIX}.nails.cat.manicure.cares.gel.desc`,
            priceCents: 4500,
            durationMinutes: 60,
          },
        ],
      },
      {
        nameKey: `${PREFIX}.nails.cat.pedicure.name`,
        descKey: `${PREFIX}.nails.cat.pedicure.desc`,
        cares: [
          {
            nameKey: `${PREFIX}.nails.cat.pedicure.cares.classic.name`,
            descKey: `${PREFIX}.nails.cat.pedicure.cares.classic.desc`,
            priceCents: 4000,
            durationMinutes: 60,
          },
        ],
      },
    ],
  },
  {
    key: 'hair',
    icon: 'content_cut',
    categories: [
      {
        nameKey: `${PREFIX}.hair.cat.cut.name`,
        descKey: `${PREFIX}.hair.cat.cut.desc`,
        cares: [
          {
            nameKey: `${PREFIX}.hair.cat.cut.cares.women.name`,
            descKey: `${PREFIX}.hair.cat.cut.cares.women.desc`,
            priceCents: 4500,
            durationMinutes: 45,
          },
          {
            nameKey: `${PREFIX}.hair.cat.cut.cares.men.name`,
            descKey: `${PREFIX}.hair.cat.cut.cares.men.desc`,
            priceCents: 3000,
            durationMinutes: 30,
          },
        ],
      },
      {
        nameKey: `${PREFIX}.hair.cat.color.name`,
        descKey: `${PREFIX}.hair.cat.color.desc`,
        cares: [
          {
            nameKey: `${PREFIX}.hair.cat.color.cares.full.name`,
            descKey: `${PREFIX}.hair.cat.color.cares.full.desc`,
            priceCents: 8500,
            durationMinutes: 120,
          },
          {
            nameKey: `${PREFIX}.hair.cat.color.cares.highlights.name`,
            descKey: `${PREFIX}.hair.cat.color.cares.highlights.desc`,
            priceCents: 9500,
            durationMinutes: 150,
          },
        ],
      },
    ],
  },
];

export function findPersona(key: string): Persona | undefined {
  return PERSONAS.find((p) => p.key === key);
}
