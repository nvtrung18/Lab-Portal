import type { LocalizedText, MemberRole } from '../../../../contracts/website-content';

export const memberRoleGroups: { role: MemberRole; label: LocalizedText; title: LocalizedText }[] = [
  { role: 'Steering Member', label: { vi: 'Thành viên chỉ đạo', en: 'Steering Member' }, title: { vi: 'Thành viên chỉ đạo', en: 'Steering Members' } },
  { role: 'Core Member', label: { vi: 'Thành viên cốt lõi', en: 'Core Member' }, title: { vi: 'Thành viên cốt lõi', en: 'Core Members' } },
  { role: 'PhD student', label: { vi: 'NCS', en: 'PhD student' }, title: { vi: 'Nghiên cứu sinh', en: 'PhD Students' } },
  { role: 'MSc student', label: { vi: 'HVCH', en: 'Master\'s student' }, title: { vi: 'Học viên cao học', en: 'Master\'s Students' } },
  { role: 'Undergraduate', label: { vi: 'SV NCKH', en: 'Student researcher' }, title: { vi: 'Sinh viên nghiên cứu khoa học', en: 'Student Researchers' } },
  { role: 'Guest', label: { vi: 'Khách', en: 'Guest' }, title: { vi: 'Thành viên đối tác', en: 'Partner Members' } },
];
