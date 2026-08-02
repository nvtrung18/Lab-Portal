import axios from 'axios';
import { FormEvent, useEffect, useState } from 'react';
import { TaskProposalContractError } from '../api/taskApi';
import { useReviewTaskProposal, useSubmitTaskProposal, useTaskProposals } from '../hooks';

const priorities = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const;
const types = ['TASK', 'SUBTASK', 'BUG', 'EXPERIMENT', 'DOCUMENT', 'REVIEW'] as const;

type ProposalFailureKind = 'forbidden' | 'not-found' | 'conflict' | 'contract' | 'unavailable';
type SubmitReconciliation = {
  outcome: 'submitted' | 'unknown';
  state: 'pending' | 'failed';
};

function proposalFailureKind(error: unknown): ProposalFailureKind {
  if (error instanceof TaskProposalContractError) return 'contract';
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 403) return 'forbidden';
    if (error.response?.status === 404) return 'not-found';
    if (error.response?.status === 409) return 'conflict';
  }
  return 'unavailable';
}

function listFailureMessage(kind: ProposalFailureKind) {
  switch (kind) {
    case 'forbidden': return 'You no longer have access to proposals in this scope.';
    case 'not-found': return 'This project or proposal scope is no longer available.';
    case 'conflict': return 'The proposal list changed while it was loading. Refresh to continue.';
    case 'contract': return 'Proposal data could not be verified. No proposal actions are available.';
    default: return 'Unable to load proposals. No proposal actions are available.';
  }
}

