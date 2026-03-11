---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
status: 'complete'
completedAt: '2026-03-05'
inputDocuments:
  - path: '_bmad-output/planning-artifacts/prd.md'
    type: 'prd'
    description: 'PRD complet Pretty Face — SaaS beauté multi-tenant, 56 FRs, 11 NFRs'
  - path: '_bmad-output/planning-artifacts/architecture.md'
    type: 'architecture'
    description: 'Architecture Decision Document Pretty Face — 7 décisions, patterns complets, structure frontend/backend'
  - path: '_bmad-output/planning-artifacts/ux-design-specification.md'
    type: 'ux'
    description: 'UX Design Specification Pretty Face — Information architecture, design challenges, personas'
---

# Pretty Face - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for Pretty Face, decomposing the requirements from the PRD, UX Design, and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: A professional can create an account with email/password
FR2: A professional can log in via Google OAuth
FR3: A professional can log in via Facebook OAuth
FR4: A professional can log in via Apple OAuth
FR5: A client can create an account with email/password
FR6: A client can log in via Google OAuth
FR7: A client can log in via Facebook OAuth
FR8: A client can log in via Apple OAuth
FR9: A user can reset their password by email
FR10: A user can log out of their account
FR11: A professional can configure their establishment information (name, logo, description)
FR12: A professional can create service categories
FR13: A professional can create services with name, description, price, and duration
FR14: A professional can associate a service with a category
FR15: A professional can add photos to a service
FR16: A professional can modify an existing service
FR17: A professional can delete a service
FR18: A professional can reorganize the display order of services
FR19: A professional can activate/deactivate a service (without deleting it)
FR20: A professional can define their opening hours by day of the week
FR21: A professional can block a specific time slot (occasional unavailability)
FR22: A professional can unblock a previously blocked time slot
FR23: A professional can cancel all appointments of a day (illness, emergency)
FR24: The system automatically notifies clients affected by a group cancellation
FR25: A visitor can view a salon's storefront without being logged in
FR26: A visitor can see the list of services with prices and durations
FR27: A visitor can see photos associated with services
FR28: A visitor can see establishment information (name, description)
FR29: A client can see available time slots for a given service
FR30: A client can select a time slot and confirm their booking
FR31: A client must be logged in to finalize a booking
FR32: The system sends a booking confirmation by email to the client
FR33: The system sends a new booking notification to the professional
FR34: The system sends a reminder by email to the client the day before the appointment
FR35: A client can see the list of their upcoming appointments
FR36: A client can see the list of their past appointments
FR37: A client can cancel an upcoming appointment
FR38: A client can reschedule an appointment to another available slot
FR39: The system notifies the professional when a client cancels or reschedules
FR40: A professional can see the list of their appointments for the day
FR41: A professional can see their schedule in a calendar view (week/month)
FR42: A professional can see the details of an appointment (client, service, time)
FR43: A professional can cancel an existing appointment
FR44: The system automatically notifies the client when the professional cancels
FR45: A professional can connect their Google Calendar account
FR46: A professional can export their appointments in iCal format
FR47: New appointments are automatically synchronized with the connected calendar
FR48: A professional can see their revenue for the current month
FR49: A professional can see the number of bookings for the current month
FR50: A professional can see the evolution of their revenue month by month
FR51: A professional can see the evolution of their bookings month by month
FR52: A professional can see their progression rate in percentage (revenue and bookings count) compared to the previous month
FR53: The system isolates each salon's data in a separate schema
FR54: A professional can only access their own salon's data
FR55: A client can book appointments at multiple different salons
FR56: The system automatically creates a new schema when a salon registers

### NonFunctional Requirements

NFR1: The system maintains ≥ 99.5% availability during booking hours (8am–8pm local time) measured by monthly cloud monitoring
NFR2: Recovery time after incident (RTO) is less than 30 minutes for critical incidents (booking service interruption)
NFR3: Client storefront pages load in less than 2 seconds at P95 on a 4G mobile connection, measured by Lighthouse under real conditions
NFR4: Booking API calls (creation, confirmation) respond in less than 500ms at P95 under normal load (≤ 50 requests/min)
NFR5: The pro dashboard loads in less than 3 seconds at P95 with up to 500 historical bookings, measured by APM
NFR6: Client data (name, email, booking history) is encrypted at rest (AES-256) and in transit (TLS 1.2+), compliant with standard SaaS practices
NFR7: OAuth2 authentication uses limited-lifetime tokens (access token ≤ 1h, refresh token ≤ 30 days) with automatic rotation
NFR8: Any failed login attempt is limited to 5 consecutive tries before a 15-minute temporary lockout (brute-force protection)
NFR9: Zero data leakage between tenants: no query can access data from another salon schema, verified by automated isolation tests at each deployment
NFR10: Creating a new tenant schema completes in less than 10 seconds during salon registration
NFR11: The architecture supports scaling to 100 simultaneous active salons without degrading the performance SLAs defined above, validated by load tests

