import { getStoredLocale, translate } from '../i18n';

const message = (key: string) => translate(key, getStoredLocale());

export const VALIDATION_MESSAGES = {
  get required() { return message('validation.required'); },
  get email() { return message('validation.email'); },
  get password() { return message('validation.password'); },
  get dateRange() { return message('validation.dateRange'); },
  get fileType() { return message('validation.fileType'); },
  get reportFileSize() { return message('validation.reportFileSize'); },
  get productFileSize() { return message('validation.productFileSize'); },
  get score() { return message('validation.score'); },
  get comment() { return message('validation.comment'); },
  get researchTitleRequired() { return message('validation.researchTitleRequired'); },
  get groupNameRequired() { return message('validation.groupNameRequired'); },
  get leaderMustBeMember() { return message('validation.leaderMustBeMember'); },
  get memberRequired() { return message('validation.memberRequired'); },
} as const;