export function TaskProposalPanel({ projectId, groupId, canSubmit, groupScope }: {
  projectId: number;
  groupId: number | null;
  canSubmit: boolean;
  groupScope: ReadonlyArray<{ groupId: number; role: string }>;
}) {
  const [page, setPage] = useState(0);
  const proposals = useTaskProposals(projectId, page, 20, undefined, groupScope);
  const submit = useSubmitTaskProposal(projectId);
  const review = useReviewTaskProposal();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<(typeof priorities)[number]>('MEDIUM');
  const [type, setType] = useState<(typeof types)[number]>('TASK');
  const [dueDate, setDueDate] = useState('');
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [submitReconciliation, setSubmitReconciliation] = useState<SubmitReconciliation | null>(null);

  useEffect(() => {
    if (!submitReconciliation || submitReconciliation.state !== 'pending' || page !== 0) return;
    let active = true;
    const outcome = submitReconciliation.outcome;
    void proposals.refetch().then((result) => {
      if (!active) return;
      if (result.isSuccess && !result.isError) {
        setSubmitReconciliation(null);
        setMessage(outcome === 'submitted'
          ? 'Proposal submitted and the authoritative list was refreshed.'
          : 'The authoritative list was refreshed. Review it before submitting again.');
      } else {
        setSubmitReconciliation({ outcome, state: 'failed' });
        setMessage(outcome === 'submitted'
          ? 'Proposal submitted; status refresh is unavailable. Refresh the list before submitting again.'
          : 'Submission status could not be confirmed. Refresh the list before submitting again.');
      }
    });
    return () => { active = false; };
  }, [page, proposals.refetch, submitReconciliation]);

  function beginSubmitReconciliation(outcome: SubmitReconciliation['outcome']) {
    setPage(0);
    setSubmitReconciliation({ outcome, state: 'pending' });
    setMessage(outcome === 'submitted'
      ? 'Proposal submitted; refreshing the authoritative list…'
      : 'Submission status is unknown; checking the authoritative list…');
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!groupId || !title.trim() || submit.isPending || submitReconciliation) return;
    setMessage(null);
    try {
      await submit.mutateAsync({ projectId, groupId, title: title.trim(), description: description.trim() || undefined, priority, type, dueDate: dueDate || undefined });
      setTitle(''); setDescription(''); setDueDate('');
      beginSubmitReconciliation('submitted');
    } catch {
      beginSubmitReconciliation('unknown');
    }
  }

  async function handleReview(proposalId: number, decision: 'approve' | 'reject') {
    if (review.isPending || (decision === 'reject' && !reason.trim())) return;
    setMessage(null);
    try {
      await review.mutateAsync({ proposalId, decision, reason: reason.trim() });
      setReason('');
      const refreshed = await proposals.refetch();
      setMessage(refreshed.isSuccess && !refreshed.isError
        ? 'Review completed and the authoritative list was refreshed.'
        : 'Review completed; status refresh is unavailable. Retry the list refresh.');
    } catch (error) {
      const failure = proposalFailureKind(error);
      switch (failure) {
        case 'forbidden':
          setMessage('Your review permission changed. The authoritative list is being refreshed.');
          break;
        case 'not-found':
          setMessage('This proposal is no longer available. The authoritative list is being refreshed.');
          break;
        case 'conflict':
          setMessage('This proposal was already reviewed. The authoritative list is being refreshed.');
          break;
        case 'contract':
          setMessage('The review result could not be verified. The authoritative list is being refreshed.');
          break;
        default:
          setMessage('Unable to confirm the review. The authoritative list is being refreshed.');
      }
      await proposals.refetch();
    }
  }

  function retryAuthoritativeList() {
    if (submitReconciliation) {
      setSubmitReconciliation({ ...submitReconciliation, state: 'pending' });
      return;
    }
    void proposals.refetch();
  }

  const hasAuthoritativeList = proposals.isSuccess && !proposals.isError && proposals.data !== undefined;
  const visibleProposals = hasAuthoritativeList ? proposals.data.content : [];
  const listError = proposals.isError ? proposalFailureKind(proposals.error) : null;
  const submitDisabled = submit.isPending || submitReconciliation !== null;

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="task-proposals-title">
      <h2 id="task-proposals-title" className="text-base font-semibold text-slate-950">Task proposals</h2>
      <p className="mt-1 text-sm text-slate-600">Persisted proposals visible in your current scope.</p>
      {message ? <p className="mt-3 rounded-md bg-slate-50 p-3 text-sm text-slate-700" role="status">{message}</p> : null}
      {canSubmit && groupId ? (
        <form className="mt-5 grid gap-4 border-t border-slate-200 pt-5" onSubmit={handleSubmit}>
          <div><label className="text-sm font-semibold text-slate-700" htmlFor="proposal-title">Title</label><input id="proposal-title" required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm" /></div>
          <div><label className="text-sm font-semibold text-slate-700" htmlFor="proposal-description">Description</label><textarea id="proposal-description" maxLength={4000} rows={3} value={description} onChange={(event) => setDescription(event.target.value)} className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm" /></div>
          <div className="grid gap-4 sm:grid-cols-3"><label className="text-sm font-semibold text-slate-700">Priority<select value={priority} onChange={(event) => setPriority(event.target.value as typeof priority)} className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm">{priorities.map((value) => <option key={value}>{value}</option>)}</select></label><label className="text-sm font-semibold text-slate-700">Type<select value={type} onChange={(event) => setType(event.target.value as typeof type)} className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm">{types.map((value) => <option key={value}>{value}</option>)}</select></label><label className="text-sm font-semibold text-slate-700">Due date<input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm" /></label></div>
          <button type="submit" disabled={submitDisabled} className="w-fit rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{submit.isPending ? 'Submitting…' : submitReconciliation ? 'Refresh required' : 'Submit proposal'}</button>
          {submitReconciliation?.state === 'failed' ? <button type="button" className="w-fit text-sm font-semibold text-slate-700 underline" onClick={retryAuthoritativeList}>Refresh before submitting again</button> : null}
        </form>
      ) : canSubmit ? <p className="mt-5 rounded-md bg-amber-50 p-3 text-sm text-amber-800">A current group in this project is required before submitting.</p> : null}
      <div className="mt-6 border-t border-slate-200 pt-5">
        {proposals.isLoading ? <p className="text-sm text-slate-600" role="status">Loading proposals…</p> : null}
        {listError ? <div className="rounded-md bg-red-50 p-3 text-sm text-red-800"><p>{listFailureMessage(listError)}</p><button type="button" className="mt-2 underline" onClick={retryAuthoritativeList}>Refresh authoritative list</button></div> : null}
        {!proposals.isLoading && hasAuthoritativeList && visibleProposals.length === 0 ? <p className="text-sm text-slate-600">No proposals are visible in the current scope.</p> : null}
        <div className="space-y-3">{visibleProposals.map((proposal) => <article key={proposal.id} className="rounded-md border border-slate-200 p-4"><div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-semibold text-slate-900">{proposal.title}</h3><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold">{proposal.status}</span></div><p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{proposal.description || 'No description.'}</p>{proposal.reason ? <p className="mt-2 text-sm text-red-700">Review note: {proposal.reason}</p> : null}{proposal.canReview && proposal.status === 'PENDING' ? <div className="mt-4"><label className="text-sm font-semibold text-slate-700" htmlFor={`proposal-reason-${proposal.id}`}>Review note (required when rejecting)</label><textarea id={`proposal-reason-${proposal.id}`} rows={2} value={reason} onChange={(event) => setReason(event.target.value)} className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm" /><div className="mt-2 flex gap-2"><button type="button" onClick={() => handleReview(proposal.id, 'approve')} disabled={review.isPending} className="rounded-md bg-emerald-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">Approve</button><button type="button" onClick={() => handleReview(proposal.id, 'reject')} disabled={review.isPending || !reason.trim()} className="rounded-md border border-red-300 px-3 py-2 text-sm font-semibold text-red-700 disabled:opacity-50">Reject</button></div></div> : null}</article>)}</div>
        {hasAuthoritativeList && proposals.data.totalPages > 1 ? <div className="mt-4 flex items-center justify-between"><button type="button" disabled={page === 0 || proposals.isFetching} onClick={() => setPage((current) => Math.max(0, current - 1))} className="rounded-md border px-3 py-2 text-sm disabled:opacity-50">Previous</button><span className="text-sm text-slate-600">Page {page + 1} of {proposals.data.totalPages}</span><button type="button" disabled={page + 1 >= proposals.data.totalPages || proposals.isFetching} onClick={() => setPage((current) => current + 1)} className="rounded-md border px-3 py-2 text-sm disabled:opacity-50">Next</button></div> : null}
      </div>
    </section>
  );
}
