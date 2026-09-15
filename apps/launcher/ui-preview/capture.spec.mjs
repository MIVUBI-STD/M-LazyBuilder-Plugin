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

async function openServerPage(page, pageName, queryPage = pageName) {
  await open(page, `?preview=active&page=${encodeURIComponent(queryPage)}`);
  if (pageName !== 'Overview') {
    await page.getByRole('button', { name: pageName, exact: true }).click();
  }
  await expect(heading(page, pageName)).toBeVisible();
}

function heading(page, name) {
  return page.getByRole('heading', { name, exact: true });
}

test.use({
  viewport: { width: 1440, height: 900 },
  colorScheme: 'dark'
});

test('capture canonical LazyBuilder launcher states', async ({ page }) => {
  await fs.mkdir(outputDir, { recursive: true });

  await open(page, '?preview=library');
  await expect(heading(page, 'Servers')).toBeVisible();
  await expect(page.getByText('Museum Khatulistiwa', { exact: true })).toBeVisible();
  await capture(page, '01-server-library.png');

  await openServerPage(page, 'Overview');
  await expect(heading(page, 'MIVUBI Build Server')).toBeVisible();
  await capture(page, '02-overview-ready.png');

  await openServerPage(page, 'Worlds');
  await capture(page, '03-worlds.png');

  await openServerPage(page, 'Plugins');
  await expect(page.getByText('FastAsyncWorldEdit', { exact: true })).toBeVisible();
  await capture(page, '04-plugins.png');

  await openServerPage(page, 'Settings');
  await capture(page, '05-settings.png');

  await open(page, '?preview=active&page=Client');
  await page.getByRole('button', { name: 'Client', exact: true }).click();
  await expect(heading(page, 'Client')).toBeVisible();
  await expect(heading(page, 'Minecraft Client')).toBeVisible();
  await capture(page, '06-client-setup.png');

  // Keep the preview runtime offline while showing the setup-required Overview.
  // This prevents the deterministic fixture from presenting an impossible
  // "EULA not accepted + server running" combination.
  await open(page, '?preview=setup&page=Settings');
  await expect(page.getByText('Accept the Minecraft EULA', { exact: true })).toBeVisible();
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
