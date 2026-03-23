# Story 3.1 — Pretty Face Landing Page

## Overview

Replace the current single-salon home page with a public multi-tenant landing page for Pretty Face. Layout: airy hero with organic shapes, 2x2 colored category cards, horizontal carousel of demo salons, dedicated pro CTA box.

**Approach:** Full refonte of the Home component. Remove all booking logic and CaresStore dependency. Add a lightweight Discover placeholder page. All salon data is hardcoded (demo) — no backend changes.

## Sections

### 1. Hero

- Background: soft pink gradient (#fff5f5 → #ffffff) with organic SVG shapes (translucent circles)
- Title: "Pretty Face" — font-weight 300, letter-spacing 1px
- Subtitle: "Trouve ton prochain soin beauté"
- Search bar: decorative input with placeholder "Rechercher un soin, un salon..."
  - On submit → `router.navigate(['/discover'], { queryParams: { q: searchTerm } })`
  - No actual search logic — just navigation to future discovery page

### 2. Category Cards

Static list of 4 categories displayed in a 2x2 grid (mobile) / 4-column row (desktop):

| Category | Emoji | Color | Fake Count |
|----------|-------|-------|------------|
| Soins visage | 💆 | #f4e1d2 (sable) | 12 salons |
| Ongles | 💅 | #f9d5d3 (rose poudré) | 8 salons |
| Coiffure | ✂️ | #dce8d2 (sauge) | 15 salons |
| Épilation | 🧖 | #d5e5f0 (brume) | 6 salons |

Click → `router.navigate(['/discover'], { queryParams: { category: slug } })`

### 3. Featured Salons (Demo Data)

Horizontal scrollable carousel with scroll-snap. 4-6 hardcoded demo salons:

```typescript
interface FeaturedSalon {
  name: string;
  slug: string;
  category: string;
  city: string;
  rating: number;
  gradient: string; // CSS gradient as photo placeholder
}

const FEATURED_SALONS: FeaturedSalon[] = [
  { name: 'Atelier Lumière', slug: 'atelier-lumiere', category: 'Soins visage', city: 'Paris 11', rating: 4.8, gradient: 'linear-gradient(135deg, #e8d5c4, #f0e0d0)' },
  { name: 'Rose & Thé', slug: 'rose-et-the', category: 'Ongles', city: 'Lyon 6', rating: 4.9, gradient: 'linear-gradient(135deg, #d5e0d2, #e0ead5)' },
  { name: 'Belle Époque', slug: 'belle-epoque', category: 'Coiffure', city: 'Bordeaux', rating: 4.7, gradient: 'linear-gradient(135deg, #d5d5e8, #e0e0f0)' },
  { name: 'Douceur de Soi', slug: 'douceur-de-soi', category: 'Épilation', city: 'Nantes', rating: 4.6, gradient: 'linear-gradient(135deg, #e0d5d8, #f0e5e8)' },
  { name: 'Les Mains d\'Or', slug: 'les-mains-dor', category: 'Ongles', city: 'Toulouse', rating: 4.8, gradient: 'linear-gradient(135deg, #f0e6cc, #f5ecd5)' },
];
```

Each card: gradient placeholder (photo area), salon name, category, star rating, city. Click → `/salon/{slug}` (will 404 for now unless slug matches a real salon — acceptable for demo).

### 4. Pro CTA

Box with subtle background (#fafafa), border-radius 12px:
- Text: "Tu es pro de la beauté ?"
- Action: "Crée ta vitrine gratuitement →" — links to `/register`

## Discover Page (Placeholder)

**New component:** `frontend/src/app/pages/discover/discover-page.component.ts`

- Route: `/discover` (public, no guard)
- Reads query params: `category` and `q`
- Displays a placeholder message: "Bientôt disponible — découvrez les salons {category or search term}"
- Clean design consistent with landing page aesthetic
- Will be replaced by real implementation in Story 3.2

## Routing Changes

Add to `app.routes.ts` (public section):

```
{ path: 'discover', loadComponent: () => import('./pages/discover/discover-page.component').then(m => m.DiscoverPageComponent) }
```

## Visual Design

- **Hero:** gradient bg, organic SVG blobs (translucent circles in pink/green/blue), search bar with box-shadow
- **Categories:** pastel background cards, emoji icon, bold name, muted count — hover: slight brightness decrease (filter: brightness(0.97))
- **Salons:** white cards, gradient placeholder for photo, soft shadow (0 2px 8px rgba(0,0,0,0.06)), horizontal scroll-snap carousel
- **CTA:** #fafafa bg, centered text, accent color for action link (#c06)
- **Responsive:** mobile-first. Categories 2x2 → 4 columns at md breakpoint. Salons carousel stays horizontal at all sizes.
- **Animations:** 150ms transitions on hover (consistent with CLAUDE.md design aesthetic)

## Internationalization

Keys added to both `fr.json` and `en.json`:

```
home.hero.title         — "Pretty Face" / "Pretty Face"
home.hero.subtitle      — "Trouve ton prochain soin beauté" / "Find your next beauty treatment"
home.hero.search        — "Rechercher un soin, un salon..." / "Search for a treatment, a salon..."
home.categories.title   — "Catégories" / "Categories"
home.salons.title       — "Découvre les artistes près de toi" / "Discover artists near you"
home.cta.question       — "Tu es pro de la beauté ?" / "Are you a beauty professional?"
home.cta.action         — "Crée ta vitrine gratuitement" / "Create your storefront for free"
discover.placeholder    — "Bientôt disponible" / "Coming soon"
discover.message        — "Découvrez les salons {{category}}" / "Discover {{category}} salons"
discover.searchMessage  — "Résultats pour «{{query}}»" / "Results for '{{query}}'"
discover.defaultMessage — "Explorez tous les salons de beauté près de chez vous" / "Explore all beauty salons near you"
```

## Home Component Cleanup

Remove from current Home component:
- `CaresStore` provider and injection
- `ServiceCardComponent`, `BookingCalendarComponent`, `BookingTimesComponent` imports
- `BookingsService`, `AuthService`, `AuthModalComponent`, `MatDialog` imports (unless needed for other purposes)
- `BookingState` interface and all booking methods (`onBookingRequested`, `onDaySelected`, `onTimeSelected`, `confirmBooking`, `createBooking`, etc.)
- `MatButtonModule`, `MatSnackBar` imports (if no longer used)

## Testing

### home.spec.ts
- Hero section renders with title, subtitle, search bar
- Category cards render (4 cards with correct names)
- Category card click navigates to `/discover?category=slug`
- Featured salons render (5 salon cards)
- Salon card click navigates to `/salon/{slug}`
- Pro CTA renders and links to `/register`
- Search bar submit navigates to `/discover?q=term`

### discover-page.component.spec.ts
- Placeholder message renders
- Reads `category` query param and displays it
- Reads `q` query param and displays it
- Default message when no params

## Security

- All routes are public (no auth guard)
- No user data involved
- No API calls
- Demo data only — no PII
