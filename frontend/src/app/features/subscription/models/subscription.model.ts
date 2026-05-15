export type SubscriptionTier = 'VITRINE' | 'GESTION' | 'PREMIUM';

export type SubscriptionBilling = 'FREE' | 'MONTHLY' | 'YEARLY';

export type SubscriptionStatus =
  | 'VITRINE_FREE'
  | 'TRIALING'
  | 'ACTIVE'
  | 'PAST_DUE'
  | 'UNPAID'
  | 'CANCELED'
  | 'INCOMPLETE'
  | 'INCOMPLETE_EXPIRED'
  | 'PAUSED';

export interface PricingPlan {
  tier: SubscriptionTier;
  billing: SubscriptionBilling;
  monthlyPriceEuros: number;
  currency: string;
}

export interface SetupIntentResponse {
  clientSecret: string;
}

export interface CreateSubscriptionRequest {
  tier: SubscriptionTier;
  billing: SubscriptionBilling;
  paymentMethodId: string;
}

export interface SubscriptionResponse {
  tier: SubscriptionTier;
  billing: SubscriptionBilling;
  status: SubscriptionStatus;
  stripeCustomerId: string | null;
  stripeSubscriptionId: string | null;
  currentPeriodEnd: string | null;
  trialEnd: string | null;
}

export interface PortalSessionResponse {
  url: string;
}

export interface StripeConfigResponse {
  publishableKey: string;
}