### Additional Requirements

**From Architecture:**
- Brownfield project — existing FleurDeCoquillage codebase is the starting point (no migration, incremental evolution)
- Multi-tenant infrastructure must be implemented first before any tenant-specific features (TenantContext, AbstractRoutingDataSource, TenantFilter, TenantSchemaManager)
- Tenant resolution via path prefix (`/salon/{slug}`) for public storefronts and JWT claim for authenticated pro endpoints
- Email service integration via Hostinger SMTP + Spring Mail with Thymeleaf templates (booking confirmation, J-1 reminder, cancellation)
- Calendar sync via iCal export (`.ics` file download + subscription URL `/api/salon/{slug}/calendar.ics`) — ical4j library
- Caching via Caffeine + HTTP headers (`Cache-Control`) for public storefront endpoints (TTL 5-15 min)
- Monitoring via Spring Boot Actuator + Sentry (backend) + Sentry Angular SDK (frontend)
- OAuth2 extension: Google already works, Facebook and Apple to be added via Spring Security configuration
- Implementation sequence: (1) Multi-tenant infra → (2) Tenant entity & public APIs → (3) Landing & Discovery → (4) Salon storefront → (5) Pro Space with dashboard
- All new UI text must have FR + EN translations (Transloco)
- Use modern Angular patterns: signals, `@if`/`@for`, `inject()`, standalone components
- Feature-first structure for both frontend and backend

**From UX Design:**
- Home is client-first: single entry point for all users; pros access their space via login from this page
- Public home structure (scroll): Hero → Care categories (illustrated, clickable) → Featured salons feed → Discreet pro CTA
- Pro onboarding must be completed in < 20 minutes; storefront stays in "draft" (private) until minimum configuration is done
- Mobile-first design for client-facing pages (booking tunnel must be flawless on small screens)
- Responsive design required for all pages (mobile + desktop)
- Dashboard "wow moment": stats visualization must be visually impactful (progress charts, percentage indicators)
- Public salon storefront has distinct "beauty & elegance" aesthetic compared to the pro backoffice
- Status clarity: bookings (PENDING/CONFIRMED/CANCELLED), cares (ACTIVE/INACTIVE), Post to Play status — all visible at a glance
- Accessibility: WCAG AA compliance (contrast, visible focus, ARIA labels, keyboard navigation)
- Micro-animations: 150–250ms transitions, never flashy
- Cookie consent banner at first visit; analytics cookies opt-in only

### FR Coverage Map

FR1: Epic 1 - Pro account creation (email/password)
FR2: Epic 1 - Pro login via Google OAuth
FR3: Epic 1 - Pro login via Facebook OAuth
FR4: Epic 1 - Pro login via Apple OAuth
FR5: Epic 5 - Client account creation (email/password)
FR6: Epic 5 - Client login via Google OAuth
FR7: Epic 5 - Client login via Facebook OAuth
FR8: Epic 5 - Client login via Apple OAuth
FR9: Epic 1 - Password reset (shared between pro/client)
FR10: Epic 1 - Logout (shared between pro/client)
FR11: Epic 2 - Configure establishment info (name, logo, description)
FR12: Epic 2 - Create service categories
FR13: Epic 2 - Create services (name, description, price, duration)
FR14: Epic 2 - Associate service with category
FR15: Epic 2 - Add photos to a service
FR16: Epic 2 - Modify an existing service
FR17: Epic 2 - Delete a service
FR18: Epic 2 - Reorganize display order of services
FR19: Epic 2 - Activate/deactivate a service
FR20: Epic 4 - Define opening hours by day of week
FR21: Epic 4 - Block a specific time slot
FR22: Epic 4 - Unblock a previously blocked slot
FR23: Epic 4 - Cancel all appointments of a day
FR24: Epic 4 - Automatically notify clients of group cancellation
FR25: Epic 3 - Visitor views salon storefront without login
FR26: Epic 3 - Visitor sees service list with prices and durations
FR27: Epic 3 - Visitor sees photos associated with services
FR28: Epic 3 - Visitor sees establishment information
FR29: Epic 5 - Client sees available slots for a service
FR30: Epic 5 - Client selects slot and confirms booking
FR31: Epic 5 - Client must be logged in to finalize booking
FR32: Epic 5 - System sends booking confirmation email to client
FR33: Epic 5 - System sends new booking notification to pro
FR34: Epic 5 - System sends reminder email to client the day before
FR35: Epic 6 - Client sees list of upcoming appointments
FR36: Epic 6 - Client sees list of past appointments
FR37: Epic 6 - Client cancels an upcoming appointment
FR38: Epic 6 - Client reschedules to another available slot
FR39: Epic 6 - System notifies pro of client cancellation/reschedule
FR40: Epic 6 - Pro sees list of today's appointments
FR41: Epic 6 - Pro sees schedule in calendar view (week/month)
FR42: Epic 6 - Pro sees appointment details (client, service, time)
FR43: Epic 6 - Pro cancels an existing appointment
FR44: Epic 6 - System automatically notifies client when pro cancels
FR45: Epic 7 - Pro connects Google Calendar account
FR46: Epic 7 - Pro exports appointments in iCal format
FR47: Epic 7 - New appointments synchronized with connected calendar
FR48: Epic 8 - Pro sees current month revenue
FR49: Epic 8 - Pro sees current month booking count
FR50: Epic 8 - Pro sees revenue evolution month by month
FR51: Epic 8 - Pro sees booking evolution month by month
FR52: Epic 8 - Pro sees progression rate vs previous month (revenue + bookings)
FR53: Epic 1 - System isolates each salon's data in separate schema
FR54: Epic 1 - Pro can only access their own salon's data
FR55: Epic 3 - Client can book at multiple different salons
FR56: Epic 1 - System automatically creates new schema on salon registration

