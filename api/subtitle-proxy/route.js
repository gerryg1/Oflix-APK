/**
 * app/api/subtitle-proxy/route.js — Fetch & convert SRT → VTT
 */

import { NextResponse } from 'next/server';

export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const srtUrl = searchParams.get('url') || '';

  const vttHeaders = {
    'Content-Type': 'text/vtt; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
  };

  if (!srtUrl) {
    return new NextResponse('WEBVTT\n\n', { status: 200, headers: vttHeaders });
  }

  try {
    const response = await fetch(srtUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0',
        'Referer': 'https://foodcash.com.br/',
        'Origin': 'https://foodcash.com.br',
      },
      next: { revalidate: 0 },
    });

    if (!response.ok) {
      return new NextResponse('WEBVTT\n\n', { status: 200, headers: vttHeaders });
    }

    const srtContent = await response.text();

    // Convert SRT → VTT
    let vtt = srtContent.replace(/\r\n|\r/g, '\n').trim();
    if (!vtt.startsWith('WEBVTT')) {
      vtt = 'WEBVTT\n\n' + vtt.replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2');
    }

    return new NextResponse(vtt, {
      status: 200,
      headers: {
        ...vttHeaders,
        'Cache-Control': 's-maxage=21600, stale-while-revalidate=86400',
      },
    });

  } catch {
    return new NextResponse('WEBVTT\n\n', { status: 200, headers: vttHeaders });
  }
}

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 200,
    headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, OPTIONS' },
  });
}
