import { FormEvent, useMemo, useState } from 'react';

import { Button, EmptyState, ErrorState, LoadingState, Modal } from '../../../shared/components';
import { LAB_MANAGER, STUDENT } from '../../../shared/constants/roles';
import { VALIDATION_MESSAGES } from '../../../shared/utils';
import { useEvaluationsByGroup, useResearchGroup, useSubmitEvaluation } from '../hooks';
import type { ResearchEvaluation } from '../types';

const SCORE_FIELDS = [
  ['contributionScore', 'Điểm đóng góp nhóm'],
  ['taskScore', 'Điểm hoàn thành nhiệm vụ'],
  ['reportScore', 'Điểm chất lượng báo cáo'],
  ['productScore', 'Điểm chất lượng sản phẩm'],
  ['attitudeScore', 'Điểm thái độ nghiên cứu'],
] as const;

type ScoreField = (typeof SCORE_FIELDS)[number][0];

interface EvaluationPageProps {
  projectId: number;
  groupId: number;
  role: typeof LAB_MANAGER | typeof STUDENT | string;
  currentUserId?: number | null;
}

interface EvaluationStudent {
  userId: number;
  fullName: string;
  email?: string | null;
  groupId: number;
  groupName: string;
}

type EvaluationFormState = Record<ScoreField, string> & {
  lecturerComment: string;
};

const DEFAULT_FORM_STATE: EvaluationFormState = {
  contributionScore: '0',
  taskScore: '0',
  reportScore: '0',
  productScore: '0',
  attitudeScore: '0',
  lecturerComment: '',
};

