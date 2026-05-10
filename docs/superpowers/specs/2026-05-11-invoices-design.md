# Factures — Préparation pré-Stripe

**Date** : 2026-05-11
**Branche cible** : `feat/invoices-prep-stripe` (worktree depuis `main`)
**Dépend de** : rien. **Sera complété par** : `project_pending_payments.md` (3 PRs Stripe).

## Objectif

Préparer toute la couche factures (UI + DB + génération PDF) maintenant, pour qu'au moment où Stripe sera connecté, il ne reste qu'à brancher les webhooks. En mode dev, des seeders peuplent la DB avec des factures fictives pour valider l'expérience de bout en bout sur les trois espaces (pro, pro→clients, client).

## Non-objectifs

- Aucune intégration Stripe SDK dans ce chantier (zéro `stripe-java`, zéro webhook handler).
- Aucune logique de paiement, de numérotation officielle, de TVA dynamique (les valeurs viendront de Stripe Tax / Stripe Connect une fois branchés).
- Pas de panneau admin LuxPretty.
- Pas de filtres/export sophistiqués sur les listes (recherche par numéro + filtre statut/année suffisent).
- Pas de stockage R2 des PDFs (génération à la volée).
- Pas de Customer Portal Stripe (chantier paiements PR1).

## Décisions clés

| Sujet | Décision |
|---|---|
| Types de factures préparés | **Pro** (abo SaaS LuxPretty → salon) **+ Client** (salon → ses clients : no-show, soins futurs) |
| Source de vérité future | **Stripe** — webhook → cache local. Notre table = cache. Notre PDF = surcouche brandée / fallback. |
| Émetteur légal facture Pro | **LuxPretty SARL** (Stripe Tax génère la facture conforme TVA UE) |
| Émetteur légal facture Client | **Le salon** (Stripe Connect Standard ; le salon possède son compte Stripe) |
| Modèle DB | **Deux tables séparées** (`pro_invoice`, `client_invoice`) — divergence métier marquée |
| Schéma | **Schéma tenant** (par salon) comme bookings/cares |
| Lib PDF | **OpenPDF + Flying Saucer + Thymeleaf** (HTML→PDF) — open source, maintenable |
| Direction visuelle PDF | **Direction B** : en-tête dégradé rose nacré + monogramme LXP, cellules carte avec ombres douces |
| Layout pages liste | **Tableau dense** : filtres en haut (recherche N°, statut, année), lignes compactes avec badges colorés |
| Données de test | **Seeders backend** (`@Profile("dev")`, idempotent) |
| Stockage PDFs | **Génération à la volée** côté backend, pas de cache disque/R2 |

## Architecture backend

```
backend/src/main/java/com/luxpretty/app/
├── proinvoice/
│   ├── domain/        ProInvoice.java
│   ├── repo/          ProInvoiceRepository.java
│   ├── app/           ProInvoiceService.java, ProInvoicePdfRenderer.java, ProInvoiceDevSeeder.java
│   └── web/           ProInvoiceController.java, dto/, mapper/
│
├── clientinvoice/
│   ├── domain/        ClientInvoice.java, ClientInvoiceLine.java
│   ├── repo/          ClientInvoiceRepository.java
│   ├── app/           ClientInvoiceService.java, ClientInvoicePdfRenderer.java, ClientInvoiceDevSeeder.java
│   └── web/           ClientInvoiceController.java (pro), MyInvoiceController.java (client), dto/, mapper/
│
└── invoice/
    └── pdf/           HtmlToPdfRenderer.java (Thymeleaf + Flying Saucer)
```

Le package `invoice/pdf/` est **transverse**, pas une feature. Il porte uniquement l'infrastructure de rendu HTML→PDF réutilisée par les deux features factures.

### Tables

#### `pro_invoice` (cache des factures Stripe Tax)

