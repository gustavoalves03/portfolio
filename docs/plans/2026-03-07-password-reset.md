# Password Reset via Email — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow professionals to reset their password via a secure, time-limited email link.

**Architecture:** Two new public endpoints (`forgot-password`, `reset-password`) on the existing `AuthController`. Token stored as columns on the `User` entity (UUID + expiry). Cooldown prevents spam by skipping email if a valid token already exists. Two new Angular pages with the same styling as login/register.

**Tech Stack:** Spring Boot 3.5 (JPA, Spring Mail, Thymeleaf), Angular 20 (standalone, signals, Transloco, Angular Material)

---

### Task 1: Add password reset fields to User entity

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/users/domain/User.java`

**Step 1: Add two nullable columns after the `providerId` field**

```java
@Column(name = "password_reset_token", unique = true)
private String passwordResetToken;

@Column(name = "password_reset_token_expires_at")
private java.time.Instant passwordResetTokenExpiresAt;
```

Add `import java.time.Instant;` at the top.

**Step 2: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

### Task 2: Add `findByPasswordResetToken` to UserRepository

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/users/repo/UserRepository.java`

**Step 1: Add the query method**

```java
Optional<User> findByPasswordResetToken(String passwordResetToken);
```

**Step 2: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

### Task 3: Create DTOs for password reset requests

**Files:**
- Create: `backend/src/main/java/com/prettyface/app/auth/dto/ForgotPasswordRequest.java`
- Create: `backend/src/main/java/com/prettyface/app/auth/dto/ResetPasswordRequest.java`

**Step 1: Create ForgotPasswordRequest**

```java
package com.prettyface.app.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
    @NotBlank @Email String email
) {}
```

**Step 2: Create ResetPasswordRequest**

```java
package com.prettyface.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8) String newPassword
) {}
```

**Step 3: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

### Task 4: Create password-reset email template

**Files:**
- Create: `backend/src/main/resources/templates/password-reset.html`

**Step 1: Create the Thymeleaf template**