## Epic List

### Epic 1: Salon Registration & Multi-Tenant Setup
A professional can register on Pretty Face, authenticate (email/password or OAuth2), and obtain their own isolated salon space ready for configuration.
**FRs covered:** FR1, FR2, FR3, FR4, FR9, FR10, FR53, FR54, FR56

### Epic 2: Salon Storefront Configuration
A professional can fully configure their salon storefront — services, categories, photos, display order — and publish it for clients to discover.
**FRs covered:** FR11, FR12, FR13, FR14, FR15, FR16, FR17, FR18, FR19

### Epic 3: Public Storefront & Client Discovery
A visitor can browse Pretty Face, discover salons, and view a salon's complete storefront (services, prices, photos) without needing an account.
**FRs covered:** FR25, FR26, FR27, FR28, FR55

### Epic 4: Availability Management
A professional can define their weekly opening hours and manage unavailabilities — blocking specific slots or cancelling an entire day — with automatic client notifications.
**FRs covered:** FR20, FR21, FR22, FR23, FR24

### Epic 5: Client Account & Online Booking
A client can create an account, log in, view available slots for a service, book an appointment 24/7, and receive confirmation and reminder emails.
**FRs covered:** FR5, FR6, FR7, FR8, FR29, FR30, FR31, FR32, FR33, FR34

### Epic 6: Appointment Management (Client & Pro)
Clients can manage their appointments (view, cancel, reschedule) and professionals can manage their full schedule (calendar view, appointment details, cancellation with client notification).
**FRs covered:** FR35, FR36, FR37, FR38, FR39, FR40, FR41, FR42, FR43, FR44

### Epic 7: Calendar Synchronization
A professional can synchronize their Pretty Face appointments with their personal calendar (Google Calendar, Apple Calendar, Outlook) via iCal export and subscription URL.
**FRs covered:** FR45, FR46, FR47

### Epic 8: Pro Dashboard & Statistics
A professional can monitor their business performance through visual statistics (monthly revenue, booking count, month-over-month progression) — the "wow moment" that proves they're building something real.
**FRs covered:** FR48, FR49, FR50, FR51, FR52

---

## Epic 1: Salon Registration & Multi-Tenant Setup

A professional can register on Pretty Face, authenticate (email/password or OAuth2), and obtain their own isolated salon space ready for configuration.

### Story 1.1: Professional Registration with Email/Password

As a beauty professional,
I want to create a Pretty Face account with my email and password,
So that I can access the platform and set up my salon.

**Acceptance Criteria:**

**Given** I am on the registration page
**When** I fill in my name, email, and a valid password (min 8 chars) and submit
**Then** my account is created, a new isolated Oracle schema is provisioned for my salon (< 10s), and I am redirected to the onboarding flow
**And** I receive a welcome email confirming my registration

**Given** I submit a registration form with an already-registered email
**When** the system processes the request
**Then** I see an error message indicating the email is already in use
**And** no duplicate account or schema is created

**Given** I submit a form with an invalid email format or a password shorter than 8 characters
**When** the system validates the form
**Then** I see specific inline validation error messages
**And** the form is not submitted

---

### Story 1.2: Professional Login with OAuth2 (Google)

As a beauty professional,
I want to log in using my Google account,
So that I can access my salon space without managing a separate password.

**Acceptance Criteria:**

**Given** I click "Login with Google" on the login page
**When** I complete the Google OAuth2 flow and grant permissions
**Then** I am authenticated and redirected to my pro dashboard
**And** if this is my first Google login, a new account and salon schema are created automatically

**Given** I have an existing account with the same email registered via email/password
**When** I log in with Google OAuth2 using the same email
**Then** the OAuth2 provider is linked to my existing account
**And** I am redirected to my pro dashboard without creating a duplicate account

**Given** the Google OAuth2 service is unavailable
**When** I attempt to log in with Google
**Then** I see a clear error message and a fallback option to log in with email/password

---

### Story 1.3: Password Reset via Email