| Colonne | Type | Note |
|---|---|---|
| `id` | UUID | PK |
| `tenant_id` | UUID | FK Tenant |
| `stripe_invoice_id` | varchar | unique, nullable tant que Stripe non branché |
| `number` | varchar | numéro humain (placeholder dev : `PRO-2026-0042`) |
| `issued_at` | timestamp | |
| `period_start` / `period_end` | date | mois facturé |
| `amount_subtotal` / `amount_tax` / `amount_total` | numeric(12,2) | |
| `currency` | char(3) | `EUR` |
| `tax_rate` | numeric(5,2) | snapshot taux (ex `17.00`) |
| `status` | enum | `DRAFT`, `OPEN`, `PAID`, `UNCOLLECTIBLE`, `VOID` (alignés Stripe) |
| `hosted_invoice_url` | text | URL Stripe (nullable en dev) |
| `pdf_url` | text | URL PDF Stripe (nullable en dev) |
| `customer_snapshot` | jsonb | snapshot salon (nom, adresse, TVA) au moment d'émission |
| `created_at` / `updated_at` | timestamp | |

Index : `tenant_id`, `stripe_invoice_id` (unique partiel sur not null), `issued_at desc`.

#### `client_invoice`

| Colonne | Type | Note |
|---|---|---|
| `id` | UUID | PK |
| `tenant_id` | UUID | FK Tenant (salon émetteur) |
| `booking_id` | UUID | FK nullable vers Booking (lien no-show) |
| `client_user_id` | UUID | FK nullable vers User (client) |
| `stripe_payment_intent_id` | varchar | nullable tant que non branché |
| `stripe_invoice_id` | varchar | nullable (Stripe Connect peut générer ou non) |
| `number` | varchar | séquence par tenant : `{TENANT_SLUG}-{YEAR}-{4digit}` |
| `issued_at` | timestamp | |
| `kind` | enum | `NO_SHOW_FEE`, `CARE_PAYMENT` (extensible) |
| `amount_subtotal` / `amount_tax` / `amount_total` | numeric(12,2) | |
| `currency` | char(3) | |
| `tax_rate` | numeric(5,2) | |
| `status` | enum | `PAID`, `REFUNDED`, `FAILED`, `PENDING` |
| `emitter_snapshot` | jsonb | snapshot salon (nom, adresse, TVA, RCS) |
| `client_snapshot` | jsonb | snapshot client (nom, adresse) |
| `created_at` / `updated_at` | timestamp | |

Index : `tenant_id`, `client_user_id`, `booking_id`, `stripe_payment_intent_id` (unique partiel), `issued_at desc`.

#### `client_invoice_line`

| Colonne | Type |
|---|---|
| `id` | UUID PK |
| `client_invoice_id` | UUID FK |
| `description` | text |
| `quantity` | numeric(10,2) |
| `unit_price_ht` | numeric(12,2) |
| `total_ht` | numeric(12,2) |
| `position` | int (ordre) |

### Endpoints

| Méthode | Route | Auth | Description |
|---|---|---|---|
| GET | `/api/pro/invoices` | pro | liste paginée des factures Pro du tenant |
| GET | `/api/pro/invoices/{id}` | pro | détail |
| GET | `/api/pro/invoices/{id}/pdf` | pro | PDF binaire (Content-Disposition: attachment) |
| GET | `/api/pro/client-invoices` | pro | liste paginée des factures émises par le salon |
| GET | `/api/pro/client-invoices/{id}` | pro | détail |
| GET | `/api/pro/client-invoices/{id}/pdf` | pro | PDF |
| GET | `/api/me/invoices` | client auth | liste des factures du client (filtrée `client_user_id`) |
| GET | `/api/me/invoices/{id}` | client auth | détail |
| GET | `/api/me/invoices/{id}/pdf` | client auth | PDF |

Pagination Spring standard (`Pageable`). Filtres query params : `status`, `year`, `q` (recherche par numéro).

