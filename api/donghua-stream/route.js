/**
 * app/api/donghua-stream/route.js — Extract & proxy stream Anichin
 */

import * as cheerio from 'cheerio';
import { NextResponse } from 'next/server';

const ANICHIN_BASE    = 'https://anichin.watch';
const CF_PROXY        = 'https://proxy-anichin.oflix.workers.dev';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

function getCFCookie() {
  return process.env.ANICHIN_CF_CLEARANCE || '';
}

async function simpleFetch(url, customHeaders = {}) {
  try {
    const resp = await fetch(url, {
      headers: { 'User-Agent': UA, ...customHeaders },
      next: { revalidate: 0 },
    });
    if (!resp.ok) return null;
    return await resp.text();
  } catch { return null; }
}

function extractAnichinStream(url) {
  try {
    const urlObj  = new URL(url);
    const videoId = urlObj.searchParams.get('id') || (url.match(/[?&]id=([^&]+)/) || [])[1];
    const base    = `${urlObj.protocol}//${urlObj.host}`;
    if (videoId) {
      return { playerUrl: url, streamUrl: `${base}/hls/${videoId}.m3u8`, streamType: 'm3u8', videoId, source: 'anichin.stream' };
    }
  } catch {}
  return null;
}

function extractGeneric(url) {
  return { playerUrl: url, streamUrl: url, streamType: url.includes('.m3u8') ? 'm3u8' : 'mp4', source: 'generic' };
}

async function extractOkRu(url) {
  let finalUrl = url;
  if (!finalUrl.includes('videoembed')) {
    const match = finalUrl.match(/\/video\/(\d+)/);
    if (match) finalUrl = 'https://ok.ru/videoembed/' + match[1];
  }
  if (!finalUrl.startsWith('http')) finalUrl = 'https:' + finalUrl;

  const html = await simpleFetch(finalUrl, { Referer: `${ANICHIN_BASE}/` });
  if (!html) return null;

  const matchOpts = html.match(/data-options="([^"]+)"/);
  if (matchOpts) {
    try {
      const raw  = matchOpts[1].replace(/&quot;/g, '"');
      const opts = JSON.parse(raw);
      const ms   = opts.flashvars?.metadata;
      if (ms) {
        const meta = JSON.parse(ms);
        if (meta) {
          const hls = meta.hlsManifestUrl || meta.hlsMasterPlaylistUrl || meta.ondemandHls || '';
          if (hls) return { playerUrl: finalUrl, streamUrl: hls, streamType: 'm3u8', source: 'ok.ru' };
        }
      }
    } catch {}
  }

  const matchHls = html.match(/"(?:hlsManifestUrl|ondemandHls)"\s*:\s*"([^"]+)"/);
  if (matchHls) {
    return { playerUrl: finalUrl, streamUrl: matchHls[1].replace(/\\\//g, '/'), streamType: 'm3u8', source: 'ok.ru' };
  }
  return null;
}

export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const ep = searchParams.get('ep') || '';

  const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Content-Type': 'application/json',
  };

  if (!ep) {
    return NextResponse.json({ error: 'Parameter ep wajib diisi', usage: '?ep={episode-slug}' }, { status: 400, headers: corsHeaders });
  }

  const CF_COOKIE = getCFCookie();

  try {
    const url      = `${ANICHIN_BASE}/${ep}/`;
    const response = await fetch(url, {
      headers: {
        'User-Agent': UA,
        'Cookie': `cf_clearance=${CF_COOKIE}`,
        'Referer': `${ANICHIN_BASE}/`,
      },
      next: { revalidate: 0 },
    });

    if (!response.ok) {
      return NextResponse.json({ success: false, error: 'Gagal fetch episode page (CF block?)', episode: ep }, { status: 502, headers: corsHeaders });
    }

    const html = await response.text();
    const $    = cheerio.load(html);
    let playerUrl = '';

    const iframe1 = $('.video-content iframe').attr('data-src') || $('.video-content iframe').attr('src');
    if (iframe1 && !iframe1.includes('about:blank')) playerUrl = iframe1;
    if (!playerUrl) {
      $('iframe').each((i, el) => {
        const src = $(el).attr('data-src') || $(el).attr('src');
        if (src && !src.includes('about:blank')) { playerUrl = src; return false; }
      });
    }
    if (!playerUrl) {
      const m = html.match(/(?:src|url|file)\s*[:=]\s*["']([^"']+(?:embed|player|video)[^"']*)['"]/i);
      if (m) playerUrl = m[1];
    }

    if (!playerUrl) {
      return NextResponse.json({ success: false, error: 'playerUrl not found', episode: ep }, { status: 404, headers: corsHeaders });
    }

    let result = null;
    if (playerUrl.includes('anichin.stream') || playerUrl.includes('anichin.club')) {
      result = extractAnichinStream(playerUrl);
      if (!result) result = extractGeneric(playerUrl);
    } else if (playerUrl.includes('ok.ru') || playerUrl.includes('odnoklassniki')) {
      result = await extractOkRu(playerUrl);
    } else {
      result = extractGeneric(playerUrl);
    }

    if (!result) result = { playerUrl, error: 'Could not extract stream' };

    if (result.streamUrl && result.streamType === 'm3u8') {
      result.proxiedUrl = `${CF_PROXY}/?url=${encodeURIComponent(result.streamUrl)}`;
    }

    result.success = true;
    result.episode = ep;

    return NextResponse.json(result, {
      headers: { ...corsHeaders, 'Cache-Control': 's-maxage=2592000, stale-while-revalidate=86400' },
    });

  } catch (error) {
    return NextResponse.json({ success: false, error: 'Internal Error', message: error.message }, { status: 500, headers: corsHeaders });
  }
}

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 200,
    headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, OPTIONS' },
  });
}
