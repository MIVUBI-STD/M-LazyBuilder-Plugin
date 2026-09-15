import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const baseUrl = process.env.LAZYBUILDER_PREVIEW_URL ?? 'http://127.0.0.1:1420';
const outputDir = path.resolve(process.cwd(), 'ui-preview-output');

async function stabilize(page) {
  await page.addStyleTag({
    content: `
      *, *::before, *::after {
        animation-duration: 0s !important;
        animation-delay: 0s !important;
        transition-duration: 0s !important;
        transition-delay: 0s !important;
        caret-color: transparent !important;
      }
    `
  });
  await page.evaluate(async () => {
    if (document.fonts?.ready) await document.fonts.ready;
  });
  await page.waitForTimeout(120);
}

async function capture(page, fileName) {
  await stabilize(page);
  await page.screenshot({
    path: path.join(outputDir, fileName),
    fullPage: false,
    animations: 'disabled'
  });
}

async function open(page, query) {
  await page.goto(`${baseUrl}/${query}`, { waitUntil: 'networkidle' });
  await expect(page.getByText('LazyBuilder', { exact: true }).first()).toBeVisible();
}

test.use({
  viewport: { width: 1440, height: 900 },
  colorScheme: 'dark'
});

test('capture canonical LazyBuilder launcher states', async ({ page }) => {
  await fs.mkdir(outputDir, { recursive: true });

  await open(page, '?preview=library');
  await expect(page.getByRole('heading', { name: 'Servers' })).toBeVisible();
  await expect(page.getByText('Museum Khatulistiwa')).toBeVisible();
  await capture(page, '01-server-library.png');

  await open(page, '?preview=active');
  await expect(page.getByRole('heading', { name: 'MIVUBI Build Server' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
  await capture(page, '02-overview-ready.png');

  await page.getByRole('button', { name: 'Worlds', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Worlds' })).toBeVisible();
  await capture(page, '03-worlds.png');

  await page.evaluate(() => history.replaceState(null, '', '/?preview=active&page=Plugins'));
  await page.getByRole('button', { name: 'Plugins', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Plugins' })).toBeVisible();
  await expect(page.getByText('FastAsyncWorldEdit')).toBeVisible();
  await capture(page, '04-plugins.png');

  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await capture(page, '05-settings.png');

  await page.getByRole('button', { name: 'Client', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Client' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Minecraft Client' })).toBeVisible();
  await capture(page, '06-client-setup.png');

  await open(page, '?preview=setup');
  await expect(page.getByText('Accept the Minecraft EULA')).toBeVisible();
  await capture(page, '07-server-setup-required.png');

  const manifest = {
    generatedAt: new Date().toISOString(),
    source: 'real Svelte launcher UI with deterministic visual-preview runtime',
    viewport: '1440x900',
    proofBoundary: 'Visual/layout proof only. Native Tauri windowing, file dialogs, filesystem/runtime behavior, and OS integration still require Local PC proof.',
    screenshots: [
      '01-server-library.png',
      '02-overview-ready.png',
      '03-worlds.png',
      '04-plugins.png',
      '05-settings.png',
      '06-client-setup.png',
      '07-server-setup-required.png'
    ]
  };
  await fs.writeFile(path.join(outputDir, 'manifest.json'), JSON.stringify(manifest, null, 2), 'utf8');
});
