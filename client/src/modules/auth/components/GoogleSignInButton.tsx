import { useEffect, useRef, useState } from 'react';

import { useI18n } from '../../../shared/i18n';

interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleAccountsId {
  initialize(config: { client_id: string; callback: (response: GoogleCredentialResponse) => void }): void;
  renderButton(element: HTMLElement, options: Record<string, string | number>): void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleAccountsId } };
  }
}

interface GoogleSignInButtonProps {
  disabled?: boolean;
  onCredential: (credential: string) => void;
}

const SCRIPT_ID = 'google-identity-services';
const SCRIPT_URL = 'https://accounts.google.com/gsi/client';

export function GoogleSignInButton({ disabled = false, onCredential }: GoogleSignInButtonProps) {
  const { locale, t } = useI18n();
  const containerRef = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onCredential);
  const [loadFailed, setLoadFailed] = useState(false);
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();

  callbackRef.current = onCredential;

  useEffect(() => {
    if (!clientId) return;

    const render = () => {
      if (!containerRef.current || !window.google) return;
      containerRef.current.replaceChildren();
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: (response) => callbackRef.current(response.credential),
      });
      window.google.accounts.id.renderButton(containerRef.current, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        text: 'continue_with',
        shape: 'pill',
        locale,
        width: Math.min(400, Math.floor(containerRef.current.clientWidth || 400)),
      });
    };

    if (window.google) {
      render();
      return;
    }

    let script = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
    if (!script) {
      script = document.createElement('script');
      script.id = SCRIPT_ID;
      script.src = SCRIPT_URL;
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
    script.addEventListener('load', render);
    script.addEventListener('error', () => setLoadFailed(true), { once: true });
    return () => script?.removeEventListener('load', render);
  }, [clientId, locale]);

  if (!clientId || loadFailed) {
    return (
      <p className="text-center text-xs text-slate-500" role="status">
        {loadFailed ? t('auth.googleLoadFailed') : t('auth.googleNotConfigured')}
      </p>
    );
  }

  return (
    <div className="flex w-full justify-center">
      <div
        aria-busy={disabled}
        className={[
          'w-full max-w-[400px] text-center [&>div]:mx-auto',
          disabled ? 'pointer-events-none opacity-60' : '',
        ].join(' ')}
        ref={containerRef}
      />
    </div>
  );
}
