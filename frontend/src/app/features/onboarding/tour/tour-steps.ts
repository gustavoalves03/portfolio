import { TourStep } from './tour-step.model';

export const TOUR_STEPS: readonly TourStep[] = [
  { key: 'name',         readinessFlag: 'name',            route: '/pro/salon',    tourStep: 'name',          titleKey: 'pro.tour.steps.name.title',         descKey: 'pro.tour.steps.name.desc' },
  { key: 'contact',      readinessFlag: 'hasContact',      route: '/pro/salon',    tourStep: 'contact',       titleKey: 'pro.tour.steps.contact.title',      descKey: 'pro.tour.steps.contact.desc' },
  { key: 'logo',         readinessFlag: 'hasLogo',         route: '/pro/salon',    tourStep: 'logo',          titleKey: 'pro.tour.steps.logo.title',         descKey: 'pro.tour.steps.logo.desc' },
  { key: 'categories',   readinessFlag: 'hasCategory',     route: '/pro/cares',    tourStep: 'categories',    titleKey: 'pro.tour.steps.categories.title',   descKey: 'pro.tour.steps.categories.desc' },
  { key: 'cares',        readinessFlag: 'hasActiveCare',   route: '/pro/cares',    tourStep: 'add-care',      titleKey: 'pro.tour.steps.cares.title',        descKey: 'pro.tour.steps.cares.desc' },
  { key: 'openingHours', readinessFlag: 'hasOpeningHours', route: '/pro/planning', tourStep: 'opening-hours', titleKey: 'pro.tour.steps.openingHours.title', descKey: 'pro.tour.steps.openingHours.desc' },
] as const;
