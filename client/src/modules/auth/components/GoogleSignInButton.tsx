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
  text?: 'signin_with' | 'signup_with';
}

const SCRIPT_ID = 'google-identity-services';
const SCRIPT_URL = 'https://accounts.google.com/gsi/client';

export function GoogleSignInButton({ disabled = false, onCredential, text = 'signin_with' }: GoogleSignInButtonProps) {
  const { locale, t } = useI18n();
  const containerRef = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onCredential);
  const renderedWidthRef = useRef(0);
  const [loadFailed, setLoadFailed] = useState(false);
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();

  callbackRef.current = onCredential;

  useEffect(() => {
    if (!clientId) return;

    containerRef.current?.replaceChildren();
    renderedWidthRef.current = 0;

    const render = () => {
      if (!containerRef.current || !window.google) return;
      const width = Math.min(400, Math.floor(containerRef.current.clientWidth || 400));
      if (renderedWidthRef.current === width && containerRef.current.childElementCount > 0) return;
      containerRef.current.replaceChildren();
      renderedWidthRef.current = width;
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: (response) => callbackRef.current(response.credential),
      });
      window.google.accounts.id.renderButton(containerRef.current, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        text,
        shape: 'pill',
        locale,
        width,
      });
    };

    const resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(render);
    if (containerRef.current) resizeObserver?.observe(containerRef.current);

    const onLoad = () => {
      setLoadFailed(false);
      render();
    };
    const onError = () => setLoadFailed(true);
    const scriptUrl = `${SCRIPT_URL}?hl=${locale}`;
    let script = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
    if (script?.src !== scriptUrl) {
      script?.remove();
      const newScript = document.createElement('script');
      newScript.id = SCRIPT_ID;
      newScript.src = scriptUrl;
      newScript.async = true;
      newScript.defer = true;
      newScript.dataset.gsiLoaded = 'false';
      newScript.addEventListener('load', () => {
        newScript.dataset.gsiLoaded = 'true';
      }, { once: true });
      newScript.addEventListener('error', () => newScript.remove(), { once: true });
      script = newScript;
    } else if (script.dataset.gsiLoaded !== 'false' && window.google) {
      setLoadFailed(false);
      render();
      return () => resizeObserver?.disconnect();
    }
    script.addEventListener('load', onLoad);
    script.addEventListener('error', onError);
    if (!script.isConnected) document.head.appendChild(script);
    return () => {
      resizeObserver?.disconnect();
      script.removeEventListener('load', onLoad);
      script.removeEventListener('error', onError);
    };
  }, [clientId, locale, text]);

  if (!clientId) {
    return (
      <p className="text-center text-xs text-slate-500" role="status">
        {t('auth.googleNotConfigured')}
      </p>
    );
  }

  return (
    <div className="w-full">
      {loadFailed ? (
        <p className="text-center text-xs text-slate-500" role="status">{t('auth.googleLoadFailed')}</p>
      ) : null}
      <div
        aria-busy={disabled}
        className={[
          'mx-auto w-full max-w-[400px] text-center [&>div]:mx-auto',
          loadFailed ? 'hidden' : '',
          disabled ? 'pointer-events-none opacity-60' : '',
        ].join(' ')}
        ref={containerRef}
      />
    </div>
  );
}
