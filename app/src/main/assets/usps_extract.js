(() => {
  const compact = value => String(value || '').replace(/\s+/g, ' ').trim();
  const structured = value => String(value || '')
    .split(/\r?\n/)
    .map(line => compact(line))
    .filter(Boolean)
    .join('\n');
  const texts = selector => Array.from(document.querySelectorAll(selector))
    .map(node => structured(node.innerText || node.textContent || ''))
    .filter(Boolean)
    .slice(0, 150);

  const statuses = texts('.tb-status');
  const steps = texts('.tb-step');
  const details = texts('.tb-status-detail');
  const banners = texts('.latest-update-banner-wrapper, .update-banner-wrapper, .banner-content');
  const title = compact(document.title).slice(0, 200);
  const bodyPrefix = compact(document.body ? document.body.innerText : '').slice(0, 1500);
  const challenge = /access denied|please verify|are you a robot|captcha|request blocked|just a moment/i
    .test(`${title}\n${bodyPrefix}`);

  return JSON.stringify({
    version: 1,
    ready: !challenge && Boolean(statuses.length || steps.length || details.length || banners.length),
    challenge,
    title,
    status: statuses[0] || '',
    steps,
    details,
    banners
  });
})()
