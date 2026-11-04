import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const fixture = JSON.parse(fs.readFileSync(path.join(process.env.AI_TEST_OUTPUT_DIR!, 'browser-fixture.json'), 'utf8'));
const scenarioFile = process.env.AI_TEST_SCENARIO_FILE!;

test.afterEach(async ({ page }, info) => {
  if (info.status === 'passed') await info.attach('verified-ui', { body: await page.screenshot(), contentType: 'image/png' });
});

async function login(page: Page, actor: 'admin' | 'otherAdmin' | 'manager' = 'admin') {
  await page.goto('/login');
  await page.getByLabel(/Email/i).fill(fixture[actor].email);
  await page.locator('input[type="password"]').fill(fixture.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/);
  await page.goto(actor === 'manager' ? '/app/assistant' : '/admin/assistant');
  await expect(page.locator('#assistant-input')).toBeVisible();
}

async function scenario(page: Page, value: object) {
  const id = 'browser-' + crypto.randomUUID();
  const values = JSON.parse(fs.readFileSync(scenarioFile, 'utf8'));
  values[id] = value;
  fs.writeFileSync(scenarioFile, JSON.stringify(values));
  await page.unroute('**/api/ai/chat');
  await page.route('**/api/ai/chat', route => route.continue({ headers: { ...route.request().headers(), 'X-Request-Id': id } }));
}

async function ask(page: Page, input: string, timeout = 30_000) {
  await page.locator('#assistant-input').fill(input);
  const response = page.waitForResponse(r => r.url().endsWith('/api/ai/chat') && r.request().method() === 'POST', { timeout });
  await page.locator('#assistant-input').press('Enter');
  const result = await response;
  expect(result.status()).toBe(200);
  const data = (await result.json()).data;
  await test.info().attach('actual-ai-response', { body: JSON.stringify(data), contentType: 'application/json' });
  return data;
}

