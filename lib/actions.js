'use server';

import { GET as cacheGET } from '@/app/api/cache/route';
import { GET as komikGET } from '@/app/api/komik/route';
import { GET as donghuaGET } from '@/app/api/donghua/route';
import { GET as playGET } from '@/app/api/play/route';

async function callRouteGET(routeHandler, urlString) {
  try {
    const req = new Request(urlString);
    const res = await routeHandler(req);
    return await res.json();
  } catch (e) {
    return { success: false, error: e.message };
  }
}

export async function fetchCategoryAction(action, page = 1) {
  return callRouteGET(cacheGET, `http://localhost/api/cache?action=${encodeURIComponent(action)}&page=${page}`);
}

export async function fetchDetailAction(detailPath) {
  return callRouteGET(cacheGET, `http://localhost/api/cache?action=detail&detailPath=${encodeURIComponent(detailPath)}`);
}

export async function fetchSearchAction(q, page = 1) {
  return callRouteGET(cacheGET, `http://localhost/api/cache?action=search&q=${encodeURIComponent(q)}&page=${page}`);
}

export async function fetchKomikPopulerAction(page = 1) {
  return callRouteGET(komikGET, `http://localhost/api/komik?action=populer&page=${page}`);
}

export async function fetchKomikDetailAction(slug) {
  return callRouteGET(komikGET, `http://localhost/api/komik?action=detail&detailManga=${encodeURIComponent(slug)}`);
}

export async function fetchKomikSearchAction(q) {
  return callRouteGET(komikGET, `http://localhost/api/komik?action=search&q=${encodeURIComponent(q)}`);
}

export async function fetchDonghuaSearchAction(q) {
  return callRouteGET(donghuaGET, `http://localhost/api/donghua?action=search&q=${encodeURIComponent(q)}`);
}

export async function fetchDonghuaDetailAction(slug) {
  return callRouteGET(donghuaGET, `http://localhost/api/donghua?action=detail&slug=${encodeURIComponent(slug)}`);
}

export async function fetchDonghuaPlayAction(ep) {
  return callRouteGET(donghuaGET, `http://localhost/api/donghua?action=play&ep=${encodeURIComponent(ep)}`);
}

export async function fetchDonghuaPopulerAction(page = 1) {
  return callRouteGET(donghuaGET, `http://localhost/api/donghua?action=populer&page=${page}`);
}

export async function fetchKomikBacaAction(bacaSlug) {
  return callRouteGET(komikGET, `http://localhost/api/komik?action=baca&bacaManga=${encodeURIComponent(bacaSlug)}`);
}

export async function fetchPlayAction(id, season, episode, detailPath) {
  try {
    const params = new URLSearchParams({ subjectId: id, detailPath: detailPath || '' });
    if (season)  params.set('se', season);
    if (episode) params.set('ep', episode);
    const req = new Request(`http://localhost/api/play?${params.toString()}`);
    const res = await playGET(req);
    return await res.json();
  } catch (e) {
    return { success: false, error: e.message };
  }
}