### Génération PDF

```
backend/src/main/resources/templates/invoice/
├── pro-invoice.html
├── client-invoice.html
└── _shared/
    ├── styles.css       (direction B : header rose nacré)
    └── footer.html
backend/src/main/resources/fonts/
├── CormorantGaramond-Medium.ttf
├── Inter-Regular.ttf
└── Inter-SemiBold.ttf
```

`HtmlToPdfRenderer.render(String templateName, Map<String, Object> context) → byte[]` :
1. Thymeleaf produit le HTML à partir du template + contexte.
2. Flying Saucer (`ITextRenderer`) consomme le HTML et produit le PDF.
3. Polices TTF embarquées pour rendu déterministe.

`ProInvoicePdfRenderer.render(ProInvoice) → byte[]` et `ClientInvoicePdfRenderer.render(ClientInvoice) → byte[]` mappent l'entité vers le contexte Thymeleaf et appellent `HtmlToPdfRenderer`.

### Multi-tenancy & sécurité

- Tables dans le **schéma tenant** (comme bookings/cares).
- Toutes les requêtes JPA filtrent sur `tenant_id` via Hibernate filter (déjà actif).
- `/api/me/invoices` filtre additionnellement par `client_user_id = currentUser.id`.
- 404 si la facture n'existe pas ou n'appartient pas au tenant/utilisateur.
- 403 si un user pro tente d'accéder à un endpoint `/api/me/invoices/{id}` d'un autre client.
- Snapshots (`customer_snapshot`, `emitter_snapshot`, `client_snapshot`) figent les coordonnées au moment de l'émission : les modifications ultérieures du profil salon/client ne réécrivent pas les vieilles factures.

## Architecture frontend

```
frontend/src/app/features/
├── pro-invoices/                    → /pro/factures
│   ├── pages/
│   │   ├── pro-invoices-list.component.ts
│   │   └── pro-invoice-detail.component.ts
│   ├── models/pro-invoice.model.ts
│   ├── services/pro-invoices.service.ts
│   └── store/pro-invoices.store.ts
│
├── client-invoices-pro/             → /pro/facturation-clients
│   └── (même structure)
│
└── client-invoices-me/              → /client/factures
    └── (même structure)
```

Pattern par feature aligné sur l'existant : `signalStore` avec `withState` + `withRequestStatus` + `withMethods` (`getAll`, `getOne`, `downloadPdf`). Composant liste basé sur `shared/uis/crud-table` (déjà en place). Layout 1 (tableau dense) : colonnes N°, Date, Période/Type, Montant TTC, Statut (badge coloré), Actions (Voir + PDF).

**Téléchargement PDF** : appel HTTP `responseType: 'blob'` → déclenche un download navigateur (FileSaver-like). Nom de fichier : `facture-{numero}.pdf`.

**i18n** : nouvelles clés sous `invoices.pro.*`, `invoices.clientEmitted.*`, `invoices.clientReceived.*`, `invoices.common.*` (status labels, actions, headers). Ajoutées dans `fr.json` ET `en.json`.

**Routes & nav** :
- `/pro/factures` (guard pro)
- `/pro/facturation-clients` (guard pro)
- `/client/factures` (guard auth)
- Liens dans la nav pro et la nav client.

## Seeders (mode dev uniquement)

Deux seeders dédiés (`@Component @Profile("dev") implements ApplicationRunner`), un par feature, tous deux idempotents :

**`ProInvoiceDevSeeder`** (dans `proinvoice/app/`) :
- Pour chaque tenant existant, si `pro_invoice` vide pour ce tenant : crée 6 `ProInvoice` (6 derniers mois, 1 `OPEN`, 5 `PAID`).

**`ClientInvoiceDevSeeder`** (dans `clientinvoice/app/`) :
- Pour chaque tenant existant, si `client_invoice` vide pour ce tenant : crée 3-4 `ClientInvoice` `NO_SHOW_FEE` (2 `PAID`, 1 `REFUNDED`, 1 `FAILED`), liées à des bookings existants si possible, sinon `booking_id` null.

