/**
 * lib/decrypt.js — SERVER-ONLY XOR Decrypt
 * OE_KEY HANYA ada di server, tidak pernah dikirim ke browser.
 */

const OE_KEY = process.env.OE_KEY || 'oFl1x_2026_sEcReT_kEy!@#';

/**
 * Decrypt base64-encoded XOR string from Cloudflare Worker.
 * @param {string} base64Str
 * @returns {string|null}
 */
export function oflixDecrypt(base64Str) {
  try {
    const buf        = Buffer.from(base64Str, 'base64');
    const keyBuf     = Buffer.from(OE_KEY, 'utf-8');
    const decrypted  = Buffer.alloc(buf.length);
    for (let i = 0; i < buf.length; i++) {
      decrypted[i] = buf[i] ^ keyBuf[i % keyBuf.length];
    }
    return decrypted.toString('utf-8');
  } catch {
    return null;
  }
}

/**
 * Fetch from CF Worker and decrypt response automatically.
 * Returns parsed JSON or null.
 */
export async function workerFetch(url) {
  const WORKER_URL = 'https://json.oflix.workers.dev';
  const fullUrl = url.startsWith('http') ? url : WORKER_URL + url;

  const res = await fetch(fullUrl, {
    headers: { 'X-OE': '1', 'User-Agent': 'Mozilla/5.0' },
    next: { revalidate: 0 },
  });

  if (!res.ok) return null;

  const text = await res.text();

  // If plain JSON
  if (text.startsWith('{') || text.startsWith('[')) {
    return JSON.parse(text);
  }

  // Otherwise decrypt
  const decrypted = oflixDecrypt(text);
  if (!decrypted) return null;
  return JSON.parse(decrypted);
}
