// Capture the public product pages of the three systems reviewed in Chapter 2.
//
// These are the vendors' own product pages, which is where they publish images of
// their interfaces. Nothing is signed into and no account is created; only
// publicly served pages are loaded. Cloudbeds (CloudFront) and eZee reject the
// default headless fingerprint, so a normal desktop Chrome user agent is sent;
// nothing else about the request is altered.
//
// Each capture is a region rather than the whole page: these marketing pages run
// to 11,000-29,000 px and a full-page grab would be unusable as a figure. The
// scroll offset for each site targets the section showing the product interface.
//
// Every figure using these must be captioned with the vendor, the URL and the
// date of capture.
//
//   node shots_competitors.mjs [outdir]
import puppeteer from 'puppeteer-core';

const OUT = process.argv[2] || '.';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
           '(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36';
const W = 1280, H = 820, SCALE = 2;

const SITES = [
  // Only the vendors that publish an image of their own product interface. Maestro
  // PMS publishes none anywhere on maestropms.com - its pages carry only the logo -
  // so Section 2.1.3 says so rather than substituting a marketing photograph.
  { file: 'comp_cloudbeds.png', name: 'Cloudbeds',
    url: 'https://www.cloudbeds.com/property-management-system/',
    pick: /img-product-pms-tab-intuitive/,
    // the page crops this image inside a tab panel, so it is loaded on its own and
    // captured at its natural size - the same pixels the vendor publishes
    direct: 'https://www.cloudbeds.com/wp-content/uploads/2025/09/img-product-pms-tab-intuitive.webp' },
  { file: 'comp_ezee.png', name: 'eZee Optimus (eZee Technosys / Yanolja Cloud Solution)',
    url: 'https://www.ezeeoptimus.com/', pick: /banner_snap/,
    direct: 'https://www.ezeeoptimus.com/wp-content/themes/optimus/images/banner_snap4.4.webp' },
];

const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  args: ['--no-sandbox', '--font-render-hinting=none'],
});

// Best-effort consent dismissal, preferring the privacy-preserving choice:
// reject / necessary-only first, a plain close only if no such control exists.
async function dismissConsent(page) {
  const label = await page.evaluate(() => {
    const pats = [/reject all/i, /decline all/i, /only necessary/i, /necessary only/i,
                  /strictly necessary/i, /reject/i, /decline/i, /confirm my choices/i];
    const btns = [...document.querySelectorAll('button,a,[role=button]')]
      .filter(b => b.offsetParent !== null);
    for (const p of pats) {
      const b = btns.find(x => p.test((x.textContent || '').trim()));
      if (b) { b.click(); return b.textContent.trim().slice(0, 30); }
    }
    const c = btns.find(x => /^(close|×|x)$/i.test((x.textContent || '').trim()) ||
                             /close/i.test(x.getAttribute('aria-label') || ''));
    if (c) { c.click(); return 'Close'; }
    return null;
  });
  await new Promise(r => setTimeout(r, 1200));
  return label;
}

for (const s of SITES) {
  const page = await browser.newPage();
  await page.setUserAgent(UA);
  await page.setExtraHTTPHeaders({ 'Accept-Language': 'en-GB,en;q=0.9' });
  await page.setViewport({ width: W, height: H, deviceScaleFactor: SCALE });
  try {
    if (s.direct) {
      const r = await page.goto(s.direct, { waitUntil: 'networkidle0', timeout: 45000 });
      const d = await page.evaluate(() => {
        const i = document.querySelector('img');
        return { w: i.naturalWidth, h: i.naturalHeight };
      });
      await page.setViewport({ width: d.w, height: d.h, deviceScaleFactor: 1 });
      await new Promise(x => setTimeout(x, 600));
      await page.screenshot({ path: `${OUT}/${s.file}`,
                              clip: { x: 0, y: 0, width: d.w, height: d.h } });
      console.log(`${s.file.padEnd(22)} HTTP ${r.status()}  ${d.w}x${d.h}  ${s.name}`);
      await page.close();
      continue;
    }
    const resp = await page.goto(s.url, { waitUntil: 'networkidle2', timeout: 45000 });
    const consent = await dismissConsent(page);
    await new Promise(r => setTimeout(r, 2000));
    // clip to the vendor's own product image rather than a slab of marketing page
    const box = await page.evaluate((src) => {
      const re = new RegExp(src.slice(1, src.lastIndexOf('/')), src.endsWith('i') ? 'i' : '');
      const imgs = [...document.querySelectorAll('img')]
        .filter(i => re.test(i.currentSrc || i.src || '') || re.test(i.alt || ''))
        .map(i => { const r = i.getBoundingClientRect();
                    return { i, w: r.width, h: r.height }; })
        .filter(o => o.w >= 400 && o.h >= 240)
        .sort((a, b) => b.w * b.h - a.w * a.h);
      if (!imgs.length) return null;
      imgs[0].i.scrollIntoView({ block: 'center' });
      return new Promise(res => setTimeout(() => {
        const r = imgs[0].i.getBoundingClientRect();
        res({ x: Math.max(0, r.left - 8), y: Math.max(0, r.top - 8),
              width: Math.min(r.width + 16, 1280), height: r.height + 16,
              file: (imgs[0].i.currentSrc || imgs[0].i.src).split('/').pop().slice(0, 40) });
      }, 900));
    }, s.pick.toString());
    await new Promise(r => setTimeout(r, 1200));
    await page.evaluate(() => document.fonts.ready);
    if (box) {
      await page.screenshot({ path: `${OUT}/${s.file}`, clip: box });
      console.log(`   clipped to ${Math.round(box.width)}x${Math.round(box.height)}  ${box.file}`);
    } else {
      await page.screenshot({ path: `${OUT}/${s.file}` });
      console.log('   no product image matched - captured viewport instead');
    }
    const title = await page.title();
    console.log(`${s.file.padEnd(22)} HTTP ${resp.status()}  ${W * SCALE}x${H * SCALE}  ` +
                `consent:${consent || 'none'}`);
    console.log(`   ${s.name} — ${title.slice(0, 64)}`);
  } catch (e) {
    console.log(`${s.file.padEnd(22)} FAILED  ${e.message.slice(0, 90)}`);
  }
  await page.close();
}
await browser.close();
