import { useEffect, useRef, useState } from 'react';

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
        shape: 'rectangular',
        locale: 'vi',
        width: Math.min(360, Math.floor(containerRef.current.clientWidth || 360)),
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
  }, [clientId]);

  if (!clientId || loadFailed) {
    return (
      <p className="text-center text-xs text-slate-500" role="status">
        {loadFailed ? 'Không thể tải Google Sign-In.' : 'Google Sign-In chưa được cấu hình.'}
      </p>
    );
  }

  return (
    <div
      aria-busy={disabled}
      className={disabled ? 'pointer-events-none flex w-full justify-center opacity-60' : 'flex w-full justify-center'}
      ref={containerRef}
    />
  );
}
