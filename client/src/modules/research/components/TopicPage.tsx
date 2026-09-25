import { useEffect, useState } from 'react';

import { useI18n } from '../../../shared/i18n';
import { useCreateResearchTopic, useResearchTopicsByLab } from '../hooks';
import type { ResearchTopic } from '../types';
import { formatDate, formatTopicStatus, getStatusClass } from '../utils';
import { CreateTopicModal } from './CreateTopicModal';

interface TopicPageProps {
  labId: number | null;
  canCreate: boolean;
  selectedTopicId: number | null;
  onSelectTopic: (topic: ResearchTopic | null) => void;
}

export function TopicPage({ labId, canCreate, selectedTopicId, onSelectTopic }: TopicPageProps) {
  const { locale, t } = useI18n();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const { data: topics = [], isError, isLoading, refetch } = useResearchTopicsByLab(labId);
  const createTopic = useCreateResearchTopic(labId);

  useEffect(() => {
    if (!topics.length) {
      onSelectTopic(null);
      return;
    }

    const selectedTopic = topics.find((topic) => topic.id === selectedTopicId) ?? topics[0];
    onSelectTopic(selectedTopic);
  }, [onSelectTopic, selectedTopicId, topics]);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="text-lg font-semibold text-slate-950">{t('research.topic.title')}</h3>
        </div>
        {canCreate ? (
          <button className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white" type="button" onClick={() => setIsCreateOpen(true)}>
            {t('research.topic.createTitle')}
          </button>
        ) : null}
      </div>

      {isLoading ? (
        <p className="mt-5 text-sm text-slate-600">{t('research.topic.loading')}</p>
      ) : isError ? (
        <div className="mt-5 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {t('research.topic.loadFailed')}
          <button className="ml-3 font-semibold underline" type="button" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        </div>
      ) : !topics.length ? (
        <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          {t('research.topic.empty')}
        </div>
      ) : (
        <div className="mt-5 grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
          {topics.map((topic) => {
            const isSelected = selectedTopicId === topic.id;
            return (
              <article
                key={topic.id}
                className={[
                  'rounded-md border p-4 transition',
                  isSelected ? 'border-slate-900 bg-slate-50' : 'border-slate-200 bg-white hover:border-slate-300',
                ].join(' ')}
              >
                <div className="flex items-start justify-between gap-3">
                  <h4 className="text-base font-semibold text-slate-950">{topic.name}</h4>
                  <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ${getStatusClass(topic.status)}`}>
                    {formatTopicStatus(topic.status, locale)}
                  </span>
                </div>
                {topic.description ? <p className="mt-3 text-sm text-slate-600">{topic.description}</p> : null}
                <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
                  <div>
                    <dt className="font-semibold text-slate-700">{t('research.topic.groupCount')}</dt>
                    <dd className="mt-1 text-slate-600">{topic.groupCount ?? 0}</dd>
                  </div>
                  <div>
                    <dt className="font-semibold text-slate-700">{t('research.topic.createdAt')}</dt>
                    <dd className="mt-1 text-slate-600">{formatDate(topic.createdAt, locale)}</dd>
                  </div>
                </dl>
                <button
                  className="mt-4 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                  type="button"
                  onClick={() => onSelectTopic(topic)}
                >
                  {t('research.topic.viewGroups')}
                </button>
              </article>
            );
          })}
        </div>
      )}

      <CreateTopicModal
        isOpen={isCreateOpen}
        labId={labId}
        isSubmitting={createTopic.isPending}
        onClose={() => setIsCreateOpen(false)}
        onSubmit={(payload) => createTopic.mutate(payload, { onSuccess: () => setIsCreateOpen(false) })}
      />
    </section>
  );
}
