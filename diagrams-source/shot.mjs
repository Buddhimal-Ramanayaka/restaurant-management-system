// Render a mockup HTML to PNG at print resolution.
//   node shot.mjs <in.html> <out.png> [scale]
import puppeteer from 'puppeteer-core';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const [inHtml, outPng, scale = '3'] = process.argv.slice(2);
const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  args: ['--no-sandbox', '--font-render-hinting=none', '--allow-file-access-from-files'],
});
const page = await browser.newPage();
// start short so scrollHeight reports true content height rather than the viewport
await page.setViewport({ width: 1040, height: 200, deviceScaleFactor: Number(scale) });
await page.goto(pathToFileURL(resolve(inHtml)).href, { waitUntil: 'networkidle0' });
await page.evaluate(() => document.fonts.ready);
const box = await page.evaluate(() => ({
  w: Math.ceil(document.body.getBoundingClientRect().width),
  h: Math.ceil(Math.max(document.documentElement.scrollHeight, document.body.scrollHeight)),
}));
await page.setViewport({ width: box.w, height: box.h, deviceScaleFactor: Number(scale) });
await page.screenshot({ path: outPng, clip: { x: 0, y: 0, width: box.w, height: box.h } });
await browser.close();
console.log(`${outPng}  ${box.w}x${box.h} css px  @${scale}x`);
