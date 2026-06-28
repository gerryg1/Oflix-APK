const MB_HOST = 'https://netnaija.film';
const HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36',
  'Accept': 'application/json',
  'Accept-Language': 'id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7',
  'X-Client-Info': '{"timezone":"Asia/Bangkok"}',
  'X-Source': 'web',
  'Referer': MB_HOST + '/',
  'Origin': MB_HOST,
};

let _tokenCache = '';
let _tokenTime = 0;
const TOKEN_TTL = 30 * 60 * 1000;

function collectAllCookies(resp) {
  const setCookies = resp.headers.getSetCookie ? resp.headers.getSetCookie() : [];
  if (!setCookies || setCookies.length === 0) {
    const fallback = resp.headers.get('set-cookie');
    if (fallback) setCookies.push(...fallback.split(/,(?=\s*\w+=)/));
  }
  return setCookies.map(c => c.split(';')[0]).join('; ');
}

function mergeCookieStr(c1, c2) {
  const map = new Map();
  [c1, c2].forEach(s => {
    if (!s) return;
    s.split(';').forEach(pair => {
      const parts = pair.trim().split('=');
      if (parts[0]) map.set(parts[0], parts.slice(1).join('='));
    });
  });
  return Array.from(map.entries()).map(([k, v]) => `${k}=${v}`).join('; ');
}

export async function ensureCookies(forceRefresh = false) {
  if (!forceRefresh && _tokenCache && (Date.now() - _tokenTime < TOKEN_TTL)) {
    return _tokenCache;
  }
  try {
    // Step 1: Visit official movieboxapp.in home
    const resp = await fetch('https://movieboxapp.in/', {
      headers: {
        ...HEADERS,
        'Accept': 'text/html,application/xhtml+xml',
        'Accept-Language': 'en-US,en;q=0.9',
        'Referer': 'https://movieboxapp.in/',
        'Origin': 'https://movieboxapp.in',
      },
    });
    let cookies = collectAllCookies(resp);

    // Step 2: Try the official App Pkgs endpoint on h5-api.aoneroom.com
    if (!cookies.includes('token=')) {
      const resp2 = await fetch('https://h5-api.aoneroom.com/wefeed-h5api-bff/app/get-latest-app-pkgs?app_name=moviebox', {
        headers: {
          ...HEADERS,
          Cookie: cookies,
          'Referer': 'https://movieboxapp.in/',
          'Origin': 'https://movieboxapp.in',
        },
      });
      cookies = mergeCookieStr(cookies, collectAllCookies(resp2));
    }

    // Step 3: Try netnaija.film
    if (!cookies.includes('token=')) {
      const resp3 = await fetch('https://netnaija.film/', {
        headers: { ...HEADERS, 'Accept': 'text/html' },
      });
      cookies = mergeCookieStr(cookies, collectAllCookies(resp3));
    }

    // Step 4: Try netnaija.film videoPlayPage
    if (!cookies.includes('token=')) {
      const resp4 = await fetch('https://netnaija.film/spa/videoPlayPage', {
        headers: { ...HEADERS, 'Accept': 'text/html' },
      });
      cookies = mergeCookieStr(cookies, collectAllCookies(resp4));
    }

    if (!cookies.includes('i18n_lang=')) {
      cookies = cookies ? cookies + '; i18n_lang=id' : 'i18n_lang=id';
    }
    
    if (cookies.includes('token=')) {
      _tokenCache = cookies;
      _tokenTime = Date.now();
    }
    return cookies || _tokenCache || 'i18n_lang=id';
  } catch (e) {
    return _tokenCache || 'i18n_lang=id';
  }
}

export async function mbFetch(url, options = {}, cookiesOverride = null) {
  const cookies = cookiesOverride || await ensureCookies();
  const init = {
    ...options,
    headers: { ...HEADERS, ...options.headers, 'Cookie': cookies },
  };

  const tokenMatch = cookies.match(/token=([^;]+)/);
  if (tokenMatch && tokenMatch[1]) {
    init.headers['Authorization'] = `Bearer ${tokenMatch[1]}`;
  }

  const res = await fetch(url, init);
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}

  if (json && (json.code === 401 || json.code === 429 || json.code === 400 || (json.message && json.message.includes('token')))) {
    if (!options._retried) {
      const freshCookies = await ensureCookies(true);
      return mbFetch(url, { ...options, _retried: true }, freshCookies);
    }
  }
  return { json, raw: text, status: res.status };
}

