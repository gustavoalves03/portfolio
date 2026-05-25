// frontend/e2e/test-data/fixtures.ts

export const CARES = [
  { id: 1, name: 'Soin visage', duration: 45, price: 5000, categoryId: 1, status: 'ACTIVE' },
  { id: 2, name: 'Massage dos', duration: 30, price: 3500, categoryId: 1, status: 'ACTIVE' },
];

export const AVAILABLE_SLOTS = [
  { startTime: '10:00', endTime: '10:45' },
  { startTime: '14:30', endTime: '15:15' },
];

export const SLOTS_BY_CARE = [
  { time: '10:00', employees: [{ id: 1, name: 'Test Employee', available: true }] },
  { time: '14:30', employees: [{ id: 1, name: 'Test Employee', available: true }] },
];

export const SALON_CLIENTS = [
  { id: 10, name: 'Marie Dupont', phone: '+33612345678', email: 'marie@test.fr' },
  { id: 11, name: 'Julie Robert', phone: '+33698765432', email: 'julie@test.fr' },
];

export const EMPLOYEES = [
  { id: 5, name: 'Sophie', role: 'EMPLOYEE' },
];

export const PUBLIC_CARES = [
  {
    id: 1,
    name: 'Soin visage',
    description: 'Soin du visage relaxant',
    duration: 45,
    price: 5000,
    imageUrls: [],
  },
  {
    id: 2,
    name: 'Massage dos',
    description: 'Massage relaxant du dos',
    duration: 30,
    price: 3500,
    imageUrls: [],
  },
];

export const PUBLIC_SALON = {
  slug: 'beaute-du-regard',
  name: 'Beauté du Regard',
  description: 'Institut de beauté',
  logoUrl: null,
  heroImageUrl: null,
  addressStreet: null,
  addressPostalCode: null,
  addressCity: null,
  addressCountry: null,
  phone: null,
  contactEmail: null,
  categories: [
    {
      name: 'Soins',
      cares: PUBLIC_CARES,
    },
  ],
};