As a beauty professional,
I want to reset my password via email,
So that I can regain access to my account if I forget my password.

**Acceptance Criteria:**

**Given** I am on the login page and click "Forgot password"
**When** I enter my registered email address and submit
**Then** I receive an email with a secure password reset link (valid for 1 hour)
**And** I see a confirmation message saying "Check your inbox"

**Given** I click the password reset link from my email
**When** the link is valid and not expired
**Then** I can enter and confirm a new password
**And** after saving, I am redirected to the login page with a success message

**Given** the reset link has expired or is invalid
**When** I click it
**Then** I see an error message with an option to request a new reset link

---

### Story 1.4: Pro Logout & Session Security

As a beauty professional,
I want to log out of my account and have my session properly secured,
So that my salon data remains protected.

**Acceptance Criteria:**

**Given** I am logged in to my pro space
**When** I click "Logout"
**Then** my session is invalidated, my JWT token is revoked, and I am redirected to the public home page

**Given** I attempt to access a pro route after logging out
**When** the system checks my authentication
**Then** I am redirected to the login page
**And** no salon data is accessible without re-authentication

**Given** I fail to log in 5 consecutive times with incorrect credentials
**When** the system detects the repeated failures
**Then** my account login is temporarily blocked for 15 minutes
**And** I see a clear message explaining the lockout duration

---

## Epic 2: Salon Storefront Configuration

A professional can fully configure their salon storefront — services, categories, photos, display order — and publish it for clients to discover.

### Story 2.1: Salon Profile Setup (Name, Logo, Description)

As a beauty professional,
I want to configure my salon's basic information (name, logo, and description),
So that clients can identify and learn about my establishment on my public storefront.

**Acceptance Criteria:**

**Given** I am logged in and in the pro settings page
**When** I enter my salon name, upload a logo image, and write a description, then save
**Then** the salon profile is updated and visible on my public storefront at `/salon/{slug}`
**And** the slug is auto-generated from the salon name (URL-friendly, unique)

**Given** I upload a logo image larger than 5MB or in an unsupported format
**When** the system validates the file
**Then** I see an error message specifying the accepted formats and size limit
**And** the previous logo (if any) remains unchanged

**Given** I leave the salon name empty and attempt to save
**When** the system validates the form
**Then** I see an inline error indicating the salon name is required
**And** the form is not submitted

---

### Story 2.2: Service Category Management

As a beauty professional,
I want to create and manage categories for my services,
So that my clients can browse my offerings in an organized way on my storefront.

**Acceptance Criteria:**

**Given** I am in the pro services management page
**When** I create a new category with a name (e.g., "Soin visage")
**Then** the category appears in my category list and is available for associating with services

**Given** I have existing categories
**When** I rename or delete a category
**Then** the change is reflected immediately in my category list
**And** if I delete a category that has services associated, I am warned and given the option to reassign or proceed

**Given** I attempt to create a category with a name that already exists in my salon
**When** the system validates the input
**Then** I see an error message indicating the category name must be unique

---

### Story 2.3: Service Creation & Management

As a beauty professional,
I want to create services with a name, description, price, and duration, and associate them with a category,
So that clients can see exactly what I offer and what it costs.

**Acceptance Criteria:**

**Given** I am on the service creation form
**When** I fill in name, description, price (€), duration (minutes), select a category, and save
**Then** the service is created and appears in my service list and on my public storefront

**Given** I open an existing service to edit
**When** I modify any field and save
**Then** the changes are immediately reflected on my storefront

**Given** I delete a service
**When** I confirm the deletion
**Then** the service is removed from my storefront
**And** existing confirmed bookings for that service are not deleted (historical data preserved)

**Given** I submit a service form with missing required fields (name, price, duration)
**When** the system validates
**Then** I see specific inline errors for each missing field
**And** the form is not submitted

---

### Story 2.4: Service Photo Upload

As a beauty professional,
I want to add photos to my services,
So that clients can see examples of my work and be more confident when booking.

**Acceptance Criteria:**

**Given** I am editing a service
**When** I upload one or more photos (JPEG, PNG, WebP, max 5MB each)
**Then** the photos are stored and displayed on the service card on my public storefront

**Given** I upload a photo in an unsupported format or exceeding 5MB
**When** the system validates the file
**Then** I see an error message specifying the accepted formats and size limit
**And** previously uploaded photos remain unchanged

**Given** I have multiple photos on a service and want to remove one
**When** I click the remove button on a photo
**Then** that photo is deleted and no longer displayed on the storefront

---

### Story 2.5: Service Activation, Deactivation & Display Order

As a beauty professional,
I want to activate/deactivate services and control their display order,
So that I can manage what clients see without permanently deleting services, and present them in the most effective order.

**Acceptance Criteria:**

**Given** I have an active service on my storefront
**When** I toggle it to "inactive"
**Then** the service disappears from the public storefront immediately
**And** it remains visible in my pro management list marked as inactive

