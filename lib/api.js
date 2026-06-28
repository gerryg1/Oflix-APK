import {
  fetchCategoryAction,
  fetchDetailAction,
  fetchSearchAction,
  fetchKomikPopulerAction,
  fetchKomikDetailAction,
  fetchKomikSearchAction,
  fetchDonghuaSearchAction,
  fetchDonghuaDetailAction,
  fetchDonghuaPlayAction,
  fetchDonghuaPopulerAction,
  fetchKomikBacaAction,
  fetchPlayAction
} from '@/lib/actions';


// ─── Base URLs ───────────────────────────────────────────────────────────────
// Next.js API routes — endpoint baru (server-side)
export const CACHE_API    = '/api/cache';          // ← was cache_api.js
export const KOMIK_API    = '/api/komik';           // ← was komik_api.js
export const DONGHUA_API  = '/api/donghua';         // ← was donghua_api.js
export const DONGHUA_STREAM = '/api/donghua-stream'; // ← was donghua_stream.js
export const SUBTITLE_API = '/api/subtitle-proxy';  // ← was subtitle-proxy.js
export const AUTH_API     = '/auth_api.php';        // PHP tetap
export const WORKER_URL   = ''; // Kosongkan agar pakai API Route lokal Next.js

// ─── Helpers ─────────────────────────────────────────────────────────────────
export function imgProxy(url) { return url || ''; }

export function stripQuery(url) {
  return (url || '').split('?')[0];
}

export function parseFoodcashUrl(url) {
  try {
    const u = new URL(url);
    return {
      id:         u.searchParams.get('id'),
      season:     u.searchParams.get('season'),
      episode:    u.searchParams.get('episode'),
      detailPath: u.searchParams.get('detailPath'),
    };
  } catch { return {}; }
}

export function fmtTime(s) {
  if (!s || isNaN(s)) return '0:00';
  const h   = Math.floor(s / 3600);
  const m   = Math.floor((s % 3600) / 60);
  const sec = Math.floor(s % 60);
  if (h > 0) return `${h}:${m.toString().padStart(2,'0')}:${sec.toString().padStart(2,'0')}`;
  return `${m}:${sec.toString().padStart(2,'0')}`;
}

function triggerStart() {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('oflix-fetch-start'));
}

function triggerEnd() {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('oflix-fetch-end'));
}

async function withLoading(promiseFunc) {
  triggerStart();
  try {
    return await promiseFunc();
  } finally {
    triggerEnd();
  }
}

// ─── In-Memory Cache (10 menit TTL) ──────────────────────────────────────────
const _cache = new Map();
const CACHE_TTL = 10 * 60 * 1000;
const _inflight = new Map();

function cacheGet(key) {
  const entry = _cache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.ts > CACHE_TTL) { _cache.delete(key); return null; }
  return entry.data;
}
function cacheSet(key, data) {
  _cache.set(key, { data, ts: Date.now() });
}

// ─── Safe Fetch (dedup + cache) ───────────────────────────────────────────────
async function safeFetch(url, opts = {}) {
  const isGet = !opts.method || opts.method === 'GET';
  const cacheKey = isGet ? url : null;

  if (cacheKey) {
    const hit = cacheGet(cacheKey);
    if (hit) return hit;
    if (_inflight.has(cacheKey)) return _inflight.get(cacheKey);
  }

  const controller = new AbortController();
  const tid = setTimeout(() => controller.abort(), 15000);

  const promise = (async () => {
    try {
      const res  = await fetch(url, { ...opts, signal: controller.signal });
      clearTimeout(tid);
      const text = await res.text();
      let data;
      try { data = JSON.parse(text); }
      catch { throw new Error('Non-JSON: ' + text.slice(0, 120)); }
      if (cacheKey) cacheSet(cacheKey, data);
      return data;
    } finally {
      clearTimeout(tid);
      if (cacheKey) _inflight.delete(cacheKey);
    }
  })();

  if (cacheKey) _inflight.set(cacheKey, promise);
  return promise;
}

// ─── Stream: langsung ke CF Worker (terenkripsi) ──────────────────────────────
// Stream masih hit CF Worker langsung karena butuh real-time, tidak di-cache
const _STREAM_KEY = 'oFl1x_2026_sEcReT_kEy!@#'; // masih butuh di client untuk decode stream

function clientDecrypt(base64Str) {
  try {
    const binary = atob(base64Str);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const keyBytes = new TextEncoder().encode(_STREAM_KEY);
    const decrypted = new Uint8Array(bytes.length);
    for (let i = 0; i < bytes.length; i++) {
      decrypted[i] = bytes[i] ^ keyBytes[i % keyBytes.length];
    }
    return new TextDecoder().decode(decrypted);
  } catch { return null; }
}

// ─── API Functions ────────────────────────────────────────────────────────────

export async function fetchCategory(action, page = 1) {
  return withLoading(() => fetchCategoryAction(action, page));
}

export async function fetchDetail(detailPath) {
  return withLoading(() => fetchDetailAction(detailPath));
}

export async function fetchSearch(q, page = 1) {
  return withLoading(() => fetchSearchAction(q, page));
}

export async function fetchStream(id, season, episode, detailPath) {
  return withLoading(async () => {
    for (let attempt = 1; attempt <= 3; attempt++) {
      try {
        const data = await fetchPlayAction(id, season, episode, detailPath);
        if (data.success && (data.downloads?.length > 0 || data.url)) return data;
        if (attempt === 3) return data;
        await new Promise(r => setTimeout(r, 500));
      } catch (e) {
        if (attempt === 3) return { success: false, error: e.message };
        await new Promise(r => setTimeout(r, 500));
      }
    }
    return { success: false, error: 'All retries failed' };
  });
}

// Komik
export async function fetchKomikPopuler(page = 1) {
  return withLoading(() => fetchKomikPopulerAction(page));
}
export async function fetchKomikDetail(slug) {
  return withLoading(() => fetchKomikDetailAction(slug));
}
export async function fetchKomikSearch(q) {
  return withLoading(() => fetchKomikSearchAction(q));
}
export async function fetchKomikBaca(bacaSlug) {
  return withLoading(() => fetchKomikBacaAction(bacaSlug));
}

// Donghua
export async function fetchDonghuaSearch(q) {
  return withLoading(() => fetchDonghuaSearchAction(q));
}
export async function fetchDonghuaDetail(slug) {
  return withLoading(() => fetchDonghuaDetailAction(slug));
}
export async function fetchDonghuaPlay(ep) {
  return withLoading(() => fetchDonghuaPlayAction(ep));
}
export async function fetchDonghuaPopuler(page = 1) {
  return withLoading(() => fetchDonghuaPopulerAction(page));
}
export async function fetchDonghuaStream(ep) {
  return withLoading(() => safeFetch(`${DONGHUA_STREAM}?ep=${encodeURIComponent(ep)}`));
}

// Auth
export async function authLogin(username, password) {
  return safeFetch(`${AUTH_API}?action=login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
}
export async function authVerify(token) {
  return safeFetch(`${AUTH_API}?action=verify&token=${encodeURIComponent(token)}`);
}
export async function authGetCW(token) {
  return safeFetch(`${AUTH_API}?action=getCW`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  });
}
export function authSaveCW(token, type, key, data) {
  fetch(`${AUTH_API}?action=saveCW`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, type, key, data }),
  }).catch(() => {});
}

// Panel ping
export function ping(page, username = '') {
  fetch(`/panel_api.php?action=ping&page=${encodeURIComponent(page)}&user=${encodeURIComponent(username)}`)
    .catch(() => {});
}
