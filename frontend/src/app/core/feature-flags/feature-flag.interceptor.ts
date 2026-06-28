import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Feature gating is enforced visually, not by redirection: a gated page still
 * renders but is blurred behind an <lp-feature-locked> upsell overlay (driven by
 * FeatureFlagsStore, no backend round-trip). A FEATURE_DISABLED 403 only happens
 * if a gated request slips through; we let it propagate so the caller's own error
 * handling kicks in — no global redirect, no snackbar that would fire on every
 * blurred page load.
 */
export const featureFlagInterceptor: HttpInterceptorFn = (req, next) => next(req);
