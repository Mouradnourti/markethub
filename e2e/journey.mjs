/**
 * End-to-end test of the main MarketHub journey through the real UI, with screenshots for the README.
 *
 *   customer browses → adds to cart → checks out (cash on delivery) → seller confirms, ships, delivers
 *   → customer sees the delivered parcel → admin dashboard.
 *
 * Usage (stack running on BASE_URL, default http://localhost:4200):
 *   cd e2e && npm install && npm test
 * Env: BASE_URL, BROWSER (Chrome/Edge executable), HEADLESS=false to watch, SCREENSHOTS=0 to skip images.
 */
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:4200';
const SHOTS_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '../docs/screenshots');
const TAKE_SHOTS = process.env.SCREENSHOTS !== '0';
const BROWSERS = [
  process.env.BROWSER,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium-browser',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].filter(Boolean);

const CUSTOMER = { email: 'customer1@markethub.test', password: 'Customer123!' };
const SELLER = { email: 'seller1@markethub.test', password: 'Seller123!' };
const ADMIN = { email: 'admin@markethub.test', password: 'Admin123!' };

let step = 0;
function pass(message) {
  console.log(`  ✔ ${++step}. ${message}`);
}

async function api(path, init = {}) {
  const response = await fetch(`${BASE_URL}${path}`, init);
  if (!response.ok) throw new Error(`${path} → HTTP ${response.status}`);
  return response.json();
}

async function shot(page, name) {
  if (!TAKE_SHOTS) return;
  await new Promise((r) => setTimeout(r, 600)); // let charts finish their animation
  await page.screenshot({ path: `${SHOTS_DIR}/${name}.png` });
}

async function settle(page) {
  await page.waitForNetworkIdle({ idleTime: 400, timeout: 15000 }).catch(() => undefined);
}

async function go(page, path) {
  await page.goto(`${BASE_URL}${path}`, { waitUntil: 'networkidle0' });
}

async function signIn(page, { email, password }) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0' });
  await page.evaluate(() => localStorage.clear());
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0' });
  await fill(page, 'input[formcontrolname=email]', email);
  await fill(page, 'input[formcontrolname=password]', password);
  await clickAndWaitForPath(page, 'button.submit', /^\/(seller|admin)?$/);
}

/**
 * Waits for a visible element and activates it with a DOM click. Synthetic mouse events proved flaky on some
 * Material buttons in headless mode, while the DOM click goes through the same Angular (click) handler.
 */
async function click(page, selector) {
  const element = await page.waitForSelector(selector, { visible: true, timeout: 15000 });
  await element.evaluate((el) => {
    el.scrollIntoView({ block: 'center' });
    (el.closest('button, a') ?? el).click();
  });
}

/** Sets an input's value and notifies Angular forms through the 'input' event. */
async function fill(page, selector, value) {
  const element = await page.waitForSelector(selector, { visible: true, timeout: 15000 });
  await element.evaluate((el, v) => {
    el.focus();
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
  }, value);
}

/** Clicks and waits until the SPA router reached a path matching the pattern. */
async function clickAndWaitForPath(page, selector, pattern) {
  await click(page, selector);
  await page.waitForFunction((source) => new RegExp(source).test(location.pathname), { timeout: 15000 }, pattern.source);
  await settle(page);
}

async function expectText(page, text) {
  await page.waitForFunction((t) => document.body.innerText.includes(t), { timeout: 15000 }, text);
}

