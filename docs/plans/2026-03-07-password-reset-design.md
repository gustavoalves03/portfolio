# Story 1.3: Password Reset via Email — Design

## Overview

Allow professionals to reset their password via a secure email link. Token stored on User entity with 1-hour expiry. Simple cooldown prevents spam (skip email if valid token already exists).

## Data Model

Add to `User` entity:
- `passwordResetToken` (String, unique, nullable) — `UUID.randomUUID()` value
- `passwordResetTokenExpiresAt` (Instant, nullable) — generation time + 1 hour

Add to `UserRepository`:
- `Optional<User> findByPasswordResetToken(String token)`

## Backend API

### `POST /api/auth/forgot-password`

Request: `{ "email": "string" }`

Flow:
1. Look up user by email. Not found → return 200 anyway (no user enumeration)
2. If a non-expired token already exists → skip email, return 200 (cooldown)
3. Generate `UUID.randomUUID()`, set expiry = now + 1 hour, save to user
4. Send email async via `EmailService` with link: `{frontendBaseUrl}/reset-password?token={token}`
5. Always return `200 { "message": "If an account exists, you will receive an email" }`

Public endpoint (no auth required).

### `POST /api/auth/reset-password`

Request: `{ "token": "string", "newPassword": "string" }`

Flow:
1. Look up user by token. Not found → 400 "Invalid or expired token"
2. Check `tokenExpiresAt > now`. Expired → 400 "Invalid or expired token"
3. Encode new password with `PasswordEncoder`, save
4. Clear `passwordResetToken` and `passwordResetTokenExpiresAt` (one-time use)
5. Return `200 { "message": "Password updated successfully" }`

Public endpoint (no auth required).

## Email Template

`password-reset.html` — Thymeleaf template matching `welcome-pro.html` style:
- Greeting with user name
- Explanation text
- "Reset my password" button with reset link
- Expiry warning (1 hour)
- "If you didn't request this" disclaimer

## Frontend

### ForgotPasswordComponent (`/forgot-password`)
- Email input form with validation
- On submit: `authService.requestPasswordReset(email)`
- On success: show "Check your inbox" confirmation, hide form
- Link back to login

### ResetPasswordComponent (`/reset-password?token=...`)
- Read `token` from query params
- Form: new password + confirm password (min 8 chars, must match)
- On submit: `authService.resetPassword(token, newPassword)`
- On success: success message + link to login
- On error: error message + link to request new reset

### Login page update
- Add "Forgot password?" link (translation key `auth.login.forgotPassword` already exists) → `/forgot-password`

### AuthService methods
- `requestPasswordReset(email: string): Observable<any>`
- `resetPassword(token: string, newPassword: string): Observable<any>`

## Translations

Add to both `en.json` and `fr.json`:
- `auth.forgotPassword.title`, `subtitle`, `email`, `submit`, `success`, `backToLogin`
- `auth.resetPassword.title`, `newPassword`, `confirmPassword`, `passwordMismatch`, `submit`, `success`, `invalidToken`, `requestNewLink`

## Tests

### Backend (JUnit 5 + Mockito)
- Forgot password: email found, email not found (same 200), cooldown (token exists)
- Reset password: valid token, expired token, invalid token, password updated + token cleared

### Frontend (Jasmine)
- ForgotPasswordComponent: form validation, success state
- ResetPasswordComponent: form validation, password mismatch, success, expired token error

## Decisions
- **Token storage:** User entity columns (simple, sufficient for scale)
- **Cooldown:** Skip email if non-expired token exists (no extra infra needed)
- **One-time use:** Token cleared after successful reset
- **No user enumeration:** Always return 200 on forgot-password regardless of email existence