// Transform helpers
const IMG_PROXY = 'https://funny-kitten-ad51d6.netlify.app/img';

export function optimizePoster(url, thumbnail) {
  if (!url && !thumbnail) return '';
  const src = url || thumbnail;
  return `${IMG_PROXY}?url=${encodeURIComponent(src)}&w=400&q=70`;
}

export function transformItem(item) {
  const sub = item.subject || item;
  const cover = sub.cover?.url || item.image?.url || '';
  const thumb = sub.cover?.thumbnail || item.image?.thumbnail || '';
  return {
    title: sub.title || item.title || '',
    poster: optimizePoster(cover, thumb),
    detailPath: sub.detailPath || item.detailPath || '',
    year: (sub.releaseDate || '').slice(0, 4),
    rating: String(sub.imdbRatingValue || sub.imdbRate || ''),
    genre: typeof sub.genre === 'string' ? sub.genre.split(',').map(s => s.trim()) : (sub.genre || ''),
    type: (sub.subjectType || item.subjectType) === 2 ? 'series' : 'film',
    country: sub.countryName || '',
    duration: sub.duration || '',
    subjectId: sub.subjectId || item.subjectId || '',
  };
}

export function resolveMovieBoxJson(data) {
  if (!Array.isArray(data)) return null;
  function resolveValue(val) {
    if (Array.isArray(val)) return val.map(v => (typeof v === 'number' && data[v] !== undefined ? resolveValue(data[v]) : resolveValue(v)));
    if (val && typeof val === 'object') {
      const result = {};
      for (const [k, v] of Object.entries(val)) {
        result[k] = typeof v === 'number' && data[v] !== undefined ? resolveValue(data[v]) : resolveValue(v);
      }
      return result;
    }
    return val;
  }
  for (const entry of data) {
    if (entry && typeof entry === 'object' && !Array.isArray(entry)) {
      const keys = Object.keys(entry);
      if (keys.some(k => k.startsWith('$s'))) {
        const resolved = {};
        for (const [k, v] of Object.entries(entry)) {
          const cleanKey = k.startsWith('$s') ? k.slice(2) : k;
          const val = typeof v === 'number' && data[v] !== undefined ? data[v] : v;
          resolved[cleanKey] = resolveValue(val);
        }
        return resolved;
      }
    }
  }
  return null;
}

export function transformDetail(mbData) {
  const rd = mbData.resData || {};
  const subject = rd.subject || {};
  const meta = rd.metadata || {};
  const stars = rd.stars || [];
  const res = rd.resource || {};

  let genre = subject.genre || '';
  if (typeof genre === 'string') genre = genre.split(',').map(s => s.trim());

  const cast = stars.map(s => ({
    name: s.name || '',
    character: s.character || '',
    avatar: s.avatarUrl ? `${IMG_PROXY}?url=${encodeURIComponent(s.avatarUrl)}&w=120&q=60` : '',
  }));

  const rawSeasons = (res.seasons || []).map(se => {
    const eps = [];
    for (let e = 1; e <= (se.maxEp || 0); e++) {
      const pUrl = `${MB_HOST}/play?id=${subject.subjectId || ''}&season=${se.se || 1}&episode=${e}&detailPath=${subject.detailPath || ''}`;
      eps.push({ episode: e, title: 'Episode ' + e, playerUrl: pUrl, url: pUrl, thumbnail: '', duration: '' });
    }
    return { season: se.se || 1, episodes: eps };
  });

  // Filter empty seasons - if no real episodes, treat as movie
  const seasons = rawSeasons.filter(s => s.episodes.length > 0);
  const totalEps = seasons.reduce((n, s) => n + s.episodes.length, 0);
  const isMovie = totalEps === 0 || subject.subjectType === 1;

  const playerUrl = isMovie
    ? `${MB_HOST}/play?id=${subject.subjectId || ''}&season=0&episode=0&detailPath=${subject.detailPath || ''}`
    : '';

  const cover = subject.cover?.url || subject.cover?.thumbnail || meta.image || '';

  // Trailer: can be string, object with videoAddress, or null
  let trailerUrl = '';
  const trailer = subject.trailer;
  if (typeof trailer === 'string' && trailer) {
    trailerUrl = trailer;
  } else if (trailer && typeof trailer === 'object') {
    trailerUrl = trailer.videoAddress?.url || trailer.url || '';
  }

  return {
    id: subject.subjectId || '',
    title: subject.title || meta.title || '',
    poster: optimizePoster(cover, ''),
    banner: subject.banner?.url || subject.cover?.url || '',
    rating: String(subject.imdbRatingValue || subject.imdbRate || ''),
    year: (subject.releaseDate || '').slice(0, 4),
    duration: subject.duration || '',
    type: isMovie ? 'film' : 'series',
    description: subject.brief || subject.description || meta.description || '',
    genre,
    country: subject.countryName || '',
    quality: meta.quality || 'HD',
    cast,
    trailerUrl,
    playerUrl,
    sources: playerUrl ? [{ url: playerUrl }] : [],
    seasons: isMovie ? [] : seasons,
    subjectId: subject.subjectId || '',
    captions: (res.captions || []).map(c => ({ url: c.url, lan: c.lan || c.language, language: c.lanName || c.language })),
  };
}