export function EvaluationPage({ projectId, groupId, role, currentUserId }: EvaluationPageProps) {
  const isManager = role === LAB_MANAGER;
  const { data: evaluations = [], isError, isLoading, refetch } = useEvaluationsByGroup(groupId);
  const { data: group, isLoading: isLoadingGroup } = useResearchGroup(groupId);
  const [editingStudent, setEditingStudent] = useState<EvaluationStudent | null>(null);
  const [formState, setFormState] = useState<EvaluationFormState>(DEFAULT_FORM_STATE);
  const [formError, setFormError] = useState<string | null>(null);
  const submitEvaluation = useSubmitEvaluation(projectId);

  const students = useMemo<EvaluationStudent[]>(() => {
    if (!group || !group.members) {
      return [];
    }
    return group.members.map((member) => ({
      userId: member.userId,
      fullName: member.fullName ?? member.email ?? `#${member.userId}`,
      email: member.email,
      groupId: group.id,
      groupName: group.name,
    })).sort((first, second) => first.fullName.localeCompare(second.fullName));
  }, [group]);

  const evaluationByStudentId = useMemo(() => new Map(evaluations.map((evaluation) => [evaluation.studentId, evaluation])), [evaluations]);
  const studentEvaluations = currentUserId
    ? evaluations.filter((evaluation) => evaluation.studentId === currentUserId)
    : [];

  function openEvaluationForm(student: EvaluationStudent) {
    const evaluation = evaluationByStudentId.get(student.userId);
    setEditingStudent(student);
    setFormError(null);
    setFormState(evaluation ? toFormState(evaluation) : DEFAULT_FORM_STATE);
  }

  function closeForm() {
    setEditingStudent(null);
    setFormState(DEFAULT_FORM_STATE);
    setFormError(null);
  }

  function updateScore(field: ScoreField, value: string) {
    setFormState((current) => ({ ...current, [field]: value }));
    setFormError(null);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editingStudent) {
      return;
    }
    const parsedScores = parseScores(formState);
    if (!parsedScores) {
      setFormError(VALIDATION_MESSAGES.score);
      return;
    }
    if (formState.lecturerComment.trim().length > 2000) {
      setFormError('Nhận xét tối đa 2000 ký tự.');
      return;
    }

    submitEvaluation.mutate(
      {
        projectId,
        groupId: editingStudent.groupId,
        studentId: editingStudent.userId,
        ...parsedScores,
        lecturerComment: formState.lecturerComment.trim(),
      },
      { onSuccess: closeForm },
    );
  }

  if (!isManager) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-lg font-semibold text-slate-950">Đánh giá của tôi</h3>
        {isLoading ? (
          <LoadingState className="mt-5">Đang tải đánh giá...</LoadingState>
        ) : isError ? (
          <ErrorState className="mt-5" onRetry={() => refetch()}>
            Không thể tải đánh giá.
          </ErrorState>
        ) : !studentEvaluations.length ? (
          <EmptyState className="mt-5">Bạn chưa có đánh giá cho đề tài này.</EmptyState>
        ) : (
          <div className="mt-5 space-y-3">
            {studentEvaluations.map((evaluation) => (
              <EvaluationCard evaluation={evaluation} key={evaluation.id} />
            ))}
          </div>
        )}
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <h3 className="text-lg font-semibold text-slate-950">Đánh giá kết quả nghiên cứu</h3>
        <p className="mt-1 text-sm text-slate-600">Chấm điểm từng sinh viên trong đề tài theo thang 0 đến 10.</p>
      </div>

      <Modal
        closeDisabled={submitEvaluation.isPending}
        footer={editingStudent ? (
          <>
            <Button disabled={submitEvaluation.isPending} onClick={closeForm} type="button" variant="outline">
              Hủy
            </Button>
            <Button form="evaluation-form" loading={submitEvaluation.isPending} loadingText="Đang lưu..." type="submit">
              Lưu đánh giá
            </Button>
          </>
        ) : null}
        isOpen={Boolean(editingStudent)}
        onClose={closeForm}
        size="xl"
        title={editingStudent ? (evaluationByStudentId.has(editingStudent.userId) ? 'Cập nhật đánh giá' : 'Chấm điểm') : 'Chấm điểm'}
      >
      {editingStudent ? (
        <form id="evaluation-form" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h4 className="font-semibold text-slate-950">
                {evaluationByStudentId.has(editingStudent.userId) ? 'Cập nhật đánh giá' : 'Chấm điểm'}
              </h4>
              <p className="mt-1 text-sm text-slate-600">
                {editingStudent.fullName} · {editingStudent.groupName}
              </p>
            </div>
          </div>

          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            {SCORE_FIELDS.map(([field, label]) => (
              <label className="block text-sm" key={field}>
                <span className="mb-1 block font-semibold text-slate-700">{label}</span>
                <input
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                  disabled={submitEvaluation.isPending}
                  max="10"
                  min="0"
                  step="0.5"
                  type="number"
                  value={formState[field]}
                  onChange={(event) => updateScore(field, event.target.value)}
                />
              </label>
            ))}
          </div>

          <label className="mt-4 block text-sm">
            <span className="mb-1 block font-semibold text-slate-700">Nhận xét của quản lý</span>
            <textarea
              className="min-h-24 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              disabled={submitEvaluation.isPending}
              maxLength={2000}
              value={formState.lecturerComment}
              onChange={(event) => {
                setFormState((current) => ({ ...current, lecturerComment: event.target.value }));
                setFormError(null);
              }}
            />
            <span className="mt-1 block text-xs text-slate-500">{formState.lecturerComment.length}/2000</span>
          </label>

          {formError ? <p className="mt-3 text-sm font-semibold text-red-700">{formError}</p> : null}

        </form>
      ) : null}
      </Modal>

      {isLoading || isLoadingGroup ? (
        <LoadingState className="mt-5">Đang tải danh sách đánh giá...</LoadingState>
      ) : isError ? (
        <ErrorState className="mt-5" onRetry={() => refetch()}>
          Không thể tải danh sách đánh giá.
        </ErrorState>
      ) : !students.length && !evaluations.length ? (
        <EmptyState className="mt-5">Chưa có đánh giá nào cho đề tài này.</EmptyState>
      ) : (
        <div className="mt-5 overflow-x-auto rounded-md border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-600">
              <tr>
                <th className="px-3 py-3">Họ tên</th>
                <th className="px-3 py-3">Nhóm</th>
                <th className="px-3 py-3">Đóng góp nhóm</th>
                <th className="px-3 py-3">Nhiệm vụ</th>
                <th className="px-3 py-3">Báo cáo</th>
                <th className="px-3 py-3">Sản phẩm</th>
                <th className="px-3 py-3">Thái độ</th>
                <th className="px-3 py-3">Tổng</th>
                <th className="px-3 py-3">Nhận xét</th>
                <th className="px-3 py-3">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {students.map((student) => {
                const evaluation = evaluationByStudentId.get(student.userId);
                return (
                  <tr key={`${student.groupId}-${student.userId}`}>
                    <td className="px-3 py-3 font-semibold text-slate-950">{student.fullName}</td>
                    <td className="px-3 py-3 text-slate-600">{student.groupName}</td>
                    <ScoreCell value={evaluation?.contributionScore} />
                    <ScoreCell value={evaluation?.taskScore} />
                    <ScoreCell value={evaluation?.reportScore} />
                    <ScoreCell value={evaluation?.productScore} />
                    <ScoreCell value={evaluation?.attitudeScore} />
                    <ScoreCell strong value={evaluation?.totalScore} />
                    <td className="max-w-xs px-3 py-3 text-slate-600">
                      <span className="block max-h-12 overflow-hidden break-words">
                        {evaluation?.lecturerComment || 'Chưa có nhận xét'}
                      </span>
                    </td>
                    <td className="px-3 py-3">
                      <Button onClick={() => openEvaluationForm(student)} size="sm" variant="outline">
                        {evaluation ? 'Cập nhật đánh giá' : 'Chấm điểm'}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function EvaluationCard({ evaluation }: { evaluation: ResearchEvaluation }) {
  return (
    <article className="rounded-md border border-slate-200 p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h4 className="font-semibold text-slate-950">{evaluation.studentName ?? 'Đánh giá cá nhân'}</h4>
          <p className="mt-1 text-sm text-slate-600">{evaluation.groupName ?? 'Nhóm nghiên cứu'}</p>
        </div>
        <div className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white">
          Tổng điểm {formatScore(evaluation.totalScore)}
        </div>
      </div>
      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-5">
        <EvaluationField label="Điểm đóng góp nhóm" value={formatScore(evaluation.contributionScore)} />
        <EvaluationField label="Điểm hoàn thành nhiệm vụ" value={formatScore(evaluation.taskScore)} />
        <EvaluationField label="Điểm chất lượng báo cáo" value={formatScore(evaluation.reportScore)} />
        <EvaluationField label="Điểm chất lượng sản phẩm" value={formatScore(evaluation.productScore)} />
        <EvaluationField label="Điểm thái độ nghiên cứu" value={formatScore(evaluation.attitudeScore)} />
      </dl>
      <div className="mt-4 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
        <span className="font-semibold text-slate-900">Nhận xét của quản lý: </span>
        {evaluation.lecturerComment || 'Chưa có nhận xét.'}
      </div>
    </article>
  );
}

function EvaluationField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-semibold text-slate-700">{label}</dt>
      <dd className="mt-1 text-slate-600">{value}</dd>
    </div>
  );
}

function ScoreCell({ strong = false, value }: { strong?: boolean; value?: number }) {
  return (
    <td className={`px-3 py-3 ${strong ? 'font-semibold text-slate-950' : 'text-slate-600'}`}>
      {value == null ? 'Chưa chấm' : formatScore(value)}
    </td>
  );
}

function parseScores(formState: EvaluationFormState): Record<ScoreField, number> | null {
  const parsed = {} as Record<ScoreField, number>;
  for (const [field] of SCORE_FIELDS) {
    const score = Number(formState[field]);
    if (!Number.isFinite(score) || score < 0 || score > 10) {
      return null;
    }
    parsed[field] = score;
  }
  return parsed;
}

// Keep formatScore helper
function toFormState(evaluation: ResearchEvaluation): EvaluationFormState {
  return {
    contributionScore: String(evaluation.contributionScore),
    taskScore: String(evaluation.taskScore),
    reportScore: String(evaluation.reportScore),
    productScore: String(evaluation.productScore),
    attitudeScore: String(evaluation.attitudeScore),
    lecturerComment: evaluation.lecturerComment ?? '',
  };
}

function formatScore(score: number) {
  return Number.isInteger(score) ? String(score) : score.toFixed(1);
}
