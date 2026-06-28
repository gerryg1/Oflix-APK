/**
 * app/api/donghua/route.js — Anichin.watch Scraper
 * CF_CLEARANCE cookie dari env var, aman di server.
 */

import * as cheerio from 'cheerio';
import { NextResponse } from 'next/server';

const ANICHIN_BASE = 'https://anichin.watch';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

function getCFCookie() {
  return process.env.ANICHIN_CF_CLEARANCE || '';
}

function cfError() {
  const CF_COOKIE = getCFCookie();
  return {
    error: 'Cloudflare block — cf_clearance cookie mungkin expired',
    hint: 'Update env var ANICHIN_CF_CLEARANCE di Vercel',
    cf_cookie_set: CF_COOKIE !== '',
  };
}

async function anichinFetch(url) {
  const CF_COOKIE = getCFCookie();
  try {
    const r = await fetch(url, {
      headers: {
        'User-Agent': UA,
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'id-ID,id;q=0.9,en;q=0.8',
        'Cookie': `cf_clearance=${CF_COOKIE}`,
        'Referer': `${ANICHIN_BASE}/`,
        'DNT': '1',
      },
      next: { revalidate: 0 },
    });
    if (!r.ok) return null;
    const html = await r.text();
    if (html.toLowerCase().includes('just a moment') || html.toLowerCase().includes('cf-challenge')) return null;
    return html;
  } catch { return null; }
}

function parseListPage(html, category, page) {
  const $ = cheerio.load(html);
  const items = [];
  $('.listupd .bs').each((i, el) => {
    const a    = $(el).find('a[href]').first();
    if (!a.length) return;
    const href  = a.attr('href');
    const title = a.attr('title') || a.text().trim();
    const slug  = href.replace(/\/$/, '').split('/').pop();
    const poster = $(el).find('img').first().attr('src') || '';
    const status = $(el).find('.epx').text().trim();
    const type   = $(el).find('.typez').text().trim().toLowerCase() || 'donghua';
    items.push({ title, detailPath: slug, poster, status, type });
  });
  if (items.length === 0) return { error: 'listupd not found or empty', cf_ok: !html.includes('cf-challenge') };
  return { category, page, items };
}

