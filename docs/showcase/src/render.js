// Renders each <section> of showcase.html to docs/showcase/<id>.jpg.
// Run from the repo root: node docs/showcase/src/render.js  (needs Playwright)
const path = require('path');
const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.join(__dirname, 'showcase.html'), { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  for (const id of await page.$$eval('section', s => s.map(e => e.id))) {
    await page.locator('#' + id).screenshot({ path: path.join(__dirname, '..', id + '.jpg'), type: 'jpeg', quality: 88 });
    console.log('rendered', id);
  }
  await browser.close();
})();
