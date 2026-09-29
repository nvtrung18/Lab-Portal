import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { LocateFixed, Network, Save, Trash2 } from 'lucide-react';

import { getCurrentCoordinates } from '../../../shared/utils';
import { useI18n } from '../../../shared/i18n';
import {
  useAddLabAllowedNetwork,
  useDisableLabAllowedNetwork,
  useLabCheckinPolicy,
  useRegisterCurrentLabNetwork,
  useUpdateLabCheckinPolicy,
} from '../hooks';

interface LabCheckinPolicyCardProps {
  labId: number;
}

interface PolicyForm {
  latitude: string;
  longitude: string;
  radiusMeters: string;
  maxAccuracyMeters: string;
  enabled: boolean;
}

const EMPTY_FORM: PolicyForm = {
  latitude: '',
  longitude: '',
  radiusMeters: '',
  maxAccuracyMeters: '',
  enabled: false,
};

export function LabCheckinPolicyCard({ labId }: LabCheckinPolicyCardProps) {
  const { t } = useI18n();
  const { data: policy, isLoading, isError } = useLabCheckinPolicy(labId);
  const updatePolicy = useUpdateLabCheckinPolicy(labId);
  const addNetwork = useAddLabAllowedNetwork(labId);
  const registerCurrentNetwork = useRegisterCurrentLabNetwork(labId);
  const disableNetwork = useDisableLabAllowedNetwork(labId);
  const [form, setForm] = useState<PolicyForm>(EMPTY_FORM);
  const [network, setNetwork] = useState('');
  const [validationError, setValidationError] = useState('');
  const [isLocating, setIsLocating] = useState(false);
  const [locationError, setLocationError] = useState('');
  const autoLocateAttempted = useRef(false);

  const locateCurrentCoordinates = useCallback(async () => {
    setLocationError('');
    setIsLocating(true);
    try {
      const coordinates = await getCurrentCoordinates();
      setForm((current) => ({
        ...current,
        latitude: String(coordinates.latitude),
        longitude: String(coordinates.longitude),
      }));
    } catch (error) {
      setLocationError(error instanceof Error ? error.message : t('common.loadError'));
    } finally {
      setIsLocating(false);
    }
  }, [t]);

  useEffect(() => {
    if (!policy) return;
    setForm({
      latitude: policy.latitude?.toString() ?? '',
      longitude: policy.longitude?.toString() ?? '',
      radiusMeters: policy.radiusMeters?.toString() ?? '',
      maxAccuracyMeters: policy.maxAccuracyMeters?.toString() ?? '',
      enabled: policy.enabled,
    });
    if (autoLocateAttempted.current || (policy.latitude != null && policy.longitude != null)) return;
    autoLocateAttempted.current = true;
    void locateCurrentCoordinates();
  }, [locateCurrentCoordinates, policy]);

  if (isLoading) {
    return <div className="h-72 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" aria-label={t('common.loading')} />;
  }

  if (isError || !policy) {
    return (
      <section className="rounded-xl border border-red-200 bg-red-50 p-5 text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">
        <h2 className="font-semibold">{t('adminLabs.loadPolicyFailed')}</h2>
      </section>
    );
  }

  const handleSave = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const latitude = Number(form.latitude);
    const longitude = Number(form.longitude);
    const radiusMeters = Number(form.radiusMeters);
    const maxAccuracyMeters = Number(form.maxAccuracyMeters);
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
      || !Number.isFinite(longitude) || longitude < -180 || longitude > 180
      || !Number.isInteger(radiusMeters) || radiusMeters <= 0
      || !Number.isInteger(maxAccuracyMeters) || maxAccuracyMeters <= 0) {
      setValidationError(t('adminLabs.policyValidation'));
      return;
    }
    setValidationError('');
    updatePolicy.mutate({ latitude, longitude, radiusMeters, maxAccuracyMeters, enabled: form.enabled });
  };

  const handleAddNetwork = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const value = network.trim();
    if (!value) return;
    addNetwork.mutate(value, { onSuccess: () => setNetwork('') });
  };

  return (
    <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800" aria-labelledby="lab-checkin-policy-title">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <LocateFixed aria-hidden="true" className="h-5 w-5 text-blue-700 dark:text-blue-300" />
            <h2 className="text-lg font-semibold text-slate-950 dark:text-white" id="lab-checkin-policy-title">{t('adminLabs.checkinPolicyTitle')}</h2>
          </div>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ring-1 ${form.enabled ? 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-200 dark:ring-emerald-800' : 'bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700'}`}>
          {form.enabled ? t('adminLabs.policyEnabled') : t('adminLabs.policyDisabled')}
        </span>
      </div>

      <form className="mt-5 space-y-4" onSubmit={handleSave}>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <PolicyField label={t('adminLabs.latitude')} value={form.latitude} min="-90" max="90" step="any" readOnly />
          <PolicyField label={t('adminLabs.longitude')} value={form.longitude} min="-180" max="180" step="any" readOnly />
          <PolicyField label={t('adminLabs.radius')} value={form.radiusMeters} onChange={(value) => setForm((current) => ({ ...current, radiusMeters: value }))} min="1" step="1" />
          <PolicyField label={t('adminLabs.gpsAccuracy')} value={form.maxAccuracyMeters} onChange={(value) => setForm((current) => ({ ...current, maxAccuracyMeters: value }))} min="1" step="1" />
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <button
            className="inline-flex min-h-10 items-center gap-2 rounded-md border border-blue-200 px-3 py-2 text-sm font-semibold text-blue-700 transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-blue-800 dark:text-blue-300 dark:hover:bg-blue-950/40"
            disabled={isLocating || updatePolicy.isPending}
            type="button"
            onClick={() => void locateCurrentCoordinates()}
          >
            {isLocating ? t('adminLabs.gettingLocation') : t('adminLabs.currentLocation')}
          </button>
          {locationError ? <p className="basis-full text-sm text-red-700 dark:text-red-300" role="alert">{locationError}</p> : null}
        </div>
        <label className="flex min-h-11 items-center gap-3 text-sm font-medium text-slate-700 dark:text-slate-200">
          <input
            checked={form.enabled}
            className="h-4 w-4 rounded border-slate-300 text-blue-700 focus:ring-blue-500"
            type="checkbox"
            onChange={(event) => setForm((current) => ({ ...current, enabled: event.target.checked }))}
          />
          {t('adminLabs.allowStudentCheckin')}
        </label>
        {validationError ? <p className="text-sm text-red-700 dark:text-red-300" role="alert">{validationError}</p> : null}
        <button className="inline-flex min-h-11 items-center gap-2 rounded-md bg-blue-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600" disabled={updatePolicy.isPending || isLocating} type="submit">
          <Save aria-hidden="true" className="h-4 w-4" />
          {updatePolicy.isPending ? t('adminLabs.savingPolicy') : t('adminLabs.savePolicy')}
        </button>
      </form>

      <div className="mt-6 border-t border-slate-200 pt-5 dark:border-slate-800">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <Network aria-hidden="true" className="h-5 w-5 text-slate-600 dark:text-slate-300" />
              <h3 className="font-semibold text-slate-950 dark:text-white">{t('adminLabs.allowedNetworks')}</h3>
            </div>
          </div>
          <button className="min-h-11 rounded-md border border-blue-200 px-3 py-2 text-sm font-semibold text-blue-700 transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-blue-800 dark:text-blue-300 dark:hover:bg-blue-950/40" disabled={registerCurrentNetwork.isPending} type="button" onClick={() => registerCurrentNetwork.mutate()}>
            {registerCurrentNetwork.isPending ? t('adminLabs.registeringNetwork') : t('adminLabs.registerNetwork')}
          </button>
        </div>

        <form className="mt-4 flex flex-col gap-2 sm:flex-row" onSubmit={handleAddNetwork}>
          <label className="sr-only" htmlFor={`allowed-network-${labId}`}>{t('adminLabs.allowedNetworkInput')}</label>
          <input
            id={`allowed-network-${labId}`}
            className="min-h-11 min-w-0 flex-1 rounded-md border border-slate-300 bg-white px-3 text-base text-slate-900 placeholder:text-slate-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
            placeholder={t('adminLabs.networkPlaceholder')}
            value={network}
            onChange={(event) => setNetwork(event.target.value)}
          />
          <button className="min-h-11 rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600 dark:bg-slate-700 dark:hover:bg-slate-600" disabled={addNetwork.isPending || !network.trim()} type="submit">
            {t('adminLabs.addNetwork')}
          </button>
        </form>

        {policy.allowedNetworks.length === 0 ? (
          <p className="mt-4 rounded-lg bg-amber-50 px-3 py-3 text-sm text-amber-800 dark:bg-amber-950/40 dark:text-amber-200">{t('adminLabs.noAllowedNetworks')}</p>
        ) : (
          <ul className="mt-4 divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
            {policy.allowedNetworks.map((entry) => (
              <li className="flex flex-wrap items-center justify-between gap-3 px-3 py-3" key={entry.id}>
                <span className="min-w-0 break-all font-mono text-sm text-slate-800 dark:text-slate-200">{entry.network}</span>
                <button className="inline-flex min-h-11 items-center gap-1.5 rounded-md px-2.5 text-sm font-semibold text-red-700 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:cursor-not-allowed disabled:opacity-50 dark:text-red-300 dark:hover:bg-red-950/40" disabled={disableNetwork.isPending} type="button" onClick={() => disableNetwork.mutate(entry.id)}>
                  <Trash2 aria-hidden="true" className="h-3.5 w-3.5" />
                  {t('adminLabs.disableNetwork')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

interface PolicyFieldProps {
  label: string;
  value: string;
  min: string;
  max?: string;
  step: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
}

function PolicyField({ label, value, min, max, step, onChange, readOnly = false }: PolicyFieldProps) {
  return (
    <label className="block text-sm font-medium text-slate-700 dark:text-slate-200">
      {label}
      <input
        className={`mt-1.5 min-h-11 w-full rounded-md border border-slate-300 px-3 text-base text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:text-white ${readOnly ? 'bg-slate-100 dark:bg-slate-800' : 'bg-white dark:bg-slate-950'}`}
        inputMode="decimal"
        max={max}
        min={min}
        readOnly={readOnly}
        step={step}
        type="number"
        value={value}
        onChange={readOnly ? undefined : (event) => onChange?.(event.target.value)}
      />
    </label>
  );
}