export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get('action') || '';

  const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Content-Type': 'application/json; charset=utf-8',
  };

  if (!action) {
    return NextResponse.json({
      success: false, error: 'action wajib diisi',
      endpoints: { populer: '?action=populer&page=1', search: '?action=search&q=Tales&page=1', detail: '?action=detail&slug={slug}', play: '?action=play&ep={episode-slug}' },
    }, { status: 400, headers: corsHeaders });
  }

  try {
    // ── POPULER ──
    if (action === 'populer') {
      const pPop    = parseInt(searchParams.get('page') || '1', 10);
      const urlPop  = `${ANICHIN_BASE}/donghua/?page=${pPop}&status=&type=&order=popular`;
      const htmlPop = await anichinFetch(urlPop);
      const result  = !htmlPop ? cfError() : parseListPage(htmlPop, 'populer', pPop);
      if (!result.error) {
        return NextResponse.json(result, { headers: { ...corsHeaders, 'Cache-Control': 's-maxage=21600, stale-while-revalidate=86400' } });
      }
      return NextResponse.json(result, { headers: corsHeaders });
    }

    // ── SEARCH ──
    if (action === 'search') {
      const q       = searchParams.get('q') || '';
      const pSearch = parseInt(searchParams.get('page') || '1', 10);
      if (!q) return NextResponse.json({ error: 'q required' }, { status: 400, headers: corsHeaders });
      const htmlSearch = await anichinFetch(`${ANICHIN_BASE}/page/${pSearch}/?s=${encodeURIComponent(q)}`);
      if (!htmlSearch) return NextResponse.json(cfError(), { headers: corsHeaders });
      const result = parseListPage(htmlSearch, 'search', pSearch);
      result.query = q;
      if (!result.error) {
        return NextResponse.json(result, { headers: { ...corsHeaders, 'Cache-Control': 's-maxage=21600, stale-while-revalidate=86400' } });
      }
      return NextResponse.json(result, { headers: corsHeaders });
    }

    // ── DETAIL ──
    if (action === 'detail') {
      const slugDetail  = searchParams.get('slug') || '';
      if (!slugDetail) return NextResponse.json({ error: 'slug required' }, { status: 400, headers: corsHeaders });
      const htmlDetail  = await anichinFetch(`${ANICHIN_BASE}/donghua/${slugDetail}/`);
      if (!htmlDetail)  return NextResponse.json(cfError(), { headers: corsHeaders });

      const $    = cheerio.load(htmlDetail);
      const data = { detailPath: slugDetail, country: 'China', type: 'donghua', subtitles: 'Indonesia', genre: [], episodes: [] };
      data.title  = $('.entry-title').text().trim();
      data.poster = $('.thumbook img').attr('src') || '';
      data.rating = $('.rating strong').text().trim() || $('.num').first().text().trim();

      $('.spe span').each((i, el) => {
        const text  = $(el).text().trim();
        const lower = text.toLowerCase();
        if (lower.includes('durasi') || lower.includes('duration')) {
          data.duration = text.replace(/^(Durasi|Duration)\s*:?\s*/i, '').trim();
        } else if (lower.includes('dirilis') || lower.includes('released') || lower.includes('rilis')) {
          data.releaseDate = text.replace(/^(Dirilis|Released|Rilis|Tanggal Rilis)\s*:?\s*/i, '').trim();
          const m = data.releaseDate.match(/(\d{4})/);
          if (m) data.year = m[1];
        } else if (lower.includes('negara') || lower.includes('country')) {
          data.country = text.replace(/^(Negara|Country)\s*:?\s*/i, '').trim() || 'China';
        }
      });

      $('.genxed a').each((i, el) => data.genre.push($(el).text().trim()));
      data.description = $('.bixbox.synp .entry-content').text().trim() || $('.entry-content').first().text().trim();

      $('.eplister li').each((i, el) => {
        const a     = $(el).find('a').first();
        if (!a.length) return;
        const playUrl = a.attr('href').replace(/\/$/, '').split('/').pop();
        const episode = $(el).find('.epl-num').text().trim() || ($(el).text().match(/episode\s*(\d+)/i) || [])[1] || '';
        const title   = $(el).find('.epl-title').text().trim() || '';
        data.episodes.push({ playUrl, episode, title });
      });

      return NextResponse.json(
        { data },
        { headers: { ...corsHeaders, 'Cache-Control': 's-maxage=43200, stale-while-revalidate=86400' } }
      );
    }

    // ── PLAY ──
    if (action === 'play') {
      const epSlug   = searchParams.get('ep') || '';
      if (!epSlug)   return NextResponse.json({ error: 'ep required' }, { status: 400, headers: corsHeaders });
      const htmlPlay = await anichinFetch(`${ANICHIN_BASE}/${epSlug}/`);
      if (!htmlPlay) return NextResponse.json(cfError(), { headers: corsHeaders });

      const $ = cheerio.load(htmlPlay);
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
        const m = htmlPlay.match(/(?:src|url|file)\s*[:=]\s*["']([^"']+(?:embed|player|video)[^"']*)['"]/i);
        if (m) playerUrl = m[1];
      }

      return NextResponse.json(
        { data: { playerUrl } },
        { headers: { ...corsHeaders, 'Cache-Control': 's-maxage=2592000, stale-while-revalidate=86400' } }
      );
    }

    return NextResponse.json({ error: 'action tidak dikenal: ' + action }, { status: 400, headers: corsHeaders });

  } catch (error) {
    return NextResponse.json({ error: 'Internal Server Error', detail: error.message }, { status: 500, headers: corsHeaders });
  }
}

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 200,
    headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, OPTIONS' },
  });
}