**Given** I have an inactive service
**When** I toggle it back to "active"
**Then** it reappears on the public storefront immediately

**Given** I have multiple services in my list
**When** I drag and drop to reorder them
**Then** the new display order is saved and reflected on the public storefront

---

## Epic 3: Public Storefront & Client Discovery

A visitor can browse Pretty Face, discover salons, and view a salon's complete storefront (services, prices, photos) without needing an account.

### Story 3.1: Pretty Face Landing Page

As a visitor,
I want to see a welcoming home page with salon discovery options,
So that I can understand what Pretty Face offers and start exploring salons near me.

**Acceptance Criteria:**

**Given** I navigate to the Pretty Face home page (`/`)
**When** the page loads
**Then** I see a hero section with a search/location bar, illustrated care categories (e.g., Soin visage, Ongles, Épilation, Coiffure), a featured salons section, and a discreet pro CTA at the bottom
**And** the page loads in under 2 seconds at P95 on a 4G mobile connection

**Given** I click on a care category (e.g., "Ongles")
**When** the navigation occurs
**Then** I am taken to a filtered salon discovery page showing salons offering that category

**Given** I am a professional visiting the home page
**When** I click the discreet "Tu es pro ?" CTA
**Then** I am taken to the pro landing page (`/pour-les-pros`)

---

### Story 3.2: Salon Discovery by Category

As a visitor,
I want to browse salons filtered by care category,
So that I can find professionals who offer the specific service I'm looking for.

**Acceptance Criteria:**

**Given** I navigate to `/discover` or click a category from the home page
**When** the page loads
**Then** I see a list of active salon cards filtered by the selected category, each showing the salon name, logo, and a brief description

**Given** there are no salons available for a selected category
**When** the page loads
**Then** I see a friendly empty state message (e.g., "No salons in this category yet — check back soon!")

**Given** I am on the discover page
**When** the page is served from cache
**Then** the response time is under 2 seconds at P95 (Caffeine cache TTL: 5 min)

---

### Story 3.3: Public Salon Storefront Page

As a visitor,
I want to view a salon's public storefront with all their services, prices, and photos,
So that I can evaluate the salon and decide whether to book an appointment.

**Acceptance Criteria:**

**Given** I navigate to `/salon/{slug}` for an active salon
**When** the page loads
**Then** I see the salon name, logo, description, and a list of active services organized by category, each showing name, price, duration, and associated photos

**Given** the salon has photos on their services
**When** I view a service card
**Then** I can see the photos (carousel or grid) directly on the storefront

**Given** I navigate to `/salon/{slug}` for a salon in "draft" mode (not yet published)
**When** the page loads
**Then** I see a 404 or "coming soon" page — the storefront is not publicly visible

**Given** I navigate to `/salon/{slug}` that does not exist
**When** the system looks up the slug
**Then** I see a 404 not found page

---

## Epic 4: Availability Management

A professional can define their weekly opening hours and manage unavailabilities — blocking specific slots or cancelling an entire day — with automatic client notifications.

### Story 4.1: Weekly Opening Hours Configuration

As a beauty professional,
I want to define my opening hours for each day of the week,
So that clients only see time slots when I am actually available.

**Acceptance Criteria:**

**Given** I am on the availability management page in my pro space
**When** I configure opening hours for each day (e.g., Monday 9h–18h, Tuesday closed)
**Then** the system saves my schedule and uses it to compute available booking slots on my public storefront

**Given** I set a day as "closed"
**When** a client views my available slots
**Then** no slots are shown for that day

**Given** I update my opening hours (e.g., extend Friday to 20h)
**When** the change is saved
**Then** the new slots are immediately reflected on the public booking calendar
**And** existing confirmed bookings outside the new hours are not automatically cancelled

---

### Story 4.2: Block & Unblock Specific Time Slots

As a beauty professional,
I want to block or unblock specific time slots on my calendar,
So that I can manage one-off unavailabilities (medical appointment, personal errand) without changing my weekly schedule.

**Acceptance Criteria:**

**Given** I am on my pro calendar view
**When** I select a specific date and time range and click "Block this slot"
**Then** that slot is marked as unavailable and no longer shown to clients on the booking calendar

**Given** I have a previously blocked slot
**When** I select it and click "Unblock"
**Then** the slot becomes available again for clients to book

**Given** I block a time slot that already has a confirmed booking
**When** the system detects the conflict
**Then** I am warned that a booking exists in that slot and given the option to cancel it (with automatic client notification) before confirming the block

---

### Story 4.3: Day Cancellation (Illness & Emergency)

As a beauty professional,
I want to cancel all appointments for an entire day with a single action,
So that in case of illness or emergency, I can notify all affected clients quickly without managing each booking individually.

**Acceptance Criteria:**

**Given** I have confirmed bookings on a given day
**When** I select that day and click "Cancel my day" and confirm
**Then** all bookings for that day are cancelled
**And** each affected client receives an automatic email notification informing them of the cancellation and inviting them to rebook

