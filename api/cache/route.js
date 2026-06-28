import { NextResponse } from 'next/server';
import { mbFetch, resolveMovieBoxJson, transformItem, transformDetail } from '@/lib/moviebox';

export const runtime = 'edge';

const H5_BASE = 'https://h5-api.aoneroom.com/wefeed-h5api-bff';

const RANKING_LISTS = {
  'trending': '872031290915189720',
  'film': '8821254238245470240',
  'indonesian-movies': '6528093688173053896',
  'indonesian-drama': '5283462032510044280',
  'kdrama': '4380734070238626200',
  'anime': '8617025562613270856',
  'western-tv': '1469286917119311888',
  'short-tv': '8624142774394406504',
  'horror': '5848753831881965888',
  'thailand-drama': '1164329479448281992',
};

// Extract items from home response's operatingList structure
function extractHomeItems(data) {
  const items = [];

  // Try operatingList (new structure)
  if (data.operatingList && Array.isArray(data.operatingList)) {
    for (const section of data.operatingList) {
      // Each section may have subjects array
      if (section.subjects && Array.isArray(section.subjects)) {
        items.push(...section.subjects);
      }
      // Or subjectList
      if (section.subjectList && Array.isArray(section.subjectList)) {
        items.push(...section.subjectList);
      }
      // Banner items might contain subjects too
      if (section.banner?.items && Array.isArray(section.banner.items)) {
        for (const bannerItem of section.banner.items) {
          if (bannerItem.subject) items.push(bannerItem.subject);
          if (bannerItem.subjects) items.push(...bannerItem.subjects);
        }
      }
    }
  }

  // Try topList (if exists)
  if (data.topList && Array.isArray(data.topList)) {
    items.push(...data.topList);
  }

  // Try topPicList
  if (data.topPicList && Array.isArray(data.topPicList)) {
    items.push(...data.topPicList);
  }

  // Fallback: flat list or data.list or data.subjectList or data.items
  if (items.length === 0 && data.list) {
    if (Array.isArray(data.list)) items.push(...data.list);
  }
  if (items.length === 0 && data.subjectList) {
    if (Array.isArray(data.subjectList)) items.push(...data.subjectList);
  }
  if (items.length === 0 && data.items) {
    if (Array.isArray(data.items)) items.push(...data.items);
  }
  if (items.length === 0 && Array.isArray(data)) {
    items.push(...data);
  }

  return items;
}

export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get('action') || '';
  const page = searchParams.get('page') || 1;

  if (!action) {
    return NextResponse.json({ success: false, error: 'action required' }, { status: 400 });
  }

  try {
    let output;

    if (action === 'detail') {
      const detailPath = searchParams.get('detailPath') || '';

      // Direct, simple fetch matching user's working URL
      const url = `https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?detailPath=${encodeURIComponent(detailPath)}`;
      const res = await mbFetch(url);

      if (res.json && res.json.code === 0 && res.json.data) {
        output = { success: true, data: transformDetail({ resData: res.json.data }) };
      } else {
        // Fallback to netnaija mirror just in case
        const fallbackUrl = `https://netnaija.film/wefeed-h5api-bff/detail?detailPath=${encodeURIComponent(detailPath)}`;
        const fbRes = await mbFetch(fallbackUrl);
        if (fbRes.json && fbRes.json.code === 0 && fbRes.json.data) {
          output = { success: true, data: transformDetail({ resData: fbRes.json.data }) };
        } else {
          return NextResponse.json({
            success: false,
            error: 'Detail not found',
            _debug: {
              primaryUrl: url,
              primaryStatus: res.status,
              primaryIsJson: !!res.json,
              primaryRaw: (res.raw || '').slice(0, 150),
              fallbackUrl: fallbackUrl,
              fallbackStatus: fbRes.status
            }
          });
        }
      }
    } else {
      // Home or Search
      let url = '';
      let fetchOptions = {};
      if (action === 'search') {
        url = 'https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search';
        fetchOptions = {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            keyword: searchParams.get('q') || '',
            page: parseInt(page || '1', 10),
            perPage: 18,
          }),
        };
      } else {
        const rankingId = RANKING_LISTS[action];
        if (rankingId) {
          url = `${H5_BASE}/ranking-list/content?id=${rankingId}&page=${page}&perPage=12`;
        } else {
          // Home
          url = H5_BASE + '/web/home';
        }
      }

      const { json, raw, status } = await mbFetch(url, fetchOptions);
      let items = [];

      if (json && json.code === 0 && json.data) {
        const rawItems = extractHomeItems(json.data);
        items = rawItems.filter(i => i && (i.title || i.subject?.title || i.subjectId || i.detailPath)).map(transformItem);
      }

      if (items.length === 0) {
        output = { success: true, items: [], _debug: { status, jsonCode: json?.code, dataKeys: json?.data ? Object.keys(json.data) : null, raw: (raw || '').slice(0, 500) } };
      } else {
        output = { success: true, items };
      }
    }

    const cacheTTL = action === 'detail' ? 43200 : 3600;
    return NextResponse.json(output, {
      headers: {
        'Cache-Control': `s-maxage=${cacheTTL}, stale-while-revalidate=86400`,
      },
    });
  } catch (err) {
    return NextResponse.json({ success: false, error: err.message, stack: err.stack?.slice(0, 200) }, { status: 500 });
  }
}

export async function OPTIONS() {
  return new NextResponse(null, { status: 200 });
}
