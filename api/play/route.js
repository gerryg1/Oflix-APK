import { NextResponse } from 'next/server';
import { mbFetch, transformStream } from '@/lib/moviebox';

export const runtime = 'edge';

export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const subjectId = searchParams.get('subjectId') || '';
  const se = searchParams.get('se') || '0';
  const ep = searchParams.get('ep') || '0';
  const detailPath = searchParams.get('detailPath') || '';

  if (!subjectId) {
    return NextResponse.json({ success: false, error: 'No subjectId' }, { status: 400 });
  }

  // --- Check if it's a Custom VPS Movie stream ---
  if (subjectId.startsWith('custom_')) {
    try {
      const customRes = await fetch('https://raw.githubusercontent.com/username/repo/main/movies.json'); // Adjust if custom API is used
      if (customRes.ok) {
        const custom = await customRes.json();
        const customId = subjectId.replace('custom_', '');
        const movie = custom.find(c => c.id === customId);
        if (movie) {
          let videoUrl = movie.video_url;
          if (movie.type === 'series') {
            const snum = parseInt(se || '1');
            const epnum = parseInt(ep || '1');
            const sData = movie.seasons?.find(s => s.season === snum);
            const eData = sData?.episodes?.find(e => e.episode === epnum);
            if (eData && eData.video_url) videoUrl = eData.video_url;
          }
          return NextResponse.json({
            success: true,
            url: videoUrl,
            downloads: [{ url: videoUrl, hlsUrl: videoUrl, resolution: 1080 }],
            captions: movie.captions || [],
            source: 'vps-custom'
          });
        }
      }
    } catch (e) {
      console.warn('Failed custom fetch', e);
    }
  }

  const qs = new URLSearchParams({ 
    id: subjectId, 
    subjectId, 
    se, 
    ep,
    format: 'MP4' 
  }).toString();

  // Try aoneroom.com first (primary)
  try {
    const playHeaders = {
      'Referer': detailPath
        ? `https://themoviebox.org/movies/${detailPath}?id=${subjectId}&type=/movie/detail&detailSe=&detailEp=&lang=en`
        : 'https://themoviebox.org/',
      'x-client-info': '{"timezone":"Asia/Bangkok"}',
      'sec-fetch-dest': 'empty',
      'sec-fetch-mode': 'cors',
      'sec-fetch-site': 'same-origin',
    };
    
    const API_BASE = 'https://h5-api.aoneroom.com/wefeed-h5api-bff';
    const playRes = await mbFetch(API_BASE + '/subject/play?' + qs, { headers: playHeaders });
    const dlRes = await mbFetch(API_BASE + '/subject/download?' + qs, { headers: playHeaders });

    const playData = playRes.json?.code === 0 ? playRes.json.data : null;
    const dlData = dlRes.json?.code === 0 ? dlRes.json.data : null;

    const result = transformStream(playData, dlData, { subjectId, se, ep, playRaw: playRes.raw, dlRaw: dlRes.raw });
    if (result.success) {
      return NextResponse.json(result);
    }
  } catch (err) {
    console.warn('Aoneroom play fetch failed, attempting fallback', err);
  }

  // Fallback to netnaija.film
  try {
    const playHeadersFallback = {
      'Referer': detailPath
        ? `https://netnaija.film/spa/videoPlayPage/movies/${detailPath}?id=${subjectId}&type=/movie/detail&detailSe=&detailEp=&lang=en`
        : 'https://netnaija.film/',
      'x-client-info': '{"timezone":"Asia/Bangkok"}',
      'X-Source': 'web',
      'sec-fetch-dest': 'empty',
      'sec-fetch-mode': 'cors',
      'sec-fetch-site': 'same-origin',
    };
    
    const API_BASE_FALLBACK = 'https://netnaija.film/wefeed-h5api-bff';
    const playRes = await mbFetch(API_BASE_FALLBACK + '/subject/play?' + qs, { headers: playHeadersFallback });
    const dlRes = await mbFetch(API_BASE_FALLBACK + '/subject/download?' + qs, { headers: playHeadersFallback });

    const playData = playRes.json?.code === 0 ? playRes.json.data : null;
    const dlData = dlRes.json?.code === 0 ? dlRes.json.data : null;

    const result = transformStream(playData, dlData, { subjectId, se, ep, playRaw: playRes.raw, dlRaw: dlRes.raw });
    return NextResponse.json(result);
  } catch (err) {
    return NextResponse.json({ success: false, error: err.message }, { status: 500 });
  }
}

export async function OPTIONS() {
  return new NextResponse(null, { status: 200 });
}
