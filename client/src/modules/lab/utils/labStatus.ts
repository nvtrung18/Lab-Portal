import type { LabResponse } from '../api';

export function isLabActive(lab: Pick<LabResponse, 'status'>) {
  return lab.status === 'AVAILABLE' || lab.status === 'ACTIVE';
}

export function isLabInactive(lab: Pick<LabResponse, 'status'>) {
  return lab.status === 'INACTIVE' || lab.status === 'ARCHIVED' || lab.status === 'CLOSED';
}
