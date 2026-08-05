import { chromium } from 'playwright-core';
import fs from 'node:fs';

const output = [];
const log = (...args) => {
  const line = args.map(value => typeof value === 'string' ? value : JSON.stringify(value)).join(' ');
  output.push(line);
  console.log(line);
};

const browser = await chromium.launch({
  executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome',
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage']
});
const context = await browser.newContext({
  locale: 'en-US',
  userAgent: 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36',
  viewport: { width: 412, height: 915 }
});
const page = await context.newPage();

page.on('request', request => {
  if (request.url().includes('/api/v2/parcels')) {
    log('TRACK_REQUEST', request.method(), request.url(), request.postData() || '');
  }
});
page.on('response', async response => {
  if (response.url().includes('/api/v2/parcels')) {
    let body = '';
    try { body = await response.text(); } catch {}
    log('TRACK_RESPONSE', response.status(), response.url(), body.slice(0, 5000));
  }
});

await page.goto('https://parcelsapp.com/en/carriers/1st', { waitUntil: 'domcontentloaded', timeout: 60000 });
await page.waitForTimeout(5000);
const trackingInput = page.locator('input[name=tracking_id]:visible').first();
await trackingInput.fill('1ST06003441583');
await trackingInput.press('Enter');

await page.waitForURL(/\/en\/tracking\/1ST06003441583/, { timeout: 30000 });
await page.waitForTimeout(3000);
log('AFTER_FIRST_REQUEST_URL', page.url());
log('AFTER_FIRST_REQUEST_BODY', (await page.locator('body').innerText()).slice(0, 1000));
log('COOKIES_AFTER_FIRST_REQUEST', await context.cookies('https://parcelsapp.com'));

log('RELOADING_TRACKING_PAGE');
await page.reload({ waitUntil: 'domcontentloaded', timeout: 60000 });
await page.waitForTimeout(30000);
log('AFTER_RELOAD_URL', page.url());
log('AFTER_RELOAD_BODY', (await page.locator('body').innerText()).slice(0, 5000));
log('COOKIES_AFTER_RELOAD', await context.cookies('https://parcelsapp.com'));

await page.screenshot({ path: '/tmp/parcelsapp-1st.png', fullPage: true });
fs.writeFileSync('/tmp/parcelsapp-1st-capture.txt', output.join('\n'));
await browser.close();
