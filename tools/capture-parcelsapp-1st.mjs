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
  if (request.method() === 'POST' || request.url().includes('/api/v2/parcels')) {
    log('REQUEST', request.method(), request.url(), request.postData() || '');
  }
});
page.on('response', async response => {
  if (response.request().method() === 'POST' || response.url().includes('/api/v2/parcels')) {
    let body = '';
    try { body = await response.text(); } catch {}
    log('RESPONSE', response.status(), response.url(), body.slice(0, 2000));
  }
});
page.on('console', message => log('BROWSER_CONSOLE', message.type(), message.text()));

await page.goto('https://parcelsapp.com/en/carriers/1st', { waitUntil: 'domcontentloaded', timeout: 60000 });
await page.waitForTimeout(5000);

const inputs = await page.locator('input').evaluateAll(elements => elements.map((element, index) => ({
  index,
  type: element.type,
  name: element.name,
  id: element.id,
  placeholder: element.placeholder,
  value: element.value,
  className: element.className,
  visible: Boolean(element.offsetWidth || element.offsetHeight || element.getClientRects().length),
  dataset: { ...element.dataset }
})));
log('INPUTS', inputs);

const forms = await page.locator('form').evaluateAll(elements => elements.map((element, index) => ({
  index,
  action: element.action,
  method: element.method,
  className: element.className,
  html: element.outerHTML.slice(0, 2500)
})));
log('FORMS', forms);

const buttons = await page.locator('button, input[type=submit]').evaluateAll(elements => elements.map((element, index) => ({
  index,
  text: (element.innerText || element.value || '').trim(),
  type: element.type,
  id: element.id,
  className: element.className,
  visible: Boolean(element.offsetWidth || element.offsetHeight || element.getClientRects().length),
  dataset: { ...element.dataset }
})));
log('BUTTONS', buttons);

const trackingInputs = page.locator('input').filter({ visible: true });
let trackingInput = null;
for (let index = 0; index < await trackingInputs.count(); index++) {
  const candidate = trackingInputs.nth(index);
  const meta = await candidate.evaluate(element => ({
    type: element.type,
    placeholder: element.placeholder.toLowerCase(),
    name: element.name.toLowerCase(),
    id: element.id.toLowerCase()
  }));
  if (['text', 'search', ''].includes(meta.type) &&
      !meta.placeholder.includes('search') &&
      !meta.name.includes('carrier') &&
      !meta.id.includes('carrier') &&
      !meta.name.includes('country') &&
      !meta.id.includes('country')) {
    trackingInput = candidate;
    break;
  }
}
if (!trackingInput) throw new Error('No visible tracking input found');

await trackingInput.fill('1ST06003441583');
log('FILLED_TRACKING_INPUT');

const likelyButtons = page.locator('button:visible, input[type=submit]:visible');
let submitted = false;
for (let index = 0; index < await likelyButtons.count(); index++) {
  const candidate = likelyButtons.nth(index);
  const text = ((await candidate.innerText().catch(() => '')) || (await candidate.getAttribute('value')) || '').trim().toLowerCase();
  if (text.includes('track') || text.includes('search')) {
    log('CLICKING_BUTTON', text);
    await candidate.click();
    submitted = true;
    break;
  }
}
if (!submitted) {
  log('PRESSING_ENTER');
  await trackingInput.press('Enter');
}

await page.waitForTimeout(30000);
log('FINAL_URL', page.url());
log('FINAL_BODY', (await page.locator('body').innerText()).slice(0, 5000));
await page.screenshot({ path: '/tmp/parcelsapp-1st.png', fullPage: true });
fs.writeFileSync('/tmp/parcelsapp-1st-capture.txt', output.join('\n'));
await browser.close();
