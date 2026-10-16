import type { LocalizedText, MemberRole } from '../../../../contracts/website-content';

export const memberRoleGroups: { role: MemberRole; label: LocalizedText; title: LocalizedText; showWhenEmpty?: boolean }[] = [
  { role: 'Steering Member', label: { vi: 'Thành viên chỉ đạo', en: 'Steering Member' }, title: { vi: 'Thành viên chỉ đạo', en: 'Steering Members' } },
  { role: 'Core Member', label: { vi: 'Thành viên cốt lõi', en: 'Core Member' }, title: { vi: 'Thành viên cốt lõi', en: 'Core Members' } },
  { role: 'PhD student', label: { vi: 'NCS', en: 'PhD student' }, title: { vi: 'Nghiên cứu sinh', en: 'PhD Students' } },
  { role: 'MSc student', label: { vi: 'ThS', en: 'Master\'s Students' }, title: { vi: 'ThS — Học viên cao học', en: 'Master\'s Students' }, showWhenEmpty: true },
  { role: 'Undergraduate', label: { vi: 'SV NCKH', en: 'Student researcher' }, title: { vi: 'Sinh viên nghiên cứu khoa học', en: 'Student Researchers' } },
  { role: 'Guest', label: { vi: 'Khách mời', en: 'Guests' }, title: { vi: 'Thành viên đối tác', en: 'Partner Members' } },
];

export function matchesMemberRole(memberRole: string, selected: string) {
  return selected === 'other'
    ? !memberRoleGroups.some(group => group.role === memberRole)
    : memberRole === selected;
}

// Display grouping only: preserve legacy roles without adding them to the academic chain.
export function partitionMemberDisplayGroups<T extends { role: string }>(groups: T[]) {
  const academicRoles = new Set(['Steering Member', 'Core Member', 'PhD student', 'MSc student']);
  return {
    hierarchy: groups.filter(group => academicRoles.has(group.role)),
    additional: groups.filter(group => !academicRoles.has(group.role) && group.role !== 'Guest'),
    collaborators: groups.filter(group => group.role === 'Guest'),
  };
}