**Given** I cancel a day with no existing bookings
**When** I confirm the action
**Then** the day is blocked in my calendar with no notifications sent (no clients to notify)

**Given** the email service is unavailable when I cancel my day
**When** the system attempts to send notifications
**Then** the bookings are still cancelled
**And** the failed notifications are queued for retry and I see a warning that notifications are pending

---

## Epic 5: Client Account & Online Booking

A client can create an account, log in, view available slots for a service, book an appointment 24/7, and receive confirmation and reminder emails.

### Story 5.1: Client Registration with Email/Password

As a client,
I want to create a Pretty Face account with my email and password,
So that I can book appointments and manage my reservations.

**Acceptance Criteria:**

**Given** I am on a salon storefront and click "Book" without being logged in
**When** I choose to register with email/password
**Then** I provide my first name, last name, email, and password, accept the CGU and privacy policy (explicit checkboxes — RGPD), and submit
**And** my account is created, I receive a welcome email, and I am returned to the booking flow where I left off

**Given** I submit a registration form with an already-registered email
**When** the system processes the request
**Then** I see an error suggesting I log in instead
**And** no duplicate account is created

**Given** I submit with an invalid email or password shorter than 8 characters
**When** the system validates the form
**Then** I see specific inline validation error messages
**And** the form is not submitted

---

### Story 5.2: Client Login with OAuth2 (Google)

As a client,
I want to log in using my Google account,
So that I can access my bookings quickly without managing a separate password.

**Acceptance Criteria:**

**Given** I click "Login with Google" on the login page or booking prompt
**When** I complete the Google OAuth2 flow
**Then** I am authenticated and returned to my previous context (e.g., booking flow)
**And** if this is my first Google login, a client account is created automatically with consent recorded

**Given** I have an existing client account with the same email registered via email/password
**When** I log in with Google OAuth2
**Then** the provider is linked to my existing account without creating a duplicate

**Given** the Google OAuth2 service is unavailable
**When** I attempt to log in with Google
**Then** I see a clear error message and a fallback option to log in with email/password

---

### Story 5.3: Available Slot Selection

As a client,
I want to see the available time slots for a specific service at a salon,
So that I can choose a convenient appointment time before committing to a booking.

**Acceptance Criteria:**

**Given** I am on a salon's storefront and click "Book" on a service
**When** the booking calendar loads
**Then** I see a date picker showing available days, and upon selecting a day, I see available time slots based on the pro's opening hours minus existing bookings and blocked slots

**Given** I select a date with no available slots
**When** the calendar renders
**Then** that date is visually disabled and I cannot select it

**Given** I view the booking calendar on a mobile device
**When** the page loads
**Then** the calendar and slot list are fully usable on a small screen (touch-friendly, no horizontal scroll)

---

### Story 5.4: Booking Confirmation

As a client,
I want to select a time slot and confirm my booking,
So that my appointment is secured and both the professional and I are immediately informed.

**Acceptance Criteria:**

**Given** I have selected a service, date, and available time slot
**When** I click "Confirm booking" (authenticated)
**Then** the booking is created with status PENDING → CONFIRMED, the slot is no longer available to other clients, and the API responds in under 500ms at P95

**Given** my booking is confirmed
**When** the system processes it
**Then** I receive a confirmation email with the service name, date, time, salon name, and a link to manage my booking
**And** the professional receives a notification email about the new booking

**Given** two clients attempt to book the same slot simultaneously
**When** the system processes both requests
**Then** only one booking succeeds; the other client sees a message that the slot is no longer available and is invited to choose another

**Given** I am not logged in when I click "Confirm booking"
**When** the system checks my authentication
**Then** I am prompted to log in or register, and after authenticating, I am returned to the confirmation step with my selected slot preserved

---

### Story 5.5: Booking Reminder Email

As a client,
I want to receive a reminder email the day before my appointment,
So that I don't forget my booking and can prepare accordingly.

**Acceptance Criteria:**

**Given** I have a confirmed upcoming booking
**When** it is the day before my appointment (at a scheduled time, e.g., 9am)
**Then** I receive a reminder email with the service name, date, time, salon name, and a link to manage or cancel my booking

**Given** I cancel my booking before the reminder is sent
**When** the scheduled reminder job runs
**Then** no reminder is sent for the cancelled booking

**Given** the email service is temporarily unavailable when the reminder job runs
**When** the system attempts to send the reminder
**Then** the failure is logged and the reminder is retried (up to 3 times with exponential backoff)

---

## Epic 6: Appointment Management (Client & Pro)

Clients can manage their appointments (view, cancel, reschedule) and professionals can manage their full schedule (calendar view, appointment details, cancellation with client notification).

### Story 6.1: Client Appointment History

As a client,
I want to see my upcoming and past appointments in one place,
So that I can keep track of my bookings and know what's coming up.

**Acceptance Criteria:**

