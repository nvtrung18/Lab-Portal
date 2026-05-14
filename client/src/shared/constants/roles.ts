export const ADMIN = 'ADMIN';
export const LAB_MANAGER = 'LAB_MANAGER';
export const STUDENT = 'STUDENT';

export const ROLES = {
  ADMIN,
  LAB_MANAGER,
  STUDENT,
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];
