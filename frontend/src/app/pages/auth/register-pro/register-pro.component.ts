import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { UpperCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';

interface Plan {
  id: string;
  name: string;
  monthlyPrice: number;
  yearlyPrice: number;
  features: string[];
  highlighted: boolean;
  badge?: string;
}

@Component({
  selector: 'app-register-pro',
  standalone: true,
  imports: [
    FormsModule, RouterLink, UpperCasePipe,
    MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatCheckboxModule, MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  templateUrl: './register-pro.component.html',
  styleUrl: './register-pro.component.scss',
})
export class RegisterProComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly step = signal<'pricing' | 'account' | 'business'>('pricing');
  readonly isYearly = signal(false);
  readonly selectedPlan = signal<string>('pro');
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);

  // Account fields
  readonly name = signal('');
  readonly email = signal('');
  readonly password = signal('');

  // Business fields
  readonly salonName = signal('');
  readonly phone = signal('');
  readonly addressStreet = signal('');
  readonly addressPostalCode = signal('');
  readonly addressCity = signal('');
  readonly siret = signal('');
  readonly consent = signal(false);

  readonly plans: Plan[] = [
    {
      id: 'free',
      name: 'Découverte',
      monthlyPrice: 0,
      yearlyPrice: 0,
      features: ['5 soins max', 'Réservations en ligne', 'Page salon basique'],
      highlighted: false,
    },
    {
      id: 'pro',
      name: 'Pro',
      monthlyPrice: 29,
      yearlyPrice: 23,
      features: ['Soins illimités', 'Hero image', 'Visible sur Discover', 'Rappels email'],
      highlighted: true,
      badge: 'Recommandé',
    },
    {
      id: 'premium',
      name: 'Premium',
      monthlyPrice: 59,
      yearlyPrice: 47,
      features: ['Tout Pro +', 'Rappels SMS', 'Analytics avancés', 'Support dédié'],
      highlighted: false,
    },
  ];

  getPrice(plan: Plan): number {
    return this.isYearly() ? plan.yearlyPrice : plan.monthlyPrice;
  }

  selectPlan(planId: string): void {
    this.selectedPlan.set(planId);
    this.step.set('account');
  }

  goToStep(s: 'pricing' | 'account' | 'business'): void {
    this.step.set(s);
  }

  isAccountValid(): boolean {
    return this.name().trim().length > 0
      && this.email().includes('@')
      && this.password().length >= 8;
  }

  isBusinessValid(): boolean {
    return this.salonName().trim().length > 0 && this.consent();
  }

  continueToBusinessStep(): void {
    if (this.isAccountValid()) {
      this.step.set('business');
    }
  }

  submit(): void {
    if (!this.isAccountValid() || !this.isBusinessValid()) return;

    this.isLoading.set(true);
    this.error.set(null);

    this.authService.registerPro({
      name: this.name().trim(),
      email: this.email().trim(),
      password: this.password(),
      salonName: this.salonName().trim(),
      phone: this.phone().trim(),
      addressStreet: this.addressStreet().trim(),
      addressPostalCode: this.addressPostalCode().trim(),
      addressCity: this.addressCity().trim(),
      siret: this.siret().trim(),
      plan: this.selectedPlan(),
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/pro/dashboard']);
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 409) {
          this.error.set('register.errors.emailConflict');
          this.step.set('account');
        } else {
          this.error.set('register.errors.generic');
        }
      },
    });
  }
}