test('RUNTIME K22: browser Spring MySQL and Kaggle return the same authorized counts', async ({ page }) => {
  test.skip(process.env.LAB_PORTAL_LIVE_AI_IT !== 'true', 'Requires verified real Kaggle runtime evidence');
  test.setTimeout(150_000);
  await login(page);
  // No route override or scripted generation in this test.
  const response = await ask(page, 'Hiện hệ thống có bao nhiêu người dùng hoạt động và đã đăng ký?', 110_000);
  expect(response.type).toBe('ANSWER');
  expect(response.assistantKey).toBe('ADMIN_ASSISTANT');
  expect(response.answer).toContain('Người dùng hoạt động: ' + fixture.counts.active);
  expect(response.answer).toContain('Người dùng đã đăng ký: ' + fixture.counts.registered);
  await expect(page.getByText(response.answer, { exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: 'Hiện hệ thống có bao nhiêu người dùng hoạt động và đã đăng ký?', exact: true }).click();
  await expect(page.getByText(response.answer, { exact: true })).toBeVisible();
});

test('H02 H03: reload and reopen retain the grounded response', async ({ page }) => {
  await login(page);
  await scenario(page, { toolId: 'admin.system.summary' });
  const response = await ask(page, 'Tổng quan người dùng HYBRID_REOPEN');
  expect(response.type).toBe('ANSWER');
  await expect(page.getByText(response.answer, { exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: 'Tổng quan người dùng HYBRID_REOPEN', exact: true }).click();
  await expect(page.getByText(response.answer, { exact: true })).toBeVisible();
});

test('H07: deleting an owned conversation removes it from the list', async ({ page }) => {
  await login(page);
  await scenario(page, { toolId: 'admin.system.summary' });
  await ask(page, 'HYBRID_DELETE_CONVERSATION');
  await page.getByRole('button', { name: /Xóa.*HYBRID_DELETE_CONVERSATION/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: /Xóa/ }).click();
  await expect(page.getByRole('button', { name: 'HYBRID_DELETE_CONVERSATION', exact: true })).toHaveCount(0);
});

test('H10: conversation switching is disabled while a request is in flight', async ({ page }) => {
  await login(page);
  await scenario(page, { toolId: 'admin.system.summary' });
  await ask(page, 'HYBRID_EXISTING_CONVERSATION');
  await page.getByRole('button', { name: 'Cuộc trò chuyện mới', exact: true }).click();
  await page.route('**/api/ai/chat', async route => { await new Promise(r => setTimeout(r, 700)); await route.fallback(); });
  await page.locator('#assistant-input').fill('HYBRID_IN_FLIGHT');
  await page.locator('#assistant-input').press('Enter');
  await expect(page.getByRole('button', { name: 'Cuộc trò chuyện mới', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'HYBRID_EXISTING_CONVERSATION', exact: true })).toBeDisabled();
  await expect(page.locator('#assistant-input')).toBeEnabled();
});

test('H11: a different authenticated actor cannot see the first actor conversation', async ({ page, browser }) => {
  await login(page);
  await scenario(page, { toolId: 'admin.system.summary' });
  const response = await ask(page, 'HYBRID_PRIVATE_CONVERSATION');
  const otherContext = await browser.newContext({ baseURL: 'http://127.0.0.1:4173' });
  const otherPage = await otherContext.newPage();
  await login(otherPage, 'otherAdmin');
  await expect(otherPage.getByRole('button', { name: /HYBRID_PRIVATE_CONVERSATION/ })).toHaveCount(0);
  const token = await otherPage.evaluate(() => localStorage.getItem('access_token'));
  const denied = await otherPage.request.get('/api/ai/conversations/' + response.conversationId,
    { headers: { Authorization: 'Bearer ' + token } });
  expect([403, 404]).toContain(denied.status());
  await otherContext.close();
  // Desktop and icon controls share the same logout handler and accessible name.
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).first().click();
  await expect(page).toHaveURL(/\/vi/);
  await login(page, 'otherAdmin');
  await expect(page.getByText('HYBRID_PRIVATE_CONVERSATION', { exact: true })).toHaveCount(0);
});

test('H10 SEC14: two tabs keep concurrent replies in their own conversations', async ({ page }) => {
  await login(page);
  await scenario(page, { toolId: 'admin.system.summary' });
  const a = await ask(page, 'HYBRID_TAB_A');
  const other = await page.context().newPage();
  await other.goto('/admin/assistant');
  await scenario(other, { toolId: 'admin.system.summary' });
  const b = await ask(other, 'HYBRID_TAB_B');
  expect(a.conversationId).not.toBe(b.conversationId);
  await page.route('**/api/ai/chat', async route => { await new Promise(r => setTimeout(r, 700)); await route.fallback(); });
  const pendingA = ask(page, 'HYBRID_A_FOLLOW_UP');
  const doneB = await ask(other, 'HYBRID_B_FOLLOW_UP');
  const doneA = await pendingA;
  expect(doneA.conversationId).toBe(a.conversationId);
  expect(doneB.conversationId).toBe(b.conversationId);
  await expect(other.getByText('HYBRID_A_FOLLOW_UP', { exact: true })).toHaveCount(0);
  await expect(page.getByText('HYBRID_B_FOLLOW_UP', { exact: true })).toHaveCount(0);
  await other.close();
});

for (const decision of ['confirm', 'cancel'] as const) {
  test(`H09 H12: preview ${decision} survives history reopen without actionable old controls`, async ({ page }) => {
    await login(page, 'manager');
    const prompt = decision === 'confirm' ? fixture.shiftPrompt : fixture.cancelShiftPrompt;
    const date = prompt.match(/(\d{2})\/(\d{2})\/(\d{4})/)!;
    await scenario(page, { toolId: 'lab.shift.create.draft', resourceId: fixture.lab.id, intent: 'CREATE_SHIFT',
      generation: { kind: 'LAB_SHIFT_CREATE_INTERPRETATION', labRef: fixture.lab.id, mode: 'NEW',
        requestedLabName: fixture.lab.name, dateMention: { day: +date[1], month: +date[2], year: +date[3] },
        timeMentions: [{ role: 'START', hour: 15, minute: 0 }, { role: 'END', hour: 17, minute: 0 }],
        capacity: 5, timeZone: null, clearFields: [], requiresHumanReview: true } });
    const response = await ask(page, prompt);
    expect(response.type).toBe('ACTION_PREVIEW');
    await page.reload();
    await page.getByRole('button', { name: prompt.slice(0, 100), exact: true }).click();
    await expect(page.getByRole('button', { name: 'Xác nhận tạo ca', exact: true })).toBeVisible();
    const resolved = page.waitForResponse(r => r.url().endsWith('/' + decision) && r.request().method() === 'POST');
    await page.getByRole('button', { name: decision === 'confirm' ? 'Xác nhận tạo ca' : 'Hủy', exact: true }).click();
    const resolution = await resolved;
    expect(resolution.status()).toBe(200);
    const result = (await resolution.json()).data;
    expect(result.status).toBe(decision === 'confirm' ? 'EXECUTED' : 'CANCELLED');
    await test.info().attach('actual-action-result', { body: JSON.stringify(result), contentType: 'application/json' });
    await expect(page.getByText(decision === 'confirm'
      ? `Đã tạo ca lĩnh vực nghiên cứu thành công (mã ca #${result.targetId}).`
      : 'Đã hủy bản xem trước. Không có dữ liệu nào được ghi.', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Xác nhận tạo ca', exact: true })).toHaveCount(0);
    await page.reload();
    const history = page.waitForResponse(r => r.url().includes('/api/ai/conversations/' + response.conversationId) && r.request().method() === 'GET');
    await page.getByRole('button', { name: prompt.slice(0, 100), exact: true }).click();
    const saved = (await (await history).json()).data.messages;
    const outcome = saved.find((message: any) => message.response?.type === 'ACTION_RESULT').response;
    expect(outcome.actionResult.status).toBe(result.status);
    await expect(page.getByText(outcome.answer, { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Xác nhận tạo ca', exact: true })).toHaveCount(0);
  });
}

for (const fault of ['connectionfailed', 'timedout']) {
test(`H14: ${fault} remains visible and a later retry is persisted once`, async ({ page }) => {
  await login(page);
  const failedInput = 'HYBRID_' + fault + '_FAILURE';
  const retryInput = 'HYBRID_' + fault + '_RETRY';
  await page.route('**/api/ai/chat', route => route.abort(fault));
  await page.locator('#assistant-input').fill(failedInput);
  await page.locator('#assistant-input').press('Enter');
  await expect(page.getByText(/Không thể kết nối|kết nối|connection/i).last()).toBeVisible();
  await scenario(page, { toolId: 'admin.system.summary' });
  const response = await ask(page, retryInput);
  expect(response.type).toBe('ANSWER');
  await expect(page.getByText(retryInput, { exact: true })).toHaveCount(1);
  const token = await page.evaluate(() => localStorage.getItem('access_token'));
  const history = await page.request.get('/api/ai/conversations/' + response.conversationId,
    { headers: { Authorization: 'Bearer ' + token } });
  expect(history.status()).toBe(200);
  const messages = (await history.json()).data.messages;
  expect(messages.filter((message: any) => message.role === 'USER' && message.content === retryInput)).toHaveLength(1);
  expect(messages.some((message: any) => message.content === failedInput)).toBe(false);
});
}

for (const [decision, type] of [['CLARIFICATION', 'CLARIFICATION_REQUIRED'], ['REFUSAL', 'REFUSED']] as const) {
  test(`H12: ${type} is rendered without action controls`, async ({ page }) => {
    await login(page);
    await scenario(page, { decision, intent: 'READ', message: 'HYBRID_' + type });
    const response = await ask(page, 'Yêu cầu cần xác minh');
    expect(response.type).toBe(type);
    await expect(page.getByText('HYBRID_' + type, { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Xác nhận tạo ca', exact: true })).toHaveCount(0);
  });
}

test('SEC11 H13: long Unicode and script markup remain inert on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  const answer = '## Dữ liệu kiểm thử\n\n<script>window.HYBRID_XSS=true</script> javascript:alert(1) ' + 'Dữ liệu kiểm thử '.repeat(100);
  await scenario(page, { decision: 'ANSWER', intent: 'CHAT', message: answer });
  expect((await ask(page, 'Hiển thị văn bản kiểm thử')).type).toBe('ANSWER');
  await expect(page.getByText(answer, { exact: true })).toBeVisible();
  expect(await page.evaluate(() => (window as any).HYBRID_XSS)).toBeUndefined();
  expect(await page.locator('a[href^="javascript:"]').count()).toBe(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await scenario(page, { toolId: 'admin.account.action.draft', resourceId: fixture.target.id, intent: 'OTHER_DRAFT',
    generation: { kind: 'ADMIN_ACCOUNT_DRAFT', subject: String(fixture.target.id),
      actions: ['Kiểm tra trước khi thay đổi. '.repeat(25), 'Chờ người có quyền xác minh. '.repeat(25)], requiresHumanReview: true } });
  const draft = await ask(page, 'Soạn đề xuất cho tài khoản ' + fixture.target.id + ', chỉ tư vấn.');
  expect(draft.type).toBe('ANSWER');
  const json = JSON.parse(draft.answer);
  expect(json.subject).toBe(String(fixture.target.id));
  expect(json.requiresHumanReview).toBe(true);
  expect(draft.answer).not.toContain('hybrid-test-token');
  expect(draft.answer).not.toContain('Traceback (most recent call last)');
  // Reload the persisted turn before scrolling; optimistic turns are replaced during history hydration.
  await page.reload();
  await page.getByRole('button', { name: 'Hiển thị văn bản kiểm thử', exact: true }).click();
  await expect(page.getByText(draft.answer, { exact: true })).toBeVisible();
  await page.getByText(draft.answer, { exact: true }).scrollIntoViewIfNeeded();
  await expect(page.getByText(draft.answer, { exact: true })).toBeInViewport();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await expect(page.getByRole('button', { name: 'Xác nhận tạo ca', exact: true })).toHaveCount(0);
});

test('SEC11: authorized RAG markup stays inert after rendering and history reload', async ({ page }) => {
  await login(page);
  await scenario(page, { toolId: 'admin.system.summary' });
  const response = await ask(page, fixture.rag.canary);
  expect(response.type).toBe('ANSWER');
  expect(response.citations.some((citation: { documentId: number }) => citation.documentId === fixture.rag.documentId)).toBe(true);
  expect(response.answer).toContain(fixture.rag.markup.replaceAll('<', '&lt;').replaceAll('>', '&gt;'));
  for (const reload of [false, true]) {
    if (reload) {
      await page.reload();
      await page.getByRole('button', { name: fixture.rag.canary, exact: true }).click();
    }
    await expect(page.getByText(response.answer, { exact: true })).toBeVisible();
    expect(await page.evaluate(() => (window as any).HYBRID_RAG_XSS)).toBeUndefined();
    await expect(page.locator('img[onerror], script:has-text("HYBRID_RAG_XSS"), a[href^="javascript:"], a[href^="data:text/html"]')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Xác nhận tạo ca', exact: true })).toHaveCount(0);
  }
});
