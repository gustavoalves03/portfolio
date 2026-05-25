export type FeatureKey =
  | 'BOOKING'
  | 'EMPLOYEES'
  | 'PHOTOS'
  | 'SMS_REMINDER'
  | 'CLIENT_FILES'
  | 'ABSENCE_MGMT'
  | 'ONLINE_PAYMENT'
  | 'SHOP'
  | 'LOYALTY'
  | 'MULTI_LOCATION';

export const ALL_FEATURE_KEYS: FeatureKey[] = [
  'BOOKING',
  'EMPLOYEES',
  'PHOTOS',
  'SMS_REMINDER',
  'CLIENT_FILES',
  'ABSENCE_MGMT',
  'ONLINE_PAYMENT',
  'SHOP',
  'LOYALTY',
  'MULTI_LOCATION',
];

export type FeatureFlagSnapshot = Record<FeatureKey, boolean>;
