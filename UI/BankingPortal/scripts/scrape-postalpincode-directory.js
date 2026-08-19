/*
 * Builds src/js/data/india-postal-directory.json from postalpincode.in.
 * Usage: node scripts/scrape-postalpincode-directory.js
 */
const fs = require('fs');
const path = require('path');
const https = require('https');

const baseUrl = 'https://www.postalpincode.in';
const outputPath = path.resolve(__dirname, '../src/js/data/india-postal-directory.json');
const stateIds = Array.from({ length: 40 }, (_, index) => index + 1);
const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

function get(url, redirects) {
  return new Promise((resolve, reject) => {
    const request = https.get(url, { headers: { 'User-Agent': 'MoneyBags postal-directory importer/1.0' } }, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location && redirects < 5) {
        response.resume();
        return resolve(get(new URL(response.headers.location, url).href, (redirects || 0) + 1));
      }
      if (response.statusCode !== 200) {
        response.resume();
        return reject(new Error(`Request failed (${response.statusCode}): ${url}`));
      }
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => resolve(body));
    });
    request.setTimeout(45000, () => request.destroy(new Error(`Request timed out: ${url}`)));
    request.on('error', reject);
  });
}

function decode(value) {
  return String(value || '')
    .replace(/&amp;/g, '&').replace(/&nbsp;/g, ' ').replace(/&#39;/g, "'")
    .replace(/&quot;/g, '"').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
}

function field(page, label, nextLabels) {
  const text = decode(page);
  const following = nextLabels.join('|');
  const match = text.match(new RegExp(`${label}\\s*:?\\s*(.+?)(?=\\s*(?:${following})\\s*:|$)`, 'i'));
  return match ? match[1].trim() : '';
}

function resultLinks(page) {
  const records = [];
  const pattern = /href=["']([^"']*Search-By-Location-Result\?Id=[^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let match;
  while ((match = pattern.exec(page))) {
    const href = new URL(match[1].replace(/&amp;/g, '&'), baseUrl).href;
    records.push({ href, area: decode(match[2]) });
  }
  return records;
}

function statePages(page, stateId) {
  const pages = new Set([`${baseUrl}/Search-By-Location?StateId=${stateId}`]);
  const pattern = new RegExp(`href=["']([^"']*Search-By-Location\\?[^"']*StateId=${stateId}[^"']*)["']`, 'gi');
  let match;
  while ((match = pattern.exec(page))) pages.add(new URL(match[1].replace(/&amp;/g, '&'), baseUrl).href);
  return Array.from(pages);
}

async function main() {
  const hierarchy = {};
  const visitedResults = new Set();
  for (const stateId of stateIds) {
    const initialUrl = `${baseUrl}/Search-By-Location?StateId=${stateId}`;
    let initialPage;
    try { initialPage = await get(initialUrl, 0); }
    catch (error) { console.warn(`Skipping StateId ${stateId}: ${error.message}`); continue; }

    const links = [];
    for (const pageUrl of statePages(initialPage, stateId)) {
      const page = pageUrl === initialUrl ? initialPage : await get(pageUrl, 0);
      links.push(...resultLinks(page));
      await delay(200);
    }
    for (const record of links) {
      if (visitedResults.has(record.href)) continue;
      visitedResults.add(record.href);
      try {
        const detail = await get(record.href, 0);
        const state = field(detail, 'State', ['Division', 'Region', 'Circle', 'Country', 'PIN Code', 'Pin Code']);
        const district = field(detail, 'District', ['State', 'Division', 'Region', 'Circle', 'Country', 'PIN Code', 'Pin Code']);
        const pincode = field(detail, 'PIN Code', ['Address', 'Country', 'Post Office']) || field(detail, 'Pin Code', ['Address', 'Country', 'Post Office']);
        const area = field(detail, 'Post Office', ['Post Office Type', 'Branch Type', 'Tehsil', 'District']) || record.area;
        if (state && district && area && /^\d{6}$/.test(pincode)) {
          hierarchy[state] = hierarchy[state] || {};
          hierarchy[state][district] = hierarchy[state][district] || [];
          hierarchy[state][district].push({ area, pincode });
        }
      } catch (error) { console.warn(`Skipping ${record.href}: ${error.message}`); }
      await delay(200);
    }
    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, JSON.stringify({ source: baseUrl, generatedAt: new Date().toISOString(), states: hierarchy }, null, 2));
    console.log(`StateId ${stateId} checkpointed.`);
  }
}

main().catch((error) => { console.error(error.stack || error.message); process.exitCode = 1; });