Idempotence : chaque seeder vérifie l'existence de factures pour le tenant avant d'écrire ; deuxième run = no-op. Pas de bouton "générer une facture de test" dans l'UI.

## Tests

### Backend

- `ProInvoiceServiceTests` : récupération par tenant, filtrage, accès cross-tenant interdit.
- `ClientInvoiceServiceTests` : idem + filtre par `client_user_id` pour `/api/me/invoices`.
- `ProInvoicePdfRendererTests` / `ClientInvoicePdfRendererTests` : génération PDF ne plante pas, taille > 0, contenu HTML intermédiaire contient marqueurs (numéro, montant).
- `HtmlToPdfRendererTests` : test unitaire du composant transverse avec template minimal.
- `ProInvoiceControllerTests` / `ClientInvoiceControllerTests` / `MyInvoiceControllerTests` (`@WebMvcTest`) : auth requise, isolation tenant, format des réponses.
- `InvoiceDevSeederTests` : idempotence (deuxième run ne duplique pas).

### Frontend

- Specs pour chaque store : `getAll` met à jour l'état et le statut ; `downloadPdf` déclenche le bon endpoint.
- Specs pour les list components : affichage des badges de statut selon `status`.
- Pas de test du PDF lui-même côté front.

## Migration Flyway

`V{n}__create_invoice_tables.sql` dans le schéma tenant template :
- `pro_invoice` + index
- `client_invoice` + index
- `client_invoice_line` + index FK
- Énums : `pro_invoice_status`, `client_invoice_status`, `client_invoice_kind`

Tenants existants : la migration s'applique automatiquement via le flow multi-tenant en place.

> ⚠️ Memo `project_pending_flyway.md` : les tenants legacy doivent être baselined avant `ddl-auto=validate`. Pas un blocker pour ce chantier mais à garder en tête lors du déploiement.

## Error handling

- 404 si facture absente ou non autorisée (pas de fuite d'information).
- 403 si user pro tente `/api/me/invoices/{id}` d'un autre client.
- PDF en échec → 500 avec log structuré (template manquant, police absente). Côté front : toast d'erreur i18n générique.
- Pages liste vides : empty state (illustration + texte i18n "Aucune facture pour le moment").

## Plan de livraison

1 worktree (`feat/invoices-prep-stripe`), 4 commits/PR séquentiels.

| # | Scope | Estimation |
|---|---|---|
| **PR1** | Backend ProInvoice : entité + repo + service + controller + seeder + tests (sans PDF) | ~0.5 j |
| **PR2** | Backend ClientInvoice : entité + lines + repo + service + 2 controllers (pro + me) + seeder + tests | ~0.5 j |
| **PR3** | Composant `HtmlToPdfRenderer` transverse + 2 renderers spécifiques + templates Thymeleaf (direction B) + tests | ~1 j |
| **PR4** | Frontend : 3 features Angular complètes + routes + nav + i18n (fr/en) + specs | ~1.5 j |

**Total ~3.5 jours.**

## Out of scope (rappel explicite)

- Webhooks Stripe → chantier paiements (`project_pending_payments.md`, 3 PRs).
- Customer Portal Stripe → chantier paiements PR1.
- Refund button salon → chantier paiements PR3.
- Numérotation officielle conforme TVA UE → Stripe Tax (PR1 paiements).
- Stockage R2 des PDFs → génération à la volée suffit.
- Panneau admin LuxPretty.

## Prérequis débloqués / restants

- ✅ Clés API Stripe disponibles (utilisateur confirmé 2026-05-11). Aucune utilisation dans ce chantier — réservées au chantier paiements.
- ⏳ Numérotation officielle : à finaliser quand Stripe Connect / Stripe Tax sera branché.
