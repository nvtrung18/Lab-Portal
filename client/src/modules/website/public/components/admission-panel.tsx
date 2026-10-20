import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { createPortal } from 'react-dom';
import type { UserProfileResponse } from '../../../user/api';
import { useUserApplications } from '../../../application/hooks';
import { LabList } from '../../../lab/pages/LabList';
import { Modal } from '../../../../shared/components';
import { STUDENT } from '../../../../shared/constants/roles';
import { getPrimaryRedirectPath } from '../../../auth/hooks';
import { useLab } from './lab-provider';
import Link from './site-link';

export function AdmissionPanel({ user, signedIn, checking, failed, onRefresh }: {
  user?: UserProfileResponse;
  signedIn: boolean;
  checking: boolean;
  failed: boolean;
  onRefresh: () => void;
}) {
  const { locale, page, preview } = useLab();
  const { hash } = useLocation();
  const student = user?.roles.some(role => role.replace(/^ROLE_/, '') === STUDENT);
  const pending = student && user && getPrimaryRedirectPath(user) === '/vi';
  const applications = useUserApplications(!preview && pending ? user?.id : undefined);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (!preview && pending && hash === '#lab-admission') {
      document.getElementById('lab-admission')?.scrollIntoView({ block: 'start' });
    }
  }, [preview, pending, hash]);
  const label = (vi: string, en: string) => locale === 'vi' ? vi : en;
  if (preview || checking || (!failed && !pending && (signedIn || page?.template !== 'join'))) return null;

  const refresh = () => {
    onRefresh();
    if (pending) void applications.refetch();
  };

  return <>
    <section className="section soft-section admission-panel" id="lab-admission">
      <div className="container">
        <h2>{label('Đăng ký tham gia lab', 'Apply to join the lab')}</h2>
        {!signedIn ? <>
          <p>{label('Đăng nhập để nộp CV. Bạn sẽ vào khu vực nội bộ sau khi hồ sơ được duyệt.',
            'Sign in to submit your CV. Internal access becomes available after approval.')}</p>
          <Link className="text-link" href={`/login?returnUrl=${encodeURIComponent(`/${locale}#lab-admission`)}`}>
            {label('Đăng nhập để nộp CV', 'Sign in to submit your CV')}
          </Link>
        </> : <>
          <p role="status">{failed
            ? label('Chưa kiểm tra được quyền truy cập. Vui lòng thử lại.', 'Unable to check your access. Please try again.')
            : label('Bạn chưa có tư cách thành viên đang hoạt động. Bạn có thể xem website, nộp CV và theo dõi hồ sơ tại đây.',
              'You do not have an active membership yet. You can browse the website, submit your CV and track your application here.')}</p>
          {pending && <button type="button" className="admission-button" onClick={() => setOpen(true)}>
            {label('Nộp CV / đăng ký tham gia', 'Submit CV / apply')}
          </button>}
          <button type="button" className="admission-button secondary" disabled={applications.isFetching} onClick={refresh}>
            {label('Cập nhật trạng thái', 'Refresh status')}
          </button>
          {pending && applications.isError && <p role="alert">
            {label('Không tải được trạng thái hồ sơ. Vui lòng thử lại.', 'Unable to load application status. Please try again.')}
          </p>}
          {pending && applications.data?.length ? <ul className="admission-status-list">
            {applications.data.map(application => <li key={application.id}>
              <strong>{application.labName}</strong>
              <span>{application.status === 'PENDING' ? label('Đang chờ duyệt', 'Awaiting approval')
                : application.status === 'REVIEWING' ? label('Đang xét duyệt', 'Under review')
                : application.status === 'APPROVED' ? label('Hồ sơ đã được duyệt', 'Application approved')
                  : application.status === 'REJECTED' ? label('Hồ sơ không được duyệt', 'Application rejected')
                    : application.status}</span>
            </li>)}
          </ul> : null}
        </>}
      </div>
    </section>
    {open && pending && createPortal(<div className="app-shell">
      <Modal title={label('Nộp CV tham gia lĩnh vực nghiên cứu', 'Apply to join a research area')} size="full" onClose={() => setOpen(false)}>
        <LabList />
      </Modal>
    </div>, document.body)}
  </>;
}
