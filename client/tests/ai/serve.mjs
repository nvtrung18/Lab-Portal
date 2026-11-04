import { createServer } from 'vite';
import process from 'node:process';
import { URL } from 'node:url';

const backend = new URL(process.env.AI_TEST_BACKEND_URL);
if (!['localhost', '127.0.0.1'].includes(backend.hostname)) throw new Error('Only the disposable local Spring server is allowed');
process.env.VITE_API_BASE_URL = '';
process.env.VITE_DEFAULT_LOCALE = 'vi';
const server = await createServer({ mode: 'ai-e2e', server: {
  host: '127.0.0.1', port: 4173, strictPort: true, https: false,
  proxy: { '/api': { target: backend.origin } },
}});
await server.listen();
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await server.close(); process.exit(0); });
