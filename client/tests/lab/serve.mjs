import { createServer } from 'vite';
import process from 'node:process';

process.env.VITE_API_BASE_URL = '';
process.env.VITE_DEFAULT_LOCALE = 'vi';
const server = await createServer({ mode: 'test', server: {
  host: '127.0.0.1', port: 4174, strictPort: true, https: false,
  // Tests intercept every API request; an unexpected request must not reach a real backend.
  proxy: { '/api': { target: 'http://127.0.0.1:1' } },
} });
await server.listen();
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await server.close(); process.exit(0); });
