// Local fault injection only. Shapes follow app/models/contracts.py and app/security.py.
const http = require('http');
let aiScenario = 'success';
let aiAttempts = 0;
let aiHeadersValid = true;
function send(res, status, body, type = 'application/json') {
  res.writeHead(status, { 'Content-Type': type });
  res.end(type === 'application/json' ? JSON.stringify(body) : body);
}
http.createServer((req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname;
  if (req.method === 'POST' && path === '/__test/scenario') {
    const next = req.body?.scenario;
    if (!['success', '401', '422', '429', '502', '503', '504', '504-retryable', '524', 'html', 'reset', 'timeout'].includes(next)) {
      return send(res, 400, { error: 'Unknown test scenario' });
    }
    aiScenario = next; aiAttempts = 0; aiHeadersValid = true;
    return send(res, 200, { scenario: aiScenario });
  }
  if (req.method === 'GET' && path === '/__test/metrics') {
    return send(res, 200, { attempts: aiAttempts, headersValid: aiHeadersValid });
  }
  if (req.method !== 'POST' || path !== '/v1/assistants/chat') {
    return send(res, 501, { error: 'This test double does not model this endpoint' });
  }
  aiAttempts++;
  aiHeadersValid = aiHeadersValid
    && req.headers['x-internal-service-token'] === 'simulation-only-token'
    && req.headers['x-request-id'] === 'simulation-request'
    && req.body?.assistantKey === 'LAB_ASSISTANT';
  if (aiScenario === 'reset') return req.socket.destroy();
  if (aiScenario === 'html') return send(res, 530, '<html>Cloudflare test fault</html>', 'text/html');
  if (aiScenario === '502' || aiScenario === '524') {
    return send(res, Number(aiScenario), '<html>Cloudflare synthetic gateway fault</html>', 'text/html');
  }
  const success = () => send(res, 200, {
    assistantKey: 'LAB_ASSISTANT', answer: 'Authorized fixture', promptTokens: 0,
    completionTokens: 0, metadata: { grounding: 'AUTHORIZED_SNAPSHOT' },
  });
  if (aiScenario === 'timeout') return setTimeout(success, 1200);
  if (aiScenario === 'success') return success();
  return send(res, aiScenario === '504-retryable' ? 504 : Number(aiScenario), {
    errorCode: aiScenario.startsWith('504') ? 'AI_REQUEST_TIMEOUT' : 'SIMULATED_ERROR',
    message: 'Synthetic fault', retryable: aiScenario === '503' || aiScenario === '504-retryable', requestId: 'simulation-request',
  });
}).listen(process.env.PORT || 4501, '127.0.0.1');