Use the same styling as `welcome-pro.html`. Template variables: `userName`, `resetUrl`.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="fr">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Réinitialisation du mot de passe</title>
    <style>
        body { font-family: 'Roboto', Arial, sans-serif; background-color: #fdf6f0; margin: 0; padding: 0; }
        .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
        .header { background-color: #e91e63; padding: 32px 40px; text-align: center; }
        .header h1 { color: #ffffff; font-size: 28px; margin: 0; font-weight: 300; letter-spacing: 2px; }
        .body { padding: 40px; color: #333333; }
        .body h2 { color: #e91e63; font-size: 22px; font-weight: 400; margin-top: 0; }
        .body p { line-height: 1.7; font-size: 15px; color: #555555; }
        .cta { text-align: center; margin: 32px 0; }
        .cta a { background-color: #e91e63; color: #ffffff; padding: 14px 32px; border-radius: 50px; text-decoration: none; font-size: 15px; font-weight: 500; }
        .footer { background-color: #f9f9f9; padding: 24px 40px; text-align: center; font-size: 12px; color: #999999; }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>Pretty Face</h1>
    </div>
    <div class="body">
        <h2>Bonjour <span th:text="${userName}">Professionnel</span>,</h2>
        <p>Vous avez demandé la réinitialisation de votre mot de passe. Cliquez sur le bouton ci-dessous pour en choisir un nouveau.</p>
        <div class="cta">
            <a th:href="${resetUrl}">Réinitialiser mon mot de passe</a>
        </div>
        <p style="font-size:13px;color:#888;">Ce lien expire dans 1 heure. Si vous n'avez pas fait cette demande, ignorez simplement cet e-mail.</p>
    </div>
    <div class="footer">
        <p>&copy; 2025 Pretty Face &mdash; Tous droits réservés.</p>
    </div>
</div>
</body>
</html>
```

---

### Task 5: Add `sendPasswordResetEmail` to EmailService

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/notification/app/EmailService.java`

**Step 1: Add the method after `sendWelcomeEmail`**

```java
@Async
public void sendPasswordResetEmail(User user, String resetToken) {
    try {
        Context ctx = new Context();
        ctx.setVariable("userName", user.getName());
        ctx.setVariable("resetUrl", frontendBaseUrl + "/reset-password?token=" + resetToken);

        String htmlContent = templateEngine.process("password-reset", ctx);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(user.getEmail());
        helper.setSubject("Reset your password / Réinitialiser votre mot de passe");
        helper.setText(htmlContent, true);

        mailSender.send(message);
        logger.info("Password reset email sent to {}", user.getEmail());

    } catch (MessagingException | java.io.UnsupportedEncodingException e) {
        logger.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
    }
}
```

**Step 2: Verify compilation**

Run: `cd backend && ./mvnw compile -q`
Expected: BUILD SUCCESS

---

### Task 6: Write backend tests for forgot-password and reset-password

**Files:**
- Modify: `backend/src/test/java/com/prettyface/app/auth/AuthControllerTests.java`

**Step 1: Add forgot-password tests**

Add these test methods to the existing `AuthControllerTests` class:

```java
// --- Forgot Password ---

// T3.1: Email exists → 200 (same response as not found, no enumeration)
@Test
void forgotPassword_existingEmail_returns200() throws Exception {
    User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
            .provider(AuthProvider.LOCAL).role(Role.PRO).build();
    when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenReturn(user);

    mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"sophie@salon.fr\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

    verify(emailService).sendPasswordResetEmail(any(User.class), anyString());
}

// T3.2: Email not found → still 200 (no user enumeration)
@Test
void forgotPassword_unknownEmail_returns200NoEmail() throws Exception {
    when(userRepository.findByEmail("unknown@salon.fr")).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"unknown@salon.fr\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

    verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
}

// T3.3: Valid token already exists → 200, no new email (cooldown)
@Test
void forgotPassword_existingValidToken_skipsEmail() throws Exception {
    User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
            .provider(AuthProvider.LOCAL).role(Role.PRO)
            .passwordResetToken("existing-token")
            .passwordResetTokenExpiresAt(Instant.now().plusSeconds(1800))
            .build();
    when(userRepository.findByEmail("sophie@salon.fr")).thenReturn(Optional.of(user));

    mockMvc.perform(post("/api/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"sophie@salon.fr\"}"))
            .andExpect(status().isOk());

    verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
    verify(userRepository, never()).save(any());
}
```

**Step 2: Add reset-password tests**

```java
// --- Reset Password ---

// T3.4: Valid token → 200, password updated, token cleared
@Test
void resetPassword_validToken_updatesPassword() throws Exception {
    User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
            .provider(AuthProvider.LOCAL).role(Role.PRO)
            .passwordResetToken("valid-token")
            .passwordResetTokenExpiresAt(Instant.now().plusSeconds(1800))
            .build();
    when(userRepository.findByPasswordResetToken("valid-token")).thenReturn(Optional.of(user));
    when(passwordEncoder.encode("newpassword123")).thenReturn("$2a$encoded");
    when(userRepository.save(any(User.class))).thenReturn(user);

    mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"valid-token\",\"newPassword\":\"newpassword123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getPassword()).isEqualTo("$2a$encoded");
    assertThat(captor.getValue().getPasswordResetToken()).isNull();
    assertThat(captor.getValue().getPasswordResetTokenExpiresAt()).isNull();
}

// T3.5: Expired token → 400
@Test
void resetPassword_expiredToken_returns400() throws Exception {
    User user = User.builder().id(1L).name("Sophie").email("sophie@salon.fr")
            .provider(AuthProvider.LOCAL).role(Role.PRO)
            .passwordResetToken("expired-token")
            .passwordResetTokenExpiresAt(Instant.now().minusSeconds(60))
            .build();
    when(userRepository.findByPasswordResetToken("expired-token")).thenReturn(Optional.of(user));

    mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"expired-token\",\"newPassword\":\"newpassword123\"}"))
            .andExpect(status().isBadRequest());
}

// T3.6: Invalid token → 400
@Test
void resetPassword_invalidToken_returns400() throws Exception {
    when(userRepository.findByPasswordResetToken("bogus")).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"bogus\",\"newPassword\":\"newpassword123\"}"))
            .andExpect(status().isBadRequest());
}
```

**Step 3: Add missing imports to the test file**

```java
import java.time.Instant;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
```

**Step 4: Run tests — they should FAIL** (endpoints don't exist yet)

Run: `cd backend && ./mvnw test -pl . -Dtest=AuthControllerTests -q`
Expected: 6 new tests FAIL (404 Not Found)

---

### Task 7: Implement forgot-password and reset-password endpoints

**Files:**
- Modify: `backend/src/main/java/com/prettyface/app/auth/AuthController.java`

**Step 1: Add imports**

```java
import com.prettyface.app.auth.dto.ForgotPasswordRequest;
import com.prettyface.app.auth.dto.ResetPasswordRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
```

**Step 2: Add forgot-password endpoint after the `/me` endpoint**

```java
@PostMapping("/forgot-password")
public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    String message = "If an account exists with this email, you will receive a reset link.";

    userRepository.findByEmail(request.email()).ifPresent(user -> {
        // Cooldown: skip if a valid (non-expired) token already exists
        if (user.getPasswordResetToken() != null
                && user.getPasswordResetTokenExpiresAt() != null
                && user.getPasswordResetTokenExpiresAt().isAfter(Instant.now())) {
            return;
        }

        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(3600));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user, token);
    });

    return ResponseEntity.ok(Map.of("message", message));
}
```

**Step 3: Add reset-password endpoint**

```java
@PostMapping("/reset-password")
@Transactional
public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    User user = userRepository.findByPasswordResetToken(request.token())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

    if (user.getPasswordResetTokenExpiresAt() == null
            || user.getPasswordResetTokenExpiresAt().isBefore(Instant.now())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
    }

    user.setPassword(passwordEncoder.encode(request.newPassword()));
    user.setPasswordResetToken(null);
    user.setPasswordResetTokenExpiresAt(null);
    userRepository.save(user);

    return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
}
```

**Step 4: Run tests — all should PASS**

Run: `cd backend && ./mvnw test -pl . -Dtest=AuthControllerTests -q`
Expected: All 12 tests PASS (6 existing + 6 new)

**Step 5: Run full backend test suite**

Run: `cd backend && ./mvnw test -q`
Expected: All tests PASS, BUILD SUCCESS

**Step 6: Commit**

```bash
git add backend/
git commit -m "feat(auth): add password reset endpoints with cooldown and email"
```

---

### Task 8: Add translations for password reset pages

**Files:**
- Modify: `frontend/public/i18n/en.json`
- Modify: `frontend/public/i18n/fr.json`

**Step 1: Add keys to en.json under `auth`**

Add after the `"register"` block:

```json
"forgotPassword": {
  "title": "Reset your password",
  "subtitle": "Enter your email address and we'll send you a reset link.",
  "email": "Email address",
  "submit": "Send reset link",
  "success": "If an account exists with this email, you will receive a reset link. Please check your inbox.",
  "backToLogin": "Back to sign in"
},
"resetPassword": {
  "title": "Choose a new password",
  "subtitle": "Enter your new password below.",
  "newPassword": "New password",
  "confirmPassword": "Confirm new password",
  "passwordHint": "At least 8 characters",
  "submit": "Update password",
  "success": "Your password has been updated successfully.",
  "goToLogin": "Sign in with your new password",
  "invalidToken": "This reset link is invalid or has expired.",
  "requestNewLink": "Request a new reset link"
}
```

Add to `auth.errors`:

```json
"passwordMismatch": "Passwords do not match"
```

**Step 2: Add keys to fr.json under `auth`**

```json
"forgotPassword": {
  "title": "Réinitialiser votre mot de passe",
  "subtitle": "Entrez votre adresse e-mail et nous vous enverrons un lien de réinitialisation.",
  "email": "Adresse e-mail",
  "submit": "Envoyer le lien",
  "success": "Si un compte existe avec cet e-mail, vous recevrez un lien de réinitialisation. Vérifiez votre boîte de réception.",
  "backToLogin": "Retour à la connexion"
},
"resetPassword": {
  "title": "Choisir un nouveau mot de passe",
  "subtitle": "Entrez votre nouveau mot de passe ci-dessous.",
  "newPassword": "Nouveau mot de passe",
  "confirmPassword": "Confirmer le nouveau mot de passe",
  "passwordHint": "Au moins 8 caractères",
  "submit": "Mettre à jour le mot de passe",
  "success": "Votre mot de passe a été mis à jour avec succès.",
  "goToLogin": "Se connecter avec le nouveau mot de passe",
  "invalidToken": "Ce lien de réinitialisation est invalide ou a expiré.",
  "requestNewLink": "Demander un nouveau lien"
}
```

Add to `auth.errors`:

```json
"passwordMismatch": "Les mots de passe ne correspondent pas"
```

---

### Task 9: Add AuthService methods for password reset

**Files:**
- Modify: `frontend/src/app/core/auth/auth.service.ts`

**Step 1: Add two methods after `handleOAuth2Callback`**

```typescript
/**
 * Request a password reset email
 */
requestPasswordReset(email: string): Observable<{ message: string }> {
  return this.http.post<{ message: string }>(`${this.apiBaseUrl}/api/auth/forgot-password`, { email });
}

/**
 * Reset password with token
 */
resetPassword(token: string, newPassword: string): Observable<{ message: string }> {
  return this.http.post<{ message: string }>(`${this.apiBaseUrl}/api/auth/reset-password`, { token, newPassword });
}
```

**Step 2: Verify compilation**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

---

### Task 10: Create ForgotPasswordComponent

**Files:**
- Create: `frontend/src/app/pages/auth/forgot-password/forgot-password.component.ts`
- Create: `frontend/src/app/pages/auth/forgot-password/forgot-password.component.html`
- Create: `frontend/src/app/pages/auth/forgot-password/forgot-password.component.scss`

**Step 1: Create the component TypeScript**

```typescript
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslocoModule,
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss',
})
export class ForgotPasswordComponent {
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  isLoading = false;
  emailSent = false;

  get emailControl() { return this.form.get('email'); }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;

    this.authService.requestPasswordReset(this.form.value.email!).subscribe({
      next: () => {
        this.isLoading = false;
        this.emailSent = true;
      },
      error: () => {
        this.isLoading = false;
        // Still show success to prevent user enumeration
        this.emailSent = true;
      },
    });
  }
}
```

**Step 2: Create the template**

```html
<div class="forgot-password-page">
  <div class="forgot-password-card">
    <div class="forgot-password-header">
      <h1 class="forgot-password-title">{{ 'auth.forgotPassword.title' | transloco }}</h1>
      <p class="forgot-password-subtitle">{{ 'auth.forgotPassword.subtitle' | transloco }}</p>
    </div>

    @if (!emailSent) {
      <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate class="forgot-password-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'auth.forgotPassword.email' | transloco }}</mat-label>
          <input matInput formControlName="email" type="email" autocomplete="email" />
          @if (emailControl?.hasError('required') && emailControl?.touched) {
            <mat-error>{{ 'auth.errors.emailRequired' | transloco }}</mat-error>
          }
          @if (emailControl?.hasError('email') && emailControl?.touched) {
            <mat-error>{{ 'auth.errors.emailInvalid' | transloco }}</mat-error>
          }
        </mat-form-field>

        <button
          mat-flat-button
          color="primary"
          type="submit"
          class="submit-btn full-width"
          [disabled]="isLoading">
          @if (isLoading) {
            <mat-spinner diameter="20" />
          } @else {
            {{ 'auth.forgotPassword.submit' | transloco }}
          }
        </button>
      </form>
    } @else {
      <div class="success-message">
        <p>{{ 'auth.forgotPassword.success' | transloco }}</p>
      </div>
    }

    <p class="back-link">
      <a routerLink="/login">{{ 'auth.forgotPassword.backToLogin' | transloco }}</a>
    </p>
  </div>
</div>
```

**Step 3: Create the SCSS** (reuse login styling)

```scss
.forgot-password-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #fdf6f0;
  padding: 16px;
}

.forgot-password-card {
  width: 100%;
  max-width: 440px;
  background: #ffffff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.forgot-password-header {
  text-align: center;
  margin-bottom: 28px;
}

.forgot-password-title {
  font-size: 24px;
  font-weight: 400;
  color: #333;
  margin: 0 0 8px;
}

.forgot-password-subtitle {
  font-size: 14px;
  color: #777;
  margin: 0;
}

.forgot-password-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.full-width {
  width: 100%;
}

.submit-btn {
  margin-top: 8px;
  height: 44px;
}

.success-message {
  background-color: #e8f5e9;
  color: #2e7d32;
  border-radius: 6px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.6;
  text-align: center;
}

.back-link {
  text-align: center;
  font-size: 13px;
  color: #666;
  margin-top: 16px;

  a {
    color: #e91e63;
    text-decoration: none;
    font-weight: 500;

    &:hover {
      text-decoration: underline;
    }
  }
}
```

---

### Task 11: Create ResetPasswordComponent

**Files:**
- Create: `frontend/src/app/pages/auth/reset-password/reset-password.component.ts`
- Create: `frontend/src/app/pages/auth/reset-password/reset-password.component.html`
- Create: `frontend/src/app/pages/auth/reset-password/reset-password.component.scss`

**Step 1: Create the component TypeScript**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslocoModule,
  ],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]],
  });

  token: string | null = null;
  isLoading = false;
  resetSuccess = false;
  invalidToken = false;

  get newPasswordControl() { return this.form.get('newPassword'); }
  get confirmPasswordControl() { return this.form.get('confirmPassword'); }
  get passwordsMismatch(): boolean {
    return this.form.value.newPassword !== this.form.value.confirmPassword
      && !!this.confirmPasswordControl?.touched;
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.invalidToken = true;
    }
  }

  onSubmit(): void {
    if (this.form.invalid || this.passwordsMismatch || !this.token) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.invalidToken = false;

    this.authService.resetPassword(this.token, this.form.value.newPassword!).subscribe({
      next: () => {
        this.isLoading = false;
        this.resetSuccess = true;
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        this.invalidToken = true;
      },
    });
  }
}
```

**Step 2: Create the template**

```html
<div class="reset-password-page">
  <div class="reset-password-card">
    <div class="reset-password-header">
      <h1 class="reset-password-title">{{ 'auth.resetPassword.title' | transloco }}</h1>
      <p class="reset-password-subtitle">{{ 'auth.resetPassword.subtitle' | transloco }}</p>
    </div>

    @if (resetSuccess) {
      <div class="success-message">
        <p>{{ 'auth.resetPassword.success' | transloco }}</p>
      </div>
      <p class="action-link">
        <a routerLink="/login">{{ 'auth.resetPassword.goToLogin' | transloco }}</a>
      </p>
    } @else if (invalidToken && !isLoading) {
      <div class="error-banner">
        <p>{{ 'auth.resetPassword.invalidToken' | transloco }}</p>
      </div>
      <p class="action-link">
        <a routerLink="/forgot-password">{{ 'auth.resetPassword.requestNewLink' | transloco }}</a>
      </p>
    } @else {
      <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate class="reset-password-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'auth.resetPassword.newPassword' | transloco }}</mat-label>
          <input matInput formControlName="newPassword" type="password" autocomplete="new-password" />
          <mat-hint>{{ 'auth.resetPassword.passwordHint' | transloco }}</mat-hint>
          @if (newPasswordControl?.hasError('required') && newPasswordControl?.touched) {
            <mat-error>{{ 'auth.errors.passwordRequired' | transloco }}</mat-error>
          }
          @if (newPasswordControl?.hasError('minlength') && newPasswordControl?.touched) {
            <mat-error>{{ 'auth.errors.passwordTooShort' | transloco }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'auth.resetPassword.confirmPassword' | transloco }}</mat-label>
          <input matInput formControlName="confirmPassword" type="password" autocomplete="new-password" />
          @if (confirmPasswordControl?.hasError('required') && confirmPasswordControl?.touched) {
            <mat-error>{{ 'auth.errors.passwordRequired' | transloco }}</mat-error>
          }
        </mat-form-field>

        @if (passwordsMismatch) {
          <div class="error-banner">{{ 'auth.errors.passwordMismatch' | transloco }}</div>
        }

        <button
          mat-flat-button
          color="primary"
          type="submit"
          class="submit-btn full-width"
          [disabled]="isLoading">
          @if (isLoading) {
            <mat-spinner diameter="20" />
          } @else {
            {{ 'auth.resetPassword.submit' | transloco }}
          }
        </button>
      </form>
    }
  </div>
</div>
```

**Step 3: Create the SCSS**

```scss
.reset-password-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #fdf6f0;
  padding: 16px;
}

.reset-password-card {
  width: 100%;
  max-width: 440px;
  background: #ffffff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.reset-password-header {
  text-align: center;
  margin-bottom: 28px;
}

.reset-password-title {
  font-size: 24px;
  font-weight: 400;
  color: #333;
  margin: 0 0 8px;
}

.reset-password-subtitle {
  font-size: 14px;
  color: #777;
  margin: 0;
}

.reset-password-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.full-width {
  width: 100%;
}

.submit-btn {
  margin-top: 8px;
  height: 44px;
}

.success-message {
  background-color: #e8f5e9;
  color: #2e7d32;
  border-radius: 6px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.6;
  text-align: center;
}

.error-banner {
  background-color: #fce4ec;
  color: #c62828;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 13px;
  margin-bottom: 8px;
}

.action-link {
  text-align: center;
  font-size: 13px;
  color: #666;
  margin-top: 16px;

  a {
    color: #e91e63;
    text-decoration: none;
    font-weight: 500;

    &:hover {
      text-decoration: underline;
    }
  }
}
```

---

### Task 12: Add routes and "Forgot password" link

**Files:**
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/pages/auth/login/login.component.html`

**Step 1: Add routes in `app.routes.ts`**

Add imports:

```typescript
import { ForgotPasswordComponent } from './pages/auth/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './pages/auth/reset-password/reset-password.component';
```

Add routes after `register`:

```typescript
{ path: 'forgot-password', component: ForgotPasswordComponent },
{ path: 'reset-password', component: ResetPasswordComponent },
```

**Step 2: Add "Forgot password?" link in login template**

After the password `mat-form-field` closing tag and before the invalid credentials error block, add:

```html
<!-- Forgot password link -->
<p class="forgot-password-link">
  <a routerLink="/forgot-password">{{ 'auth.login.forgotPassword' | transloco }}</a>
</p>
```

**Step 3: Add styling for the link in `login.component.scss`**

```scss
.forgot-password-link {
  text-align: right;
  margin: -4px 0 8px;
  font-size: 13px;

  a {
    color: #e91e63;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}
```

**Step 4: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

**Step 5: Commit**

```bash
git add frontend/
git commit -m "feat(auth): add forgot-password and reset-password pages with translations"
```

---

### Task 13: Write frontend tests

**Files:**
- Create: `frontend/src/app/pages/auth/forgot-password/forgot-password.component.spec.ts`
- Create: `frontend/src/app/pages/auth/reset-password/reset-password.component.spec.ts`

**Step 1: Create ForgotPasswordComponent tests**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from '../../../core/auth/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';

const mockTranslations = {
  'auth.forgotPassword.title': 'Reset your password',
  'auth.forgotPassword.subtitle': 'Enter your email',
  'auth.forgotPassword.email': 'Email',
  'auth.forgotPassword.submit': 'Send',
  'auth.forgotPassword.success': 'Check your inbox',
  'auth.forgotPassword.backToLogin': 'Back',
  'auth.errors.emailRequired': 'Email is required',
  'auth.errors.emailInvalid': 'Email is not valid',
};

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['requestPasswordReset']);

    await TestBed.configureTestingModule({
      imports: [
        ForgotPasswordComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: { defaultLang: 'en', availableLangs: ['en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should not call service when form is invalid', () => {
    component.onSubmit();
    expect(authServiceSpy.requestPasswordReset).not.toHaveBeenCalled();
  });

  it('should show success message after submitting valid email', () => {
    authServiceSpy.requestPasswordReset.and.returnValue(of({ message: 'ok' }));

    component.form.setValue({ email: 'sophie@salon.fr' });
    component.onSubmit();

    expect(authServiceSpy.requestPasswordReset).toHaveBeenCalledWith('sophie@salon.fr');
    expect(component.emailSent).toBeTrue();
  });

  it('should still show success on error (no enumeration)', () => {
    authServiceSpy.requestPasswordReset.and.returnValue(throwError(() => new Error('fail')));

    component.form.setValue({ email: 'sophie@salon.fr' });
    component.onSubmit();

    expect(component.emailSent).toBeTrue();
  });
});
```

**Step 2: Create ResetPasswordComponent tests**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../../core/auth/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

const mockTranslations = {
  'auth.resetPassword.title': 'New password',
  'auth.resetPassword.subtitle': 'Enter new password',
  'auth.resetPassword.newPassword': 'New password',
  'auth.resetPassword.confirmPassword': 'Confirm',
  'auth.resetPassword.passwordHint': '8 chars',
  'auth.resetPassword.submit': 'Update',
  'auth.resetPassword.success': 'Updated',
  'auth.resetPassword.goToLogin': 'Login',
  'auth.resetPassword.invalidToken': 'Invalid token',
  'auth.resetPassword.requestNewLink': 'Request new',
  'auth.errors.passwordRequired': 'Required',
  'auth.errors.passwordTooShort': 'Too short',
  'auth.errors.passwordMismatch': 'Mismatch',
};

describe('ResetPasswordComponent', () => {
  let component: ResetPasswordComponent;
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  function setup(token: string | null) {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['resetPassword']);

    TestBed.configureTestingModule({
      imports: [
        ResetPasswordComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: { defaultLang: 'en', availableLangs: ['en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should show invalid token when no token in URL', () => {
    setup(null);
    expect(component.invalidToken).toBeTrue();
  });

  it('should not submit when passwords do not match', () => {
    setup('valid-token');
    component.form.setValue({ newPassword: 'password123', confirmPassword: 'different1' });
    component.form.markAllAsTouched();
    component.onSubmit();
    expect(authServiceSpy.resetPassword).not.toHaveBeenCalled();
  });

  it('should call resetPassword and show success on valid submit', () => {
    setup('valid-token');
    authServiceSpy.resetPassword.and.returnValue(of({ message: 'ok' }));

    component.form.setValue({ newPassword: 'newpass123', confirmPassword: 'newpass123' });
    component.onSubmit();

    expect(authServiceSpy.resetPassword).toHaveBeenCalledWith('valid-token', 'newpass123');
    expect(component.resetSuccess).toBeTrue();
  });

  it('should show invalid token error on 400 response', () => {
    setup('expired-token');
    const error = new HttpErrorResponse({ status: 400, statusText: 'Bad Request' });
    authServiceSpy.resetPassword.and.returnValue(throwError(() => error));

    component.form.setValue({ newPassword: 'newpass123', confirmPassword: 'newpass123' });
    component.onSubmit();

    expect(component.invalidToken).toBeTrue();
    expect(component.resetSuccess).toBeFalse();
  });
});
```

**Step 3: Verify TypeScript compiles**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

**Step 4: Commit**

```bash
git add frontend/
git commit -m "test(auth): add specs for forgot-password and reset-password components"
```

---

### Task 14: Final verification

**Step 1: Run full backend tests**

Run: `cd backend && ./mvnw test`
Expected: All tests PASS, BUILD SUCCESS

**Step 2: Run frontend TypeScript check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

**Step 3: Verify dev server builds**

Check that `ng serve` (already running) shows no build errors.

**Step 4: Manual smoke test**

1. Navigate to `http://localhost:4200/login` — verify "Forgot password?" link appears
2. Click "Forgot password?" — verify `/forgot-password` page loads with email form
3. Navigate to `http://localhost:4200/reset-password` (no token) — verify "invalid token" error shown
4. Navigate to `http://localhost:4200/reset-password?token=test` — verify password form loads
