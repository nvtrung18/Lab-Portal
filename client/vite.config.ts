import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';
import fs from 'node:fs';
import path from 'node:path';

export function validateApiBaseUrl(value: string | undefined, mode: string) {
  const normalized = value?.trim();
  if (mode !== 'production' && !normalized) {
    return;
  }
  if (!normalized) {
    throw new Error('VITE_API_BASE_URL is required for production builds.');
  }

  let parsed: URL;
  try {
    parsed = new URL(normalized);
  } catch {
    throw new Error('VITE_API_BASE_URL must be a valid absolute URL.');
  }
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password) {
    throw new Error('VITE_API_BASE_URL must use http(s) without credentials.');
  }
  if (parsed.pathname !== '/') {
    throw new Error('VITE_API_BASE_URL must be the backend origin only, without /api or another path.');
  }
  if (normalized.endsWith('/')) {
    throw new Error('VITE_API_BASE_URL must not have a trailing slash.');
  }
  if (parsed.search || parsed.hash) {
    throw new Error('VITE_API_BASE_URL must not include a query string or hash.');
  }

  return normalized;
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  validateApiBaseUrl(env.VITE_API_BASE_URL, mode);

  const certDir = path.resolve(process.cwd(), '../.local-certs');
  const certPath = path.join(certDir, 'lab-ip.pem');
  const keyPath = path.join(certDir, 'lab-ip-key.pem');
  const hasLocalCertificate = mode !== 'production'
    && fs.existsSync(certPath)
    && fs.existsSync(keyPath);

  return {
    plugins: [react()],
    server: {
      fs: { allow: [process.cwd(), path.resolve(process.cwd(), '../contracts')] },
      host: '0.0.0.0',
      port: 5173,
      strictPort: true,
      ...(hasLocalCertificate
        ? {
            https: {
              cert: fs.readFileSync(certPath),
              key: fs.readFileSync(keyPath),
            },
          }
        : {}),
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8080',
          xfwd: true,
        },
      },
    },
  };
});