export function transformStream(playData, dlData, debug = {}) {
  const videoProxy = 'https://uid5558280582469143984atp3ext1774623069exp1782.eyjhbgcioijiuzi1niisinr5cci6ikpxvcj9eyj1awqiojy1nta3mda1mta1mz.workers.dev/?url=';
  const downloads = [];

  // Helper: proxy MP4 URLs but leave m3u8/HLS URLs direct
  function proxyUrl(rawUrl) {
    if (!rawUrl) return '';
    if (rawUrl.includes('.m3u8') || rawUrl.includes('/playstream')) return rawUrl;
    return videoProxy + encodeURIComponent(rawUrl);
  }

  // From download endpoint
  if (dlData?.downloads) {
    for (const dl of dlData.downloads) {
      if (dl.url) {
        const dlRes = parseInt(dl.resolution) || 0;
        downloads.push({
          url: proxyUrl(dl.url),
          resolution: dlRes,
          label: dlRes ? `${dlRes}p` : 'Auto',
        });
      }
    }
  }

  // From play endpoint streams
  if (playData?.streams) {
    for (const st of playData.streams) {
      if (st.url) {
        const stRes = parseInt(st.resolutions || st.resolution) || 0;
        downloads.push({
          url: proxyUrl(st.url),
          resolution: stRes,
          label: stRes ? `${stRes}p` : 'Auto',
        });
      }
    }
  }

  // Fallback to old list field just in case
  if (dlData?.list) {
    for (const d of dlData.list) {
      if (!d.url && !d.path) continue;
      const resVal = parseInt(d.quality?.replace(/[^0-9]/g, '') || '0', 10);
      downloads.push({
        url: proxyUrl(d.url || d.path),
        hlsUrl: '',
        resolution: resVal,
        label: d.quality || (resVal ? `${resVal}p` : 'Auto'),
      });
    }
  }

  // Dedup by resolution (keep first of each)
  const seen = new Set();
  const uniqueDownloads = [];
  for (const d of downloads) {
    const key = d.resolution;
    if (!seen.has(key)) {
      seen.add(key);
      uniqueDownloads.push(d);
    }
  }
  uniqueDownloads.sort((a, b) => (b.resolution || 0) - (a.resolution || 0));

  let mainUrl = uniqueDownloads[0]?.url || '';

  // Check HLS from MovieBox — use DIRECT (no proxy) to avoid 429 and broken segments
  if (playData?.hls?.length) {
    const hlsUrl = playData.hls[0]?.url || playData.hls[0];
    if (typeof hlsUrl === 'string' && hlsUrl) mainUrl = hlsUrl;
  }

  // Captions from download endpoint
  const captions = [];
  if (dlData?.captions) {
    for (const cap of dlData.captions) {
      if (cap.url) {
        captions.push({
          url: cap.url,
          languageCode: cap.lan || '',
          lan: cap.lan || '',
          language: cap.lanName || ''
        });
      }
    }
  }

  const hasContent = !!(mainUrl || uniqueDownloads.length > 0);
  return {
    success: hasContent,
    url: mainUrl,
    downloads: uniqueDownloads,
    captions,
    source: 'moviebox-worker',
    ...(hasContent ? {} : { error: 'No streams available', _debug: debug }),
  };
}
