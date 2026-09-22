// Capture print-resolution screenshots of the running RMS for Figures 3.8+.
//
// The in-app browser could not paint reliably (it needs the app window forward),
// so this drives its own headless Chrome instead. That also gets us exact viewport
// control and deviceScaleFactor, which the figures need: printed at 5.80in wide, a
// 1440px viewport at 2x is 2880px, about 496 dpi.
//
//   node shots_app.mjs [outdir]
import puppeteer from 'puppeteer-core';

const OUT = process.argv[2] || '.';
const APP = 'http://localhost:5173';
const PASS = 'admin123';
// Captured at 1024px wide, the tablet resolution NFR-09 specifies the system must
// support. Width is what sets printed text size: printed_pt = css_font_px * 72 *
// 5.80 / css_width, so 1024 gives ~5.7pt on a 14px UI font where 1440 gave 4.1pt.
const W = 1024, SCALE = 3;

const SHOTS = [
  { user: 'waiter',  route: '/pos',     file: 'app_pos.png',     wait: 'Cart|Menu|Kitchen', table: 'T-06', cat: 'Noodles' },
  { user: 'kitchen', route: '/kitchen', file: 'app_kitchen.png', wait: 'New|Preparing|Ready', h: 500 },
  { user: 'cashier', route: '/cashier', file: 'app_cashier.png', wait: 'Subtotal|VAT|Service', click: 'Order #' },
  { user: 'manager', route: '/manager', file: 'app_manager.png', wait: 'Sales|Stock|Dashboard', h: 1500 },
  { user: 'admin',   route: '/admin',   file: 'app_admin.png',   wait: 'Users|Menu|Suppliers', h: 1200 },
];

const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  args: ['--no-sandbox', '--font-render-hinting=none'],
});

async function login(page, user) {
  await page.goto(APP + '/login', { waitUntil: 'networkidle0' });
  // the login form has two inputs; fill them in order rather than by brittle selector
  const inputs = await page.$$('input');
  if (inputs.length < 2) throw new Error('login form not found for ' + user);
  await inputs[0].click({ clickCount: 3 });
  await inputs[0].type(user);
  await inputs[1].click({ clickCount: 3 });
  await inputs[1].type(PASS);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle0' }).catch(() => {}),
    page.evaluate(() => {
      const b = [...document.querySelectorAll('button')]
        .find(x => /sign in|log ?in/i.test(x.textContent));
      if (b) b.click();
    }),
  ]);
  await new Promise(r => setTimeout(r, 1500));
}

for (const s of SHOTS) {
  const page = await browser.newPage();
  await page.setViewport({ width: W, height: s.h || 768, deviceScaleFactor: SCALE });
  try {
    await login(page, s.user);
    await page.goto(APP + s.route, { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 2500));           // let data + websocket settle
    if (s.table) {
      // The POS lands on the floor plan. Open a free table so the figure shows the
      // item grid, category tabs and cart panel its caption describes - opening an
      // already-occupied table shows the submitted-order view instead. Nothing is
      // sent to the kitchen: the cart is client-side until "Send to Kitchen".
      const opened = await page.evaluate((label) => {
        const el = [...document.querySelectorAll('button,div,article,section')]
          .filter(x => (x.textContent || '').includes(label))
          .map(x => ({ x, r: x.getBoundingClientRect() }))
          .filter(o => o.r.width > 60 && o.r.width < 400 && o.r.height < 300)
          .sort((a, b) => a.r.width * a.r.height - b.r.width * b.r.height)[0];
        if (el) { el.x.click(); return el.x.textContent.trim().slice(0, 40); }
        return null;
      }, s.table);
      await new Promise(r => setTimeout(r, 2500));
      console.log('   opened: ' + (opened || 'NOTHING MATCHED'));

      const addTwo = () => page.evaluate(() => {
        const b = [...document.querySelectorAll('button')]
          .filter(x => /add to cart/i.test(x.textContent));
        if (b[0]) b[0].click();
        if (b[1]) b[1].click();
      });
      await addTwo();
      await new Promise(r => setTimeout(r, 800));
      // a second category, so the cart shows more than one section of the menu
      await page.evaluate((c) => {
        const b = [...document.querySelectorAll('button')]
          .find(x => x.textContent.trim() === c);
        if (b) b.click();
      }, s.cat);
      await new Promise(r => setTimeout(r, 1200));
      await addTwo();
      await new Promise(r => setTimeout(r, 1500));
    }
    if (s.click) {
      // open the first queued bill so the figure shows the itemised bill,
      // service charge and VAT rather than an empty selection prompt
      const picked = await page.evaluate((label) => {
        // smallest element that still contains the label: picking the first match
        // grabs the outer container and the click does nothing
        const cands = [...document.querySelectorAll('button,div,li,article')]
          .filter(x => new RegExp(label).test(x.textContent || ''))
          .map(x => ({ x, a: x.getBoundingClientRect().width * x.getBoundingClientRect().height }))
          .filter(o => o.a > 2000)
          .sort((p, q) => p.a - q.a);
        if (cands.length) { cands[0].x.click(); return cands[0].x.textContent.trim().slice(0, 40); }
        return null;
      }, s.click);
      await new Promise(r => setTimeout(r, 2000));
      console.log('   clicked: ' + (picked || 'NOTHING MATCHED'));
    }
    await page.evaluate(() => document.fonts.ready);
    const txt = await page.evaluate(() => document.body.innerText.slice(0, 400));
    const hit = new RegExp(s.wait, 'i').test(txt);
    await page.screenshot({ path: `${OUT}/${s.file}`, fullPage: !!s.full });
    console.log(`${s.file.padEnd(18)} ${s.user.padEnd(8)} ${s.route.padEnd(9)} ` +
                `${W * SCALE}x${(s.h || 768) * SCALE}  content:${hit ? 'ok' : 'CHECK'}  ` +
                `"${txt.replace(/\s+/g, ' ').slice(0, 60)}"`);
  } catch (e) {
    console.log(`${s.file.padEnd(18)} FAILED: ${e.message}`);
  }
  await page.close();
}
await browser.close();
