/**
 * Exports the backend OpenAPI document and renders docs/api-contract.md from it.
 * Usage (backend running): node scripts/export-api-contract.mjs [http://localhost:8080]
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const backend = process.argv[2] ?? 'http://localhost:8080';
const docs = resolve(dirname(fileURLToPath(import.meta.url)), '../docs');

const spec = await (await fetch(`${backend}/v3/api-docs`)).json();
writeFileSync(`${docs}/openapi.json`, JSON.stringify(spec, null, 2));

const PUBLIC = [/^\/api\/auth\/(register|login|refresh)$/, /^\/api\/payments\/stripe\/webhook$/];
const PUBLIC_GET = [/^\/api\/(products|categories|shops|config)/];
function access(path, method) {
  if (PUBLIC.some((r) => r.test(path)) || (method === 'get' && PUBLIC_GET.some((r) => r.test(path)) && !path.endsWith('/eligibility'))) {
    return 'public';
  }
  if (path.startsWith('/api/internal')) return 'X-Internal-Key';
  if (path.startsWith('/api/seller')) return 'SELLER';
  if (path.startsWith('/api/admin')) return 'ADMIN';
  if (/^\/api\/(cart|orders|recommendations)/.test(path)) return 'CUSTOMER';
  return 'authenticated';
}

function schemaName(ref) {
  return ref ? ref.split('/').pop() : '';
}

function bodyOf(operation) {
  const content = operation.requestBody?.content ?? {};
  const media = Object.values(content)[0];
  return schemaName(media?.schema?.$ref) || (content['multipart/form-data'] ? 'multipart file' : '');
}

function responseOf(operation) {
  const ok = operation.responses?.['200'] ?? operation.responses?.['201'] ?? operation.responses?.['204'];
  const media = ok?.content ? Object.values(ok.content)[0] : undefined;
  const schema = media?.schema;
  if (!schema) return ok ? '—' : '';
  if (schema.$ref) return schemaName(schema.$ref);
  if (schema.type === 'array') return `${schemaName(schema.items?.$ref) || schema.items?.type}[]`;
  return schema.type ?? 'object';
}

const byTag = new Map();
for (const [path, item] of Object.entries(spec.paths)) {
  for (const [method, operation] of Object.entries(item)) {
    const tag = operation.tags?.[0] ?? 'Other';
    if (!byTag.has(tag)) byTag.set(tag, []);
    byTag.get(tag).push({ path, method, operation });
  }
}

const lines = [
  '# API contract',
  '',
  `Generated from the live OpenAPI document (\`${backend}/v3/api-docs\`) by \`scripts/export-api-contract.mjs\`.`,
  'The full machine-readable spec is [`openapi.json`](openapi.json); interactive docs: http://localhost:8080/swagger-ui.html.',
  '',
  'Conventions: JSON bodies, `Authorization: Bearer <access token>` for authenticated calls, errors as',
  '[RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `application/problem+json` with `status`, `title`, `detail`',
  'and, for validation errors (400), an `errors` map `field → message`. Pagination: `page` (0-based) and `size`;',
  'paginated responses are `{ content, page, size, totalElements, totalPages }`.',
  '',
  '## Backend (Spring Boot, port 8080)',
  '',
];
for (const tag of [...byTag.keys()].sort()) {
  lines.push(`### ${tag}`, '', '| Method | Path | Access | Request | Response | Description |', '|---|---|---|---|---|---|');
  for (const { path, method, operation } of byTag.get(tag).sort((a, b) => a.path.localeCompare(b.path))) {
    lines.push(`| ${method.toUpperCase()} | \`${path}\` | ${access(path, method)} | ${bodyOf(operation)} | ${responseOf(operation)} | ${operation.summary ?? ''} |`);
  }
  lines.push('');
}
lines.push(readFileSync(`${docs}/api-contract.services.md`, 'utf8'));
writeFileSync(`${docs}/api-contract.md`, lines.join('\n'));
console.log(`Wrote docs/api-contract.md (${[...byTag.values()].flat().length} operations) and docs/openapi.json`);
