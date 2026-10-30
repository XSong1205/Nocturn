import http from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { endpoints, validateParameters } from './endpoints.mjs';

export const VERSION = '0.1.0';
export const MAX_BODY_BYTES = 64 * 1024;

class RequestError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

function sendJson(response, status, body, extraHeaders = {}) {
  if (response.destroyed || response.writableEnded) return;
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Connection': 'close',
    ...extraHeaders,
  });
  response.end(JSON.stringify(body));
}

function authorized(request, token) {
  const actual = Buffer.from(request.headers.authorization || '', 'utf8');
  const expected = Buffer.from(`Bearer ${token}`, 'utf8');
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

async function readJson(request) {
  if (!/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] || '')) {
    throw new RequestError(415, 'UNSUPPORTED_MEDIA_TYPE', 'Use application/json.');
  }
  if (request.headers['content-encoding'] && request.headers['content-encoding'] !== 'identity') {
    throw new RequestError(415, 'UNSUPPORTED_ENCODING', 'Compressed request bodies are unsupported.');
  }
  if (Number(request.headers['content-length']) > MAX_BODY_BYTES) {
    throw new RequestError(413, 'BODY_TOO_LARGE', 'Request body exceeds 64 KiB.');
  }
  let size = 0;
  const chunks = [];
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new RequestError(413, 'BODY_TOO_LARGE', 'Request body exceeds 64 KiB.');
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new RequestError(400, 'INVALID_JSON', 'Request body must be valid JSON.'); }
}

/** Owns only loopback HTTP. Provider injection keeps transport tests independent of music services. */
export async function startLocalApi({ token, port = 0, provider, requestTimeoutMs = 25000, maxConcurrent = 8 }) {
  if (typeof token !== 'string' || token.length < 32 || token.length > 256 || !/^[A-Za-z0-9_-]+$/.test(token)) {
    throw new Error('NOCTURN_API_TOKEN must be 32–256 URL-safe characters.');
  }
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Invalid LocalAPI port.');
  if (typeof provider !== 'function') throw new Error('A LocalAPI provider is required.');
  let inFlight = 0;
  const server = http.createServer({ maxHeaderSize: 16384 }, async (request, response) => {
    let active = false;
    let timer;
    try {
      // Origin is never used by native clients. Reject browser-originated requests (including localhost pages).
      if (request.headers.origin) throw new RequestError(403, 'ORIGIN_FORBIDDEN', 'Browser origins are unsupported.');
      // Require origin-form URLs; no proxy requests, escaped routes or query-string cookie transport.
      const path = request.url;
      if (!path || !/^\/[a-z0-9/]+$/.test(path)) throw new RequestError(404, 'NOT_FOUND', 'Unknown route.');
      if (path === '/health') {
        if (request.method !== 'GET') throw new RequestError(405, 'METHOD_NOT_ALLOWED', 'Use GET for health.');
        sendJson(response, 200, { app: 'Nocturn LocalAPI', version: VERSION, status: 'ready' });
        return;
      }
      if (!authorized(request, token)) throw new RequestError(401, 'UNAUTHORIZED', 'A valid bearer token is required.');
      const endpoint = path.startsWith('/api/') ? path.slice(5) : '';
      if (!Object.hasOwn(endpoints, endpoint)) throw new RequestError(404, 'NOT_FOUND', 'Unknown endpoint.');
      if (request.method !== 'POST') throw new RequestError(405, 'METHOD_NOT_ALLOWED', 'Use POST for API requests.');
      const body = await readJson(request);
      const invalid = validateParameters(endpoint, body);
      if (invalid) throw new RequestError(400, 'INVALID_PARAMETERS', invalid);
      if (inFlight >= maxConcurrent) throw new RequestError(429, 'BUSY', 'Too many concurrent requests.');
      inFlight += 1;
      active = true;
      // Timeout covers bootstrap and request; provider also bounds network I/O to prevent abandoned sockets.
      const result = await Promise.race([
        Promise.resolve().then(() => provider(endpoint, body)),
        new Promise((_, reject) => {
          timer = setTimeout(() => reject(new RequestError(504, 'UPSTREAM_TIMEOUT', 'Music service timed out.')), requestTimeoutMs);
        }),
      ]);
      const bodyResult = result?.body ?? result;
      if (bodyResult === undefined || bodyResult === null || typeof bodyResult !== 'object') {
        throw new RequestError(502, 'INVALID_UPSTREAM_RESPONSE', 'Music service returned an invalid response.');
      }
      // Successful transport preserves business codes (including QR polling codes and permission results).
      sendJson(response, 200, bodyResult);
    } catch (error) {
      if (error instanceof RequestError) {
        sendJson(response, error.status, { error: { code: error.code, message: error.message } });
      } else if (error?.body && typeof error.body === 'object' && Number(error.status ?? error.body.code) < 500) {
        // Preserve upstream business response for login/permissions without returning cookies in headers.
        sendJson(response, 200, error.body);
      } else {
        // Never expose arbitrary Error messages: axios errors can contain URLs, cookies or internal paths.
        sendJson(response, 502, { error: { code: 'UPSTREAM_FAILURE', message: 'Music service request failed.' } });
      }
    } finally {
      clearTimeout(timer);
      if (active) inFlight -= 1;
    }
  });
  server.requestTimeout = 15000;
  server.headersTimeout = 10000;
  server.keepAliveTimeout = 1000;
  server.maxRequestsPerSocket = 1;
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => {
      server.removeListener('error', reject);
      resolve();
    });
  });
  return { server, port: server.address().port, close: () => new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())) };
}