**Given** I am logged in as a client and navigate to "My appointments"
**When** the page loads
**Then** I see two tabs: "Upcoming" and "Past", each listing my appointments with service name, salon name, date, time, and current status (PENDING, CONFIRMED, CANCELLED)

**Given** I have no upcoming appointments
**When** the "Upcoming" tab loads
**Then** I see a friendly empty state with a CTA to discover salons

**Given** I have upcoming and past appointments
**When** I switch between tabs
**Then** upcoming appointments are sorted by date ascending; past appointments by date descending

---

### Story 6.2: Client Appointment Cancellation

As a client,
I want to cancel an upcoming appointment,
So that the time slot is freed for other clients and the professional is informed.

**Acceptance Criteria:**

**Given** I am viewing my upcoming appointments
**When** I click "Cancel" on a confirmed booking and confirm the cancellation
**Then** the booking status changes to CANCELLED, the slot becomes available again on the public calendar
**And** the professional receives an email notification about the cancellation

**Given** I cancel a booking
**When** the cancellation is processed
**Then** I see a confirmation message and the booking moves to my "Past" tab with status CANCELLED

**Given** the appointment is in the past
**When** I view it in my appointment list
**Then** no "Cancel" option is shown — only past bookings with their final status

---

### Story 6.3: Client Appointment Rescheduling

As a client,
I want to reschedule an upcoming appointment to a different available slot,
So that I can adapt my booking to changes in my schedule without cancelling entirely.

**Acceptance Criteria:**

**Given** I am viewing my upcoming appointments
**When** I click "Reschedule" on a confirmed booking
**Then** I see the booking calendar for the same service showing all currently available slots (excluding the original slot)

**Given** I select a new date and time and confirm
**When** the system processes the reschedule
**Then** the original slot is released, the new slot is booked, the booking status remains CONFIRMED
**And** the professional receives an email notification about the reschedule (original time + new time)
**And** I receive a new confirmation email with the updated appointment details

**Given** no other slots are available for that service
**When** the reschedule calendar loads
**Then** I see a message indicating no availability and am offered to cancel instead

---

### Story 6.4: Pro Daily Appointment View

As a beauty professional,
I want to see all my appointments for today in a clear list,
So that I can start each day knowing exactly what's ahead without having to navigate a complex calendar.

**Acceptance Criteria:**

**Given** I am logged in and navigate to my pro dashboard or bookings page
**When** the page loads
**Then** I see today's appointments listed chronologically with client name, service name, start time, duration, and status (CONFIRMED, CANCELLED)

**Given** I have no appointments today
**When** the page loads
**Then** I see a message "No appointments today" and a link to view my full calendar

**Given** a client cancels a booking for today
**When** I refresh or the view updates
**Then** the cancelled booking is shown with CANCELLED status (not removed — still visible for awareness)

---

### Story 6.5: Pro Calendar View (Week & Month)

As a beauty professional,
I want to view my full schedule in a calendar (week and month view),
So that I can plan ahead, spot gaps, and understand my workload at a glance.

**Acceptance Criteria:**

**Given** I navigate to the calendar view in my pro space
**When** the page loads
**Then** I see a week view by default showing all confirmed bookings as blocks with client name and service name, and blocked slots visually distinct from bookings

**Given** I switch to month view
**When** the calendar re-renders
**Then** I see a monthly grid with appointment counts per day, and I can click a day to see its bookings in detail

**Given** I navigate to a different week or month using prev/next controls
**When** the navigation occurs
**Then** the calendar loads the correct period with all bookings and blocked slots for that range

---

### Story 6.6: Pro Appointment Cancellation with Client Notification

As a beauty professional,
I want to cancel a specific client appointment,
So that the slot is freed and the client is automatically notified without me having to contact them manually.

**Acceptance Criteria:**

**Given** I am viewing an appointment in my calendar or daily list
**When** I click "Cancel appointment" and confirm
**Then** the booking status changes to CANCELLED, the slot is freed for new bookings
**And** the client receives an automatic email notification informing them of the cancellation and inviting them to rebook

**Given** I cancel an appointment
**When** the cancellation is processed
**Then** I see a confirmation that the client has been notified
**And** the cancelled booking remains visible in my calendar with CANCELLED status for reference

**Given** the email service is temporarily unavailable when I cancel
**When** the system attempts to notify the client
**Then** the booking is still cancelled
**And** the notification is queued for retry and I see a warning that the client notification is pending

---

## Epic 7: Calendar Synchronization

A professional can synchronize their Pretty Face appointments with their personal calendar (Google Calendar, Apple Calendar, Outlook) via iCal export and subscription URL.

### Story 7.1: iCal Export & Calendar Subscription URL

As a beauty professional,
I want to export my appointments as an iCal file and get a subscription URL,
So that I can add all my Pretty Face bookings to my personal calendar and have them stay up to date automatically.

**Acceptance Criteria:**

