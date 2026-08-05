import { chromium } from 'playwright-core';
import fs from 'node:fs';

const SAMPLE = '1ST06003441583';
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

async function exercise(name, startUrl) {
  log('===== SITE', name, startUrl, '=====');
  const context = await browser.newContext({
    locale: 'en-US',
    userAgent: 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36',
    viewport: { width: 412, height: 915 }
  });
  const page = await context.newPage();

  page.on('request', request => {
    const url = request.url();
    if (request.method() !== 'GET' || /track|parcel|shipment|courier|api|query|search/i.test(url)) {
      log(name, 'REQUEST', request.method(), url, request.postData() || '');
    }
  });
  page.on('response', async response => {
    const url = response.url();
    const type = response.headers()['content-type'] || '';
    if (response.request().method() !== 'GET' || /track|parcel|shipment|courier|api|query|search/i.test(url)) {
      let body = '';
      if (/json|text|html|javascript/.test(type)) {
        try { body = (await response.text()).slice(0, 8000); } catch {}
      }
      log(name, 'RESPONSE', response.status(), type, url, body);
    }
  });

  try {
    await page.goto(startUrl, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForTimeout(6000);
    log(name, 'TITLE', await page.title());
    log(name, 'URL', page.url());

    const inputs = await page.locator('input, textarea').evaluateAll(elements => elements.map((element, index) => ({
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
    log(name, 'INPUTS', inputs);

    let trackingInput = null;
    const visible = page.locator('input:visible, textarea:visible');
    for (let index = 0; index < await visible.count(); index++) {
      const candidate = visible.nth(index);
      const metadata = await candidate.evaluate(element => `${element.tagName} ${element.type} ${element.name} ${element.id} ${element.placeholder} ${element.className}`.toLowerCase());
      if (/track|parcel|shipment|number|waybill|barcode|order|search/.test(metadata) && !/email|postal|zip|country|phone/.test(metadata)) {
        trackingInput = candidate;
        break;
      }
    }
    if (!trackingInput && await visible.count()) trackingInput = visible.first();

    if (!trackingInput) {
      log(name, 'NO_TRACKING_INPUT');
      log(name, 'BODY', (await page.locator('body').innerText()).slice(0, 5000));
      await context.close();
      return;
    }

    await trackingInput.fill(SAMPLE);
    log(name, 'FILLED');

    const form = trackingInput.locator('xpath=ancestor::form[1]');
    let submitted = false;
    if (await form.count()) {
      const button = form.locator('button:visible, input[type=submit]:visible').first();
      if (await button.count()) {
        await button.click();
        submitted = true;
      }
    }
    if (!submitted) {
      const buttons = page.locator('button:visible, input[type=submit]:visible');
      for (let index = 0; index < await buttons.count(); index++) {
        const candidate = buttons.nth(index);
        const text = `${await candidate.innerText().catch(() => '')} ${await candidate.getAttribute('value') || ''}`.toLowerCase();
        if (/track|search|find|submit/.test(text)) {
          await candidate.click();
          submitted = true;
          break;
        }
      }
    }
    if (!submitted) await trackingInput.press('Enter');

    await page.waitForTimeout(45000);
    log(name, 'AFTER_URL', page.url());
    log(name, 'AFTER_BODY', (await page.locator('body').innerText()).slice(0, 10000));
    await page.screenshot({ path: `/tmp/${name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.png`, fullPage: true });
  } catch (error) {
    log(name, 'ERROR', error.stack || String(error));
  } finally {
    await context.close();
  }
}

await exercise('TrackGlobal', 'https://track.global/en/courier/1st');
await exercise('Packy', 'https://packyapp.com/en/carriers/1st');
fs.writeFileSync('/tmp/1st-aggregators-capture.txt', output.join('\n'));
await browser.close();