async function main() {
  const executablePath = BROWSERS.find((path) => existsSync(path));
  if (!executablePath) throw new Error('No Chrome/Edge found; set BROWSER to its executable path');
  if (TAKE_SHOTS) mkdirSync(SHOTS_DIR, { recursive: true });

  // Pick an in-stock product of the seller's shop so the seller can process the order.
  const shop = await api('/api/shops/technova');
  const products = await api(`/api/products?shopId=${shop.id}&inStock=true&sort=POPULAR&size=1`);
  const product = products.content[0];

  const browser = await puppeteer.launch({
    executablePath,
    headless: process.env.HEADLESS !== 'false',
    defaultViewport: { width: 1440, height: 900 },
    args: ['--no-first-run', '--disable-features=Translate'],
  });
  const page = await browser.newPage();
  page.on('dialog', (dialog) => dialog.accept());
  console.log(`MarketHub E2E journey on ${BASE_URL}`);

  try {
    // ---------------------------------------------------------------- visitor
    await go(page, '/');
    await expectText(page, 'Best sellers');
    await shot(page, '01-home');
    pass('Home page shows the catalog highlights');

    await go(page, '/products?categoryId=1&sort=POPULAR');
    await expectText(page, 'products');
    await shot(page, '02-catalog');
    pass('Catalog filters by category and sorts by popularity');

    // --------------------------------------------------------------- customer
    await signIn(page, CUSTOMER);
    await go(page, '/');
    await expectText(page, 'Recommended for you');
    await shot(page, '03-recommendations');
    pass('Customer signs in and gets AI recommendations');

    await go(page, `/products/${product.id}`);
    await expectText(page, product.name);
    await shot(page, '04-product');
    const added = page.waitForResponse((r) => r.url().endsWith('/api/cart/items') && r.request().method() === 'POST');
    await click(page, 'button.grow');
    if (!(await added).ok()) throw new Error('Add to cart was refused');
    pass(`Product page: "${product.name}" added to the cart`);

    await go(page, '/cart');
    await expectText(page, 'Order summary');
    await shot(page, '05-cart');
    await clickAndWaitForPath(page, '::-p-text(Proceed to checkout)', /^\/checkout$/);
    await fill(page, 'input[formcontrolname=addressLine]', '12 rue de la Gare');
    await fill(page, 'input[formcontrolname=city]', 'Lyon');
    await fill(page, 'input[formcontrolname=postalCode]', '69002');
    await shot(page, '06-checkout');
    await clickAndWaitForPath(page, '::-p-text(Place order)', /^\/orders\/\d+$/);
    await expectText(page, 'is confirmed');
    const reference = await page.$eval('h1', (h1) => h1.textContent.replace('Order', '').trim());
    await shot(page, '07-order-confirmation');
    pass(`Cash-on-delivery order ${reference} placed`);

    // ----------------------------------------------------------------- seller
    await signIn(page, SELLER);
    await expectText(page, 'Monthly revenue');
    await expectText(page, 'Projected revenue');
    await shot(page, '08-seller-dashboard');
    pass('Seller dashboard shows KPIs, charts and the AI sales forecast');

    await go(page, '/seller/orders');
    await click(page, `::-p-text(${reference})`);
    await expectText(page, 'Your earnings');
    await shot(page, '09-seller-order');
    await click(page, '.detail ::-p-text(Confirm)');
    await expectText(page, 'is now confirmed');
    await fill(page, '.detail input', 'TRK-E2E-001');
    await click(page, '.detail ::-p-text(Mark shipped)');
    await expectText(page, 'is now shipped');
    await click(page, '.detail ::-p-text(Mark delivered)');
    await expectText(page, 'is now delivered');
    pass('Seller confirms, ships (with tracking number) and delivers the parcel');

    // -------------------------------------------------------- customer again
    await signIn(page, CUSTOMER);
    await go(page, '/orders');
    await click(page, `::-p-text(${reference})`);
    await expectText(page, 'TRK-E2E-001');
    await expectText(page, 'Return this parcel');
    await shot(page, '10-order-tracking');
    pass('Customer sees the delivered parcel, its tracking number and can request a return');

    // ------------------------------------------------------------------ admin
    await signIn(page, ADMIN);
    await expectText(page, 'Gross merchandise value');
    await shot(page, '11-admin-dashboard');
    await go(page, '/admin/shops');
    await expectText(page, 'Green Corner');
    await shot(page, '12-admin-shops');
    pass('Admin dashboard and seller validation');

    console.log(`\nE2E journey passed (${step} steps).`);
  } catch (error) {
    if (TAKE_SHOTS) await page.screenshot({ path: `${SHOTS_DIR}/failure.png` }).catch(() => undefined);
    throw error;
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(`\n✖ E2E journey failed at step ${step + 1}: ${error.message}`);
  process.exit(1);
});
