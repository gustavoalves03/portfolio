# Home & Discover Pages Redesign

## Context

Pretty Face is a beauty salon management app. The home and discover pages need a redesign:
- Remove hardcoded fictitious categories (Soins visage, Ongles, etc.)
- Better salon presentation with city/location data
- Add a map (Leaflet/OpenStreetMap) showing salons relative to the client's position
- Geocode salon addresses using Nominatim (free, no API key)

## Design: Home — Vitrine immersive (Option B)

### Structure (top to bottom)

**1. Hero plein écran**
- Background: warm nacre gradient (peach/sand tones) with subtle overlay
- Title "Pretty Face" white with text-shadow + subtitle
- Search bar with white/frosted background, rounded, centered
- Search redirects to `/discover?q=...`

**2. Section "Près de toi"**
- Grid layout: mini-carte (160px wide) on the left + 2-3 closest salons on the right
- Mini map: small Leaflet map with salon pins + user dot (interactive)
- Salon cards: compact horizontal (avatar 36px + name + city + distance hint)
- Requires geolocation — if denied, show all salons without distance

**3. Section "Derniers posts"**
- Horizontal row of 3 post thumbnails (100x130px, rounded, gradient fallback)
- Each shows post type overlay at bottom (e.g. "Avant / Après")
- Tapping navigates to the salon's post tab
- Requires a new backend endpoint: `GET /api/public/posts/recent?limit=6`

**4. CTA Pro**
- Light banner: "Tu es pro ? Crée ta vitrine gratuitement →"

### What's removed
- Hardcoded categories grid
- Horizontal carousel layout

## Design: Discover — Split View Airbnb (Option B)

### Desktop (>= 768px)

Side-by-side layout using CSS grid:
- **Left panel (50%):** search bar at top + scrollable salon list (overflow-y: auto, full viewport height minus header)
- **Right panel (50%):** Leaflet map, sticky (position: sticky; top: 0; height: 100vh)

### Interactions
- **Hover** a salon card → border turns pink (#c06) with shadow, corresponding map marker scales up and shows popup
- **Mouse leave** → card and marker return to normal
- **Click map marker** → popup with salon name + city; clicking popup navigates to `/salon/{slug}`
- **Click salon card** → navigates to `/salon/{slug}`

### Map details
- Pink pin markers (brand gradient, CSS div icon) for salons
- Blue dot for client geolocation
- Auto-fit bounds to show all markers + user position
- Default: France center (46.6, 2.3) zoom 6 if no markers

### Mobile (< 768px)
- Single column: search → map (250px height) → salon list below
- No hover interactions (touch)
- Map and list are independent (no sync needed on mobile)

### Salon cards (in list)
Horizontal layout: avatar (48-56px rounded square) + text block:
- **Name** (bold, 14px)
- **City** (📍 icon, gray, 11px)
- **Description** (truncated 100 chars, 11px, gray)
- **Category chips** (small beige pills)

## Backend changes

### SalonCardResponse DTO
Add two fields:
- `addressCity: String` — city name from tenant entity
- `fullAddress: String` — concatenation of street + postal code + city (for geocoding)

Already implemented in current code.

### New endpoint: Recent public posts
- `GET /api/public/posts/recent?limit=6`
- Returns the N most recent posts across all active tenants
- Response: list of objects with `id`, `type`, `caption`, `thumbnailUrl`, `salonName`, `salonSlug`, `createdAt`
- Used by the home page "Derniers posts" section

## Frontend changes

### Model (`discovery.model.ts`)
`SalonCard` already has `addressCity` and `fullAddress` fields (already implemented).

### Home page (`pages/home/`)
- Remove `CATEGORIES` constant and category-related code
- New hero: warm gradient background, white text, frosted search bar
- New "Près de toi" section: mini-map + nearby salon cards grid
- New "Derniers posts" section: horizontal scroll of post thumbnails
- Add Leaflet mini-map (small, non-interactive or lightly interactive)
- Fetch recent posts from new endpoint

### Discover page (`pages/discover/`)
- Remove `CATEGORIES` constant and category chips
- Implement split-view layout:
  - CSS grid `grid-template-columns: 1fr 1fr` on desktop
  - Single column on mobile (media query < 768px)
- Left panel: search + scrollable list (height: calc(100vh - header); overflow-y: auto)
- Right panel: Leaflet map (position: sticky; top: 0; height: 100vh)
- Hover sync: mouseenter/mouseleave on salon cards trigger marker highlight
- Dynamic import of leaflet for code splitting
- Geocoding with Nominatim + in-memory cache

### Dependencies
- `leaflet` + `@types/leaflet` (already installed)
- Leaflet CSS in angular.json styles array (already configured)

## Translations

New keys:
- `discover.yourLocation` — "Vous êtes ici" / "You are here"
- `home.salons.discoverAll` — "Voir tous les salons" / "See all salons"
- `home.nearYou` — "Près de toi" / "Near you"
- `home.recentPosts` — "Derniers posts" / "Recent posts"

Removed keys (unused):
- `discover.allCategories`
- `discover.backHome`
- `home.categories.title`

## What's NOT changing
- Salon page (detail view)
- Pro pages
- Backend salon entity (no schema changes)
- Authentication
- Other pages (bookings, about, etc.)
