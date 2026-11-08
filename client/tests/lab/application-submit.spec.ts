import { expect, test, type Page, type Request } from '@playwright/test';
import { readFileSync } from 'node:fs';
import type { ApplicationResponse } from '../../src/modules/application/api';
import type { LabResponse } from '../../src/modules/lab/api';
import type { UserProfileResponse } from '../../src/modules/user/api';

const website = JSON.parse(readFileSync(new URL('../../../contracts/website-demo-content.json', import.meta.url), 'utf8'));
const student: UserProfileResponse = {
  id: 7, fullName: 'CV test applicant', email: 'cv-applicant@example.invalid',
  phone: null, roles: ['STUDENT'], memberships: [],
};
const lab: LabResponse = {
  id: 11, labName: 'CV regression lab', description: null, department: null,
  location: null, capacity: 10, status: 'AVAILABLE', manager: null,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
};

async function openApplication(page: Page, rejectFirst = false) {
  const submissions: Request[] = [];
  const applications: ApplicationResponse[] = [];
  await page.addInitScript(() => {
    localStorage.setItem('access_token', 'isolated-cv-test-token');
    localStorage.setItem('user_role', 'STUDENT');
  });
  await page.route('**/*', route => ['127.0.0.1', 'localhost'].includes(new URL(route.request().url()).hostname)
    ? route.fallback() : route.abort());
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    const ok = (data: unknown, status = 200) => route.fulfill({
      status, json: { code: status, message: 'Test response', data },
    });
    if (pathname === '/api/public/website') return ok({ content: website.content });
    if (pathname === '/api/users/me') return ok(student);
    if (pathname === '/api/labs') return ok([lab]);
    if (pathname === '/api/labs/11') return ok(lab);
    if (pathname === '/api/applications/users/7') return ok({ content: applications, totalElements: applications.length });
    if (pathname === '/api/labs/11/apply' && request.method() === 'POST') {
      submissions.push(request);
      if (rejectFirst && submissions.length === 1) {
        return route.fulfill({ status: 500, json: { code: 500, message: 'CV upload unavailable', data: null } });
      }
      applications.push({
        id: 19, userId: student.id, applicantName: student.fullName, applicantEmail: student.email,
        labId: lab.id, labName: lab.labName, status: 'PENDING',
        createdAt: '2026-01-02T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z',
      });
      return ok(applications[0], 201);
    }
    throw new Error(`Unexpected API request: ${request.method()} ${pathname}`);
  });
  await page.goto('/vi#lab-admission');
  await page.getByRole('button', { name: 'Nộp CV / đăng ký tham gia', exact: true }).click();
  await page.getByRole('button', { name: 'Ứng tuyển', exact: true }).click();
  return { dialog: page.getByRole('dialog', { name: 'Ứng tuyển vào lĩnh vực nghiên cứu', exact: true }), submissions };
}

async function submittedPayload(request: Request) {
  const body = request.postDataBuffer();
  expect(request.headers()['content-type']).toContain('multipart/form-data; boundary=');
  expect(body).not.toBeNull();
  return new Response(new Uint8Array(body!), { headers: { 'content-type': request.headers()['content-type'] } }).formData();
}

test('file-only CV submission sends multipart and updates pending status through the portaled modal', async ({ page }) => {
  const { dialog, submissions } = await openApplication(page);
  await dialog.locator('input[type="file"]').setInputFiles({ name: 'cv.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7 test CV') });
  await dialog.getByRole('button', { name: 'Nộp đơn', exact: true }).click();
  await expect(dialog).toBeHidden();
  await expect(page.locator('.admission-status-list')).toContainText('Đang chờ duyệt');
  expect(submissions).toHaveLength(1);
  const payload = await submittedPayload(submissions[0]);
  expect(payload.has('cvUrl')).toBe(false);
  const file = payload.get('cvFile');
  expect(file).not.toBeNull();
  expect(typeof file).not.toBe('string');
  if (!file || typeof file === 'string') throw new Error('The submitted CV must be a file');
  expect(file.name).toBe('cv.pdf');
  expect(await file.text()).toBe('%PDF-1.7 test CV');
});

test('URL-only CV submission works with Enter and does not require a file', async ({ page }) => {
  const { dialog, submissions } = await openApplication(page);
  await dialog.getByLabel('CV URL', { exact: true }).fill('https://example.invalid/cv.pdf');
  await dialog.getByLabel('CV URL', { exact: true }).press('Enter');
  await expect(dialog).toBeHidden();
  expect(submissions).toHaveLength(1);
  const payload = await submittedPayload(submissions[0]);
  expect(payload.get('cvUrl')).toBe('https://example.invalid/cv.pdf');
  expect(payload.has('cvFile')).toBe(false);
});

test('missing CV and invalid URL cannot send an application', async ({ page }) => {
  const { dialog, submissions } = await openApplication(page);
  const submit = dialog.getByRole('button', { name: 'Nộp đơn', exact: true });
  await expect(submit).toBeDisabled();
  await dialog.getByLabel('CV URL', { exact: true }).fill('invalid-url');
  await submit.click();
  await expect(dialog.getByText('CV URL không hợp lệ', { exact: true })).toBeVisible();
  expect(submissions).toHaveLength(0);
});

test('an API failure keeps the CV form open and allows a successful retry', async ({ page }) => {
  const { dialog, submissions } = await openApplication(page, true);
  await dialog.getByLabel('CV URL', { exact: true }).fill('https://example.invalid/cv.pdf');
  const submit = dialog.getByRole('button', { name: 'Nộp đơn', exact: true });
  await submit.click();
  await expect(dialog.getByText('CV upload unavailable', { exact: true })).toBeVisible();
  await expect(submit).toBeEnabled();
  await expect(dialog.getByLabel('CV URL', { exact: true })).toHaveValue('https://example.invalid/cv.pdf');
  await submit.click();
  await expect(dialog).toBeHidden();
  expect(submissions).toHaveLength(2);
});