**Given** I am in my pro space and navigate to the calendar sync settings
**When** the page loads
**Then** I see two options: "Download .ics file" (one-time export) and "Copy subscription URL" (auto-updating feed)

**Given** I click "Download .ics file"
**When** the system generates the file
**Then** a valid `.ics` file is downloaded containing all my confirmed upcoming bookings (client name, service name, start time, duration, salon address if set)
**And** the file is compatible with Google Calendar, Apple Calendar, and Outlook

**Given** I copy the subscription URL and add it to Google Calendar
**When** Google Calendar fetches the URL (`/api/salon/{slug}/calendar.ics`)
**Then** the feed returns a valid iCal response with all confirmed upcoming bookings
**And** the endpoint is publicly accessible (no auth required — URL itself acts as the token via the unique slug)

---

### Story 7.2: Automatic Calendar Sync on Booking Changes

As a beauty professional,
I want my calendar subscription to reflect booking changes automatically,
So that my personal calendar always shows my current schedule without manual updates.

**Acceptance Criteria:**

**Given** I have subscribed to my Pretty Face iCal URL in Google Calendar
**When** a new booking is confirmed (by a client or myself)
**Then** the iCal feed is updated and the new booking appears in my personal calendar on the next sync cycle

**Given** a booking is cancelled (by me or a client)
**When** the cancellation is processed
**Then** the iCal cache is invalidated, the feed is regenerated, and the cancelled booking is removed from the feed on the next calendar sync

**Given** I block a time slot
**When** the iCal feed is requested
**Then** blocked slots are included in the feed as "Unavailable" events so they appear in my personal calendar

**Given** the iCal endpoint is called frequently (e.g., by multiple calendar clients)
**When** no booking changes have occurred
**Then** the cached iCal response is served (cache TTL: 15 min) to avoid regenerating on every request

---

## Epic 8: Pro Dashboard & Statistics

A professional can monitor their business performance through visual statistics (monthly revenue, booking count, month-over-month progression) — the "wow moment" that proves they're building something real.

### Story 8.1: Monthly Revenue & Booking Count

As a beauty professional,
I want to see my total revenue and number of bookings for the current month at a glance,
So that I know immediately how my business is performing this month when I open the app.

**Acceptance Criteria:**

**Given** I am logged in and navigate to my pro dashboard
**When** the page loads
**Then** I see two prominent KPI cards: "Revenue this month" (sum of confirmed bookings × service price) and "Bookings this month" (count of confirmed bookings in the current calendar month)
**And** the dashboard loads in under 3 seconds at P95 with up to 500 historical bookings

**Given** I have no confirmed bookings this month
**When** the dashboard loads
**Then** both KPI cards show 0 with an encouraging message (e.g., "Your first booking is just around the corner!")

**Given** a booking is cancelled after being counted
**When** the dashboard refreshes
**Then** the cancelled booking is excluded from revenue and booking count (only CONFIRMED status counts)

---

### Story 8.2: Month-over-Month Evolution Charts

As a beauty professional,
I want to see how my revenue and booking count have evolved month by month,
So that I can understand my growth trend and feel motivated by my progress.

**Acceptance Criteria:**

**Given** I am on my pro dashboard
**When** the page loads
**Then** I see two charts: a monthly revenue chart and a monthly bookings chart, each showing the last 6 months of data as a bar or line chart

**Given** I have data for multiple months
**When** the charts render
**Then** each month is labeled (e.g., "Jan", "Feb") and the values are clearly readable on both mobile and desktop

**Given** I have less than 2 months of data
**When** the charts render
**Then** I see the available months with a message encouraging me to keep going (e.g., "Your trend is building — keep it up!")

**Given** the dashboard loads with up to 500 historical bookings
**When** the statistics are computed
**Then** the aggregation query completes within the 3-second P95 threshold (server-side aggregation, not client-side)

---

### Story 8.3: Progression Rate vs Previous Month

As a beauty professional,
I want to see my progression rate in percentage compared to the previous month for both revenue and bookings,
So that I have a clear, emotionally resonant signal of whether my business is growing.

**Acceptance Criteria:**

**Given** I have confirmed bookings in both the current and previous month
**When** the dashboard loads
**Then** I see a progression indicator on each KPI card showing the percentage change vs last month (e.g., "+15% vs last month" in green, or "-8% vs last month" in amber)
**And** the indicator is visually prominent — not just a number, but a clear growth/decline signal (icon + color + percentage)

**Given** my revenue or bookings this month exceed last month's
**When** the progression rate is displayed
**Then** the indicator is shown in a positive color (green/rose) with an upward arrow or celebratory icon

**Given** my revenue or bookings this month are lower than last month's
**When** the progression rate is displayed
**Then** the indicator is shown in a neutral/amber color with a downward arrow — never alarming, always constructive

**Given** there is no data for the previous month (first month of activity)
**When** the dashboard loads
**Then** no progression rate is shown; instead I see "First month — your baseline starts here!" as an encouraging message
