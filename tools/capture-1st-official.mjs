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
const context = await browser.newContext({ locale: 'en-US', viewport: { width: 1280, height: 900 } });
const page = await context.newPage();

page.on('request', request => {
  const url = request.url();
  if (request.method() !== 'GET' || /track|trace|query|search|order|waybill|shipment|logistics|erp/i.test(url)) {
    log('REQUEST', request.method(), url, request.postData() || '');
  }
});
page.on('response', async response => {
  const url = response.url();
  if (response.request().method() !== 'GET' || /track|trace|query|search|order|waybill|shipment|logistics|erp/i.test(url)) {
    let body = '';
    const type = response.headers()['content-type'] || '';
    if (/json|text|html|javascript/.test(type)) {
      try { body = (await response.text()).slice(0, 5000); } catch {}
    }
    log('RESPONSE', response.status(), type, url, body);
  }
});

await page.goto('https://1st56.com/group/index/', { waitUntil: 'networkidle', timeout: 90000 });
await page.waitForTimeout(3000);
log('FINAL_URL', page.url());
log('TITLE', await page.title());

const inputs = await page.locator('input, textarea, select').evaluateAll(elements => elements.map((element, index) => ({
  index,
  tag: element.tagName,
  type: element.type,
  name: element.name,
  id: element.id,
  placeholder: element.placeholder,
  value: element.value,
  visible: Boolean(element.offsetWidth || element.offsetHeight || element.getClientRects().length),
  className: element.className,
  dataset: { ...element.dataset }
})));
log('INPUTS', inputs);

const forms = await page.locator('form').evaluateAll(elements => elements.map((element, index) => ({
  index,
  action: element.action,
  method: element.method,
  id: element.id,
  className: element.className,
  html: element.outerHTML.slice(0, 5000)
})));
log('FORMS', forms);

const links = await page.locator('a').evaluateAll(elements => elements.map(element => ({
  text: (element.innerText || '').trim(),
  href: element.href,
  className: element.className
})).filter(item => /track|trace|query|search|order|waybill|shipment|logistics|erp|查询|查件|轨迹/i.test(item.text + ' ' + item.href)));
log('RELEVANT_LINKS', links);

const scripts = await page.locator('script[src]').evaluateAll(elements => elements.map(element => element.src));
log('SCRIPTS', scripts);

const candidates = page.locator('input:visible');
let trackingInput = null;
for (let index = 0; index < await candidates.count(); index++) {
  const candidate = candidates.nth(index);
  const data = await candidate.evaluate(element => `${element.type} ${element.name} ${element.id} ${element.placeholder} ${element.className}`.toLowerCase());
  if (/track|trace|query|search|order|waybill|shipment|物流|查询|单号/.test(data)) {
    trackingInput = candidate;
    break;
  }
}

if (trackingInput) {
  log('FOUND_TRACKING_INPUT');
  await trackingInput.fill('1ST06003441583');
  const form = trackingInput.locator('xpath=ancestor::form[1]');
  if (await form.count()) {
    const submit = form.locator('button[type=submit], input[type=submit], button').first();
    if (await submit.count()) await submit.click();
    else await trackingInput.press('Enter');
  } else {
    await trackingInput.press('Enter');
  }
  await page.waitForTimeout(30000);
  log('AFTER_SUBMIT_URL', page.url());
  log('AFTER_SUBMIT_BODY', (await page.locator('body').innerText()).slice(0, 5000));
} else {
  log('NO_TRACKING_INPUT');
  log('BODY', (await page.locator('body').innerText()).slice(0, 5000));
}

await page.screenshot({ path: '/tmp/1st-official.png', fullPage: true });
fs.writeFileSync('/tmp/1st-official-capture.txt', output.join('\n'));
await browser.close();
