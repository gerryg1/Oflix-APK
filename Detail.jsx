'use client';

import { useEffect, useState, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { fetchDetail, fetchStream, parseFoodcashUrl } from '@/lib/api';
import { useAuth } from '@/context/AuthContext';
import VideoPlayer from '@/components/VideoPlayer';
import { useMobile } from '@/hooks/useMobile';
import MobileDetail from '@/components/MobileDetail';
import { useDetailModal } from '@/context/DetailModalContext';

function toArr(v) {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  if (typeof v === 'string') return v.split(',').map(s=>s.trim()).filter(Boolean);
  return [];
}



export default function DetailPage({ detailPathOverride }) {
  const isMobile = useMobile();
  const params   = useSearchParams();
  const rawPath  = detailPathOverride || params.get('d') || params.get('p') || '';
  const router = useRouter();

  const { closeDetail } = useDetailModal() || {};
  function handleClose() {
    if (detailPathOverride && closeDetail) {
      closeDetail();
    } else {
      if (typeof window !== 'undefined' && window.history.length > 1) {
        router.back();
      } else {
        router.push('/');
      }
    }
  }

  const [localPath, setLocalPath] = useState(rawPath);

  useEffect(() => {
    setLocalPath(rawPath);
  }, [rawPath]);

  // Parse localPath to check if it contains play information
  let detailPath = localPath;
  let playSeason = 0; // 0-indexed season idx
  let playEpisode = -1; // 0-indexed ep idx (-1 means not episode, or movie)
  let isPlayActive = false;

  if (localPath.includes('/play-se')) {
    const parts = localPath.split('/play-se');
    detailPath = parts[0];
    isPlayActive = true;
    
    // parts[1] is like "1-ep3" or "1-eps3"
    const playMatch = parts[1].match(/^(\d+)-(?:ep|eps)(\d+)/);
    if (playMatch) {
      playSeason = parseInt(playMatch[1], 10) - 1;
      playEpisode = parseInt(playMatch[2], 10) - 1;
    }
  } else if (localPath.endsWith('/play') || localPath.includes('/play?')) {
    const parts = localPath.split('/play');
    detailPath = parts[0];
    isPlayActive = true;
  }

  const { user, saveCW, getSavedProgress, addToWatchlist, removeFromWatchlist, isInWatchlist, addHistory, setLike, getLike } = useAuth();

  const [data, setData]             = useState(null);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [playerData, setPlayerData] = useState(null);
  const [playerLoading, setPlayerLoading] = useState(false);
  const [currentSeason, setCurrentSeason] = useState(0);
  const [currentEp, setCurrentEp]   = useState(-1);
  const [liked, setLiked]           = useState(false);
  const [disliked, setDisliked]     = useState(false);
  const [inList, setInList]         = useState(false);
  const [showTrailer, setShowTrailer] = useState(false);
  const [trailerMuted, setTrailerMuted] = useState(true);
  const trailerTimerRef = useRef(null);
  const trailerVideoRef = useRef(null);

  const BATCH_SIZE = 100;
  const [activeBatch, setActiveBatch] = useState(0);

  useEffect(() => {
    if (currentEp >= 0) {
      setActiveBatch(Math.floor(currentEp / BATCH_SIZE));
    } else {
      setActiveBatch(0);
    }
  }, [currentEp, currentSeason]);

  useEffect(() => {
    if (!detailPath) return;
    setLoading(true); setError('');
    setShowTrailer(false); setTrailerMuted(true);
    clearTimeout(trailerTimerRef.current);
    
    fetchDetail(detailPath)
      .then(res => {
        if (res.success && res.data) {
          setData(res.data);
          if (res.data.trailerUrl) {
            trailerTimerRef.current = setTimeout(() => setShowTrailer(true), 4000);
          }
          addHistory(detailPath, res.data.title || '', res.data.poster || '', res.data.seasons?.length ? 'series' : 'film');
        }
        else setError(JSON.stringify(res).slice(0, 200));
        setLoading(false);
      })
      .catch(e => { setError(e.message); setLoading(false); });

    getLike(detailPath).then(action => {
      setLiked(action === 'like');
      setDisliked(action === 'dislike');
    }).catch(() => {});
    setInList(isInWatchlist(detailPath));
    return () => clearTimeout(trailerTimerRef.current);
  }, [detailPath]);

  useEffect(() => {
    if (trailerVideoRef.current) trailerVideoRef.current.muted = trailerMuted;
  }, [trailerMuted, showTrailer]);

  // Declarative effect to load stream whenever play state or episode changes
  useEffect(() => {
    if (!data) return;
    if (!isPlayActive) {
      setPlayerData(null);
      setPlayerLoading(false);
      return;
    }

    let active = true;
    setPlayerLoading(true);

    async function loadStream() {
      try {
        const isMovie  = !data.seasons?.length;
        const sIdx = playSeason >= 0 ? playSeason : 0;
        const epIdx = playEpisode >= 0 ? playEpisode : -1;

        const sourceUrl = isMovie
          ? (data.playerUrl || data.sources?.[0]?.url || '')
          : (data.seasons?.[sIdx]?.episodes?.[epIdx]?.playerUrl || data.seasons?.[sIdx]?.episodes?.[epIdx]?.url || '');
        
        if (!sourceUrl) {
          if (active) {
            setPlayerLoading(false);
            setError('Link streaming tidak ditemukan.');
          }
          return;
        }

        const parsed = parseFoodcashUrl(sourceUrl);
        if (!parsed.id) {
          if (active) {
            setPlayerLoading(false);
            setError('Gagal memproses ID streaming.');
          }
          return;
        }

        const seasonVal  = isMovie ? '' : (data.seasons?.[sIdx]?.season || sIdx + 1);
        const episodeVal = isMovie ? '' : (data.seasons?.[sIdx]?.episodes?.[epIdx]?.episode || epIdx + 1);
        
        const res = await fetchStream(parsed.id, seasonVal, episodeVal, detailPath);
        if (!res.success) {
          if (active) {
            setPlayerLoading(false);
            setError(res.error || 'Gagal memuat stream.');
          }
          return;
        }

        const downloads = [];
        if (res.downloads?.length) {
          const sorted = [...res.downloads].sort((a,b) => (Number(b.resolution)||0)-(Number(a.resolution)||0));
          sorted.forEach(d => {
            if (!d.url) return;
            const h = Number(d.resolution) || 0;
            let label = 'Auto';
            if (h >= 1080) label = `High ${h}p`;
            else if (h >= 480) label = `Medium ${h}p`;
            else if (h > 0) label = `Low ${h}p`;
            downloads.push({ label, url: d.url, hlsUrl: d.hlsUrl || '', resolution: h });
          });
        }

        let startDlIdx = 0;
        if (downloads.length > 1) {
          for (let i = downloads.length - 1; i >= 0; i--) {
            if (downloads[i].resolution && downloads[i].resolution >= 480) { startDlIdx = i; break; }
          }
        }

        const chosen = downloads[startDlIdx] || downloads[0];
        let finalUrl = '';
        if (res.url?.includes('.m3u8')) {
          finalUrl = res.url;
        } else {
          finalUrl = chosen?.url || res.url || '';
        }

        const subtitles = [];
        if (res.captions?.length) {
          const seen = new Set();
          [{ code:'in_id', label:'Indonesia' },{ code:'id', label:'Indonesia' },{ code:'en', label:'English' }]
          .forEach(({ code, label }) => {
            const cap = res.captions.find(c => c?.url && (c.languageCode===code || c.lan===code));
            if (!cap || seen.has(label)) return;
            seen.add(label);
            subtitles.push({ url: `/api/subtitle-proxy?url=${encodeURIComponent(cap.url)}`, name: label, language: code });
          });
        }

        const saved = getSavedProgress(detailPath, epIdx);
        
        if (active) {
          setCurrentSeason(sIdx);
          setCurrentEp(epIdx);
          setPlayerData({
            url: finalUrl,
            hlsCheckUrl: '',
            mp4Fallback: chosen?.url || '',
            downloads,
            startDlIdx,
            subtitles,
            savedTime: saved?.time || 0,
          });
          setPlayerLoading(false);
        }
      } catch (err) {
        console.error('[loadStream] error:', err);
        if (active) {
          setPlayerLoading(false);
          setError(err.message || 'Error loading stream.');
        }
      }
    }

    loadStream();

    return () => {
      active = false;
    };
  }, [data, isPlayActive, playSeason, playEpisode, detailPath]);

  function playVideo(epIdx = -1, sIdx = 0) {
    const isMov = !data?.seasons?.length;
    const subPath = isMov 
      ? `/play` 
      : `/play-se${sIdx + 1}-ep${epIdx + 1}`;
    const newD = `${detailPath}${subPath}`;

    if (detailPathOverride) {
      setLocalPath(newD);
      const search = new URLSearchParams(window.location.search);
      search.set('d', newD);
      const newUrl = window.location.pathname + '?' + search.toString();
      window.history.replaceState(null, '', newUrl);
    } else {
      const targetUrl = isMov 
        ? `/detail?d=${detailPath}/play` 
        : `/detail?d=${detailPath}/play-se${sIdx + 1}-ep${epIdx + 1}`;
      router.replace(targetUrl, { scroll: false });
    }
  }

  function handleBackFromPlayer() {
    setPlayerData(null);
    if (detailPathOverride) {
      setLocalPath(detailPath);
      const search = new URLSearchParams(window.location.search);
      search.set('d', detailPath);
      const newUrl = window.location.pathname + '?' + search.toString();
      window.history.replaceState(null, '', newUrl);
    } else {
      router.replace(`/detail?d=${detailPath}`, { scroll: false });
    }
  }

  function handleWatchBtn() {
    if (!data) return;
    const isMovie = !data.seasons?.length;
    if (isMovie) { playVideo(-1, 0); return; }
    const saved = getSavedProgress(detailPath);
    if (saved && saved.episode >= 0) playVideo(saved.episode, saved.seasonIdx || 0);
    else playVideo(0, 0);
  }

  function handleSaveCW(progress) {
    if (!data) return;
    saveCW({ title: data.title, detailPath, poster: data.poster || '', ...progress });
  }

  async function handlePreloadNext() {
    if (!data || !data.seasons?.length) return;
    const epIdx = currentEp + 1;
    const sIdx = currentSeason;
    const epData = data.seasons?.[sIdx]?.episodes?.[epIdx];
    if (!epData) return;

    try {
      const sourceUrl = epData.playerUrl || epData.url || '';
      if (!sourceUrl) return;
      const parsed = parseFoodcashUrl(sourceUrl);
      if (!parsed.id) return;

      const seasonVal  = data.seasons[sIdx]?.season || sIdx + 1;
      const episodeVal = epData.episode || epIdx + 1;
      
      const res = await fetchStream(parsed.id, seasonVal, episodeVal, detailPath);
      if (!res.success || !res.downloads?.length) return;
      
      const sorted = [...res.downloads].sort((a,b) => (Number(b.resolution)||0)-(Number(a.resolution)||0));
      let target = sorted[0];
      for (let i = sorted.length - 1; i >= 0; i--) {
        if (Number(sorted[i].resolution) >= 480) { target = sorted[i]; break; }
      }
      
      if (target?.hlsUrl) {
        fetch(target.hlsUrl).catch(() => {});
      }
    } catch(e) {
      console.error('[preload] ERROR:', e.message);
    }
  }

  function toggleLike() {
    const v = !liked; setLiked(v); if (v) setDisliked(false);
    setLike(detailPath, v ? 'like' : 'none', data?.title || '', data?.poster || '');
  }
  function toggleDislike() {
    const v = !disliked; setDisliked(v); if (v) setLiked(false);
    setLike(detailPath, v ? 'dislike' : 'none', data?.title || '', data?.poster || '');
  }
  function toggleList() {
    const v = !inList; setInList(v);
    if (v) addToWatchlist({ title: data?.title, detailPath, poster: data?.poster || '' });
    else removeFromWatchlist(detailPath);
  }

  if (isMobile) {
    return <MobileDetail />;
  }

  if (playerData) {
    const seasons = data?.seasons || [];
    const title   = data?.title || '';
    const epTitle = currentEp >= 0 && seasons[currentSeason]?.episodes?.[currentEp]
      ? `S${seasons[currentSeason].season||currentSeason+1} E${seasons[currentSeason].episodes[currentEp].episode||currentEp+1}` : '';
    return (
      <VideoPlayer
        url={playerData.url}
        hlsCheckUrl={playerData.hlsCheckUrl}
        mp4Fallback={playerData.mp4Fallback}
        title={epTitle ? `${title} · ${epTitle}` : title}
        downloads={playerData.downloads||[]}
        subtitles={playerData.subtitles}
        savedTime={playerData.savedTime}
        seasons={seasons} currentSeasonIdx={currentSeason} currentEpIdx={currentEp}
        onEpisodeChange={(si,ei) => playVideo(ei, si)}
        onPreloadNext={handlePreloadNext}
        onClose={handleBackFromPlayer}
        onSaveCW={handleSaveCW}
      />
    );
  }

  if (loading) {
    return (
      <div className="detail-page-container">
        <div className="detail-backdrop" style={{
          background: 'rgba(6, 7, 10, 0.45)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
          opacity: 1,
          pointerEvents: 'auto',
          cursor: 'pointer'
        }} onClick={handleClose} />
        
        <div className="detail-modal-window">
          <button className="detail-modal-close" onClick={handleClose} title="Tutup">
            <i className="fas fa-times" />
          </button>
          <div className="detail-page" style={{ paddingBottom: 40 }}>
            {/* Shimmering Hero Banner */}
            <div className="skeleton-shimmer" style={{ width: '100%', height: '48vh', minHeight: 320 }} />
            
            {/* Shimmering Content Section */}
            <div className="detail-content" style={{ marginTop: 24, padding: '0 40px' }}>
              {/* Title Placeholder */}
              <div className="skeleton-shimmer" style={{ width: '55%', height: 36, borderRadius: 8, marginBottom: 20 }} />
              
              {/* Meta Badges Placeholders */}
              <div style={{ display: 'flex', gap: 10, marginBottom: 24 }}>
                <div className="skeleton-shimmer" style={{ width: 70, height: 26, borderRadius: 6 }} />
                <div className="skeleton-shimmer" style={{ width: 55, height: 26, borderRadius: 6 }} />
                <div className="skeleton-shimmer" style={{ width: 110, height: 26, borderRadius: 6 }} />
              </div>
              
              {/* Buttons Placeholders */}
              <div style={{ display: 'flex', gap: 12, marginBottom: 30 }}>
                <div className="skeleton-shimmer" style={{ width: 180, height: 46, borderRadius: 8 }} />
                <div className="skeleton-shimmer" style={{ width: 46, height: 46, borderRadius: '50%' }} />
              </div>
              
              {/* Description Placeholders */}
              <div className="skeleton-shimmer" style={{ width: '92%', height: 16, borderRadius: 4, marginBottom: 12 }} />
              <div className="skeleton-shimmer" style={{ width: '88%', height: 16, borderRadius: 4, marginBottom: 12 }} />
              <div className="skeleton-shimmer" style={{ width: '60%', height: 16, borderRadius: 4, marginBottom: 36 }} />
              
              {/* Cast Placeholder */}
              <div style={{ marginBottom: 30 }}>
                <div className="skeleton-shimmer" style={{ width: 100, height: 20, borderRadius: 4, marginBottom: 16 }} />
                <div style={{ display: 'flex', gap: 20 }}>
                  {[1, 2, 3, 4, 5].map(i => (
                    <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                      <div className="skeleton-shimmer" style={{ width: 68, height: 68, borderRadius: '50%' }} />
                      <div className="skeleton-shimmer" style={{ width: 55, height: 12, borderRadius: 3 }} />
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (isPlayActive && !playerData) {
    const title = data?.title || 'Video';
    const epTitle = playEpisode >= 0 && data?.seasons?.[playSeason]?.episodes?.[playEpisode]
      ? `S${data.seasons[playSeason].season||playSeason+1} E${data.seasons[playSeason].episodes[playEpisode].episode||playEpisode+1}` : '';
    const fullTitle = epTitle ? `${title} · ${epTitle}` : title;

    return (
      <div className="player-overlay" style={{ background: '#050508', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', zIndex: 99999 }}>
        <style>{`
          @keyframes player-shimmer-anim {
            0% { background-position: -200% 0; }
            100% { background-position: 200% 0; }
          }
          .player-shimmer-bg {
            background: linear-gradient(90deg, #0f1017 25%, #1b1d28 50%, #0f1017 75%);
            background-size: 200% 100%;
            animation: player-shimmer-anim 1.6s infinite linear;
          }
          @keyframes core-progress-shimmer {
            0% { transform: translateX(-100%); }
            100% { transform: translateX(100%); }
          }
        `}</style>
        
        {/* Header Bar */}
        <div className="player-row-top" style={{ position: 'absolute', top: 0, left: 0, width: '100%', zIndex: 10, display: 'flex', alignItems: 'center', padding: '20px 24px' }}>
          <button className="pctrl-btn pctrl-back" style={{ background: 'none', border: 'none', color: '#fff', cursor: 'pointer', fontSize: 24, marginRight: 16 }} 
                  onClick={handleBackFromPlayer}>
            <i className="fas fa-arrow-left" />
          </button>
          <div className="player-shimmer-bg" style={{ width: 180, height: 16, borderRadius: 4 }} />
        </div>

        {/* Center Glowing Play button & loading state */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', zIndex: 2 }}>
          <div style={{ position: 'relative', width: 80, height: 80, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 24 }}>
            <div className="player-shimmer-bg" style={{ position: 'absolute', inset: 0, borderRadius: '50%' }} />
            <i className="fas fa-play" style={{ color: 'rgba(255, 255, 255, 0.4)', fontSize: 28, zIndex: 3 }} />
          </div>
          
          <div style={{ position: 'relative', width: 280, height: 4, background: 'rgba(255, 255, 255, 0.08)', borderRadius: 4, overflow: 'hidden', marginBottom: 20 }}>
            <div style={{
              position: 'absolute', top: 0, left: 0, height: '100%', width: '100%',
              background: 'linear-gradient(90deg, transparent, #e50914, transparent)',
              animation: 'core-progress-shimmer 1.5s infinite linear'
            }} />
          </div>
          <div style={{ color: '#fff', fontSize: 13, fontWeight: 700, letterSpacing: '1px', textTransform: 'uppercase', opacity: 0.9 }}>
            Connecting Stream
          </div>
          <div style={{ color: '#555', fontSize: 11, marginTop: 8 }}>
            Menghubungkan ke server untuk <span style={{ color: '#999', fontWeight: 600 }}>{fullTitle}</span>
          </div>
        </div>

        {/* Bottom Shimmer Controls */}
        <div className="player-row-bottom" style={{ position: 'absolute', bottom: 0, left: 0, width: '100%', padding: '24px 32px', display: 'flex', flexDirection: 'column', gap: 16, background: 'linear-gradient(to top, rgba(0,0,0,0.9) 0%, transparent 100%)', pointerEvents: 'none' }}>
          {/* Shimmer Progress Track */}
          <div className="player-shimmer-bg" style={{ width: '100%', height: 4, borderRadius: 2 }} />
          
          {/* Control items */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
              <div className="player-shimmer-bg" style={{ width: 22, height: 22, borderRadius: 4 }} />
              <div className="player-shimmer-bg" style={{ width: 22, height: 22, borderRadius: 4 }} />
              <div className="player-shimmer-bg" style={{ width: 22, height: 22, borderRadius: 4 }} />
              <div className="player-shimmer-bg" style={{ width: 80, height: 12, borderRadius: 3 }} />
            </div>
            
            <div className="player-shimmer-bg" style={{ width: 160, height: 16, borderRadius: 4 }} />
            
            <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
              <div className="player-shimmer-bg" style={{ width: 22, height: 22, borderRadius: 4 }} />
              <div className="player-shimmer-bg" style={{ width: 22, height: 22, borderRadius: 4 }} />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error || !data) return (
    <div className="detail-page" style={{ padding:'100px 48px' }}>
      <button onClick={handleClose} style={{ background:'none', border:'none', color:'#888', marginBottom:16, cursor:'pointer', fontSize:15 }}>← Kembali</button>
      <div style={{ background:'#1a0a0a', border:'1px solid #440000', borderRadius:10, padding:20 }}>
        <div style={{ color:'#ff6b6b', fontWeight:700, marginBottom:8 }}>Gagal memuat detail</div>
        <div style={{ color:'#555', fontSize:12, fontFamily:'monospace', wordBreak:'break-all' }}>{error || 'Data kosong'}</div>
      </div>
    </div>
  );

  const isMovie    = !data.seasons?.length;
  const genres     = toArr(data.genre || data.genres);
  const hasCW      = !!getSavedProgress(detailPath);
  const watchLabel = isMovie ? '▶ Tonton Film' : (hasCW ? '▶ Lanjutkan Nonton' : '▶ Tonton Ep. 1');
  const episodes   = data.seasons?.[currentSeason]?.episodes || [];
  const seriesPoster = data.poster || '';

  return (
    <div className="detail-page-container">
      <div className="detail-backdrop" style={{
        backgroundImage: detailPathOverride ? 'none' : `url(${seriesPoster})`,
        background: detailPathOverride ? 'rgba(6, 7, 10, 0.45)' : undefined,
        backdropFilter: detailPathOverride ? 'blur(16px)' : undefined,
        WebkitBackdropFilter: detailPathOverride ? 'blur(16px)' : undefined,
        opacity: detailPathOverride ? 1 : undefined,
        pointerEvents: 'auto',
        cursor: 'pointer'
      }} onClick={handleClose} />
      
      <div className="detail-modal-window">
        <button className="detail-modal-close" onClick={handleClose} title="Tutup">
          <i className="fas fa-times" />
        </button>
        <div className="detail-page">
          <div className="detail-hero">
            <img src={seriesPoster} alt={data.title} style={{ display: showTrailer ? 'none' : 'block' }} onError={e=>{e.target.style.opacity=0.1;}} />
            {showTrailer && data.trailerUrl && (
              <video ref={trailerVideoRef} src={data.trailerUrl} autoPlay muted={trailerMuted} loop playsInline style={{ width:'100%', height:'100%', objectFit:'cover', display:'block' }} />
            )}
            <div className="detail-hero-overlay" />
            <button className="detail-hero-back" onClick={handleClose}><i className="fas fa-chevron-left" /></button>
            {showTrailer && (
              <button onClick={() => setTrailerMuted(v => !v)} style={{ position:'absolute', bottom:20, right:48, zIndex:12, width:42, height:42, borderRadius:'50%', background:'rgba(0,0,0,0.5)', border:'1px solid rgba(255,255,255,0.25)', color:'#fff', fontSize:16, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}>
                <i className={`fas ${trailerMuted ? 'fa-volume-mute' : 'fa-volume-up'}`} />
              </button>
            )}
          </div>

          <div className="detail-content">
            <h1 className="detail-title">{data.title}</h1>
            <div className="detail-meta">
              {data.year && <span className="meta-badge">{data.year}</span>}
              {data.rating && <span className="meta-badge">⭐ {data.rating}</span>}
              {genres.slice(0,3).map((g,i)=><span key={i} className="meta-badge">{g}</span>)}
              <span className="meta-badge" style={{ background:'rgba(229,9,20,0.15)', color:'var(--primary)' }}>{isMovie ? 'Film' : 'Series'}</span>
            </div>

            <div className="detail-btns">
              <button className="btn-watch" onClick={handleWatchBtn} disabled={playerLoading} style={{ opacity:playerLoading?0.7:1 }}>
                {playerLoading ? <><div className="spinner" style={{ width:18,height:18,borderWidth:2 }} /> Memuat...</> : <><i className="fas fa-play" /> {watchLabel}</>}
              </button>
              {data.trailerUrl && (
                <button className="btn-watch" style={{ background:'rgba(255,255,255,0.1)', border:'1px solid rgba(255,255,255,0.2)', color:'#ccc', flex:'0 0 auto', minWidth:'auto', padding:'14px 20px' }}
                  onClick={() => setPlayerData({ url:data.trailerUrl, subtitles:[], savedTime:0 })}>
                  <i className="fas fa-film" />
                </button>
              )}
            </div>

            <div style={{ display:'flex', gap:14, marginBottom:22 }}>
              {[
                { label:'DAFTAR', icon:inList?'fa-check':'fa-plus', active:inList, fn:toggleList },
                { label:'SUKA', icon:'fa-thumbs-up', active:liked, fn:toggleLike },
                { label:'TDK SUKA', icon:'fa-thumbs-down', active:disliked, fn:toggleDislike },
              ].map(({ label, icon, active, fn }) => (
                <div key={label} style={{ display:'flex', flexDirection:'column', alignItems:'center', gap:4 }}>
                  <button className={`btn-icon-action ${active?'active':''}`} onClick={fn}><i className={`fas ${icon}`} /></button>
                  <span style={{ fontSize:9, color:'#555', fontWeight:700 }}>{label}</span>
                </div>
              ))}
            </div>

            {data.description && <p className="detail-desc">{data.description}</p>}
            <div className="detail-info-row">
              {data.country && <span className="info-chip"><strong>Negara:</strong> {data.country}</span>}
              {data.duration && <span className="info-chip"><strong>Durasi:</strong> {data.duration}</span>}
              {data.network && <span className="info-chip"><strong>Network:</strong> {data.network}</span>}
            </div>

            {toArr(data.cast).length > 0 && (
              <section style={{ marginBottom:30 }}>
                <div style={{ fontSize:16, fontWeight:800, color:'#fff', marginBottom:14 }}>Pemeran</div>
                <div className="cast-scroll">
                  {Array.from(new Map(toArr(data.cast).map(c=>[c.name?.trim().toLowerCase(),c])).values()).map((c,i)=>(
                    <div key={i} className="cast-item">
                      <img className="cast-avatar" src={c.avatar || '/unknow-cast.png'} alt={c.name} onError={e => { e.target.onerror = null; e.target.src = '/unknow-cast.png'; }} />
                      <span className="cast-name">{c.name}</span>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {!isMovie && data.seasons?.length > 0 && (
              <section style={{ marginBottom:30 }}>
                <div style={{ display:'flex', alignItems:'center', gap:16, marginBottom:18 }}>
                  <span style={{ fontSize:16, fontWeight:800, color:'#fff' }}>Episode</span>
                  {data.seasons.length > 1 && (
                    <div className="season-tabs" style={{ marginBottom:0 }}>
                      {data.seasons.map((s,si)=>(<button key={si} className={`season-tab ${si===currentSeason?'active':''}`} onClick={()=>setCurrentSeason(si)}>Season {s.season||si+1}</button>))}
                    </div>
                  )}
                </div>

                {episodes.length > BATCH_SIZE && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 20 }}>
                    {Array.from({ length: Math.ceil(episodes.length / BATCH_SIZE) }).map((_, idx) => {
                      const start = idx * BATCH_SIZE + 1;
                      const end = Math.min((idx + 1) * BATCH_SIZE, episodes.length);
                      const isActive = activeBatch === idx;
                      return (
                        <button
                          key={idx}
                          onClick={() => setActiveBatch(idx)}
                          style={{
                            padding: '6px 14px',
                            borderRadius: 20,
                            background: isActive ? 'var(--primary)' : 'rgba(255,255,255,0.06)',
                            color: '#fff',
                            border: isActive ? '1px solid var(--primary)' : '1px solid rgba(255,255,255,0.1)',
                            fontSize: 12,
                            fontWeight: 700,
                            cursor: 'pointer',
                            transition: 'all 0.2s',
                          }}
                          onMouseEnter={e => {
                            if (!isActive) e.currentTarget.style.background = 'rgba(255,255,255,0.12)';
                          }}
                          onMouseLeave={e => {
                            if (!isActive) e.currentTarget.style.background = 'rgba(255,255,255,0.06)';
                          }}
                        >
                          {start} - {end}
                        </button>
                      );
                    })}
                  </div>
                )}

                <div style={{ display:'grid', gridTemplateColumns:'repeat(4, 1fr)', gap:16 }}>
                  {episodes.slice(activeBatch * BATCH_SIZE, (activeBatch + 1) * BATCH_SIZE).map((ep, vIdx)=>{
                    const ei = activeBatch * BATCH_SIZE + vIdx;
                    const isActive = ei === currentEp;
                    const thumb = ep.thumbnail || seriesPoster;
                    return (
                      <div key={ei} onClick={()=>playVideo(ei,currentSeason)} style={{ background: isActive ? '#1c0505' : 'var(--bg-card)', borderRadius:'var(--radius-md)', overflow:'hidden', cursor:'pointer', border: isActive ? '1px solid var(--primary)' : '1px solid transparent', transition:'transform 0.28s ease, box-shadow 0.28s ease' }}
                        onMouseEnter={e => { e.currentTarget.style.transform='translateY(-4px)'; e.currentTarget.style.boxShadow='0 8px 30px rgba(0,0,0,0.4)'; }}
                        onMouseLeave={e => { e.currentTarget.style.transform=''; e.currentTarget.style.boxShadow=''; }}>
                        <div style={{ width:'100%', aspectRatio:'16/9', background:'var(--bg-card2)', position:'relative', overflow:'hidden' }}>
                          <img src={thumb} alt="" style={{ width:'100%', height:'100%', objectFit:'cover', opacity: ep.thumbnail ? 1 : 0.4 }} onError={e=>{e.target.style.opacity='0.15';}} />
                          <div style={{ position:'absolute', inset:0, background:'rgba(0,0,0,0.3)', display:'flex', alignItems:'center', justifyContent:'center', opacity:0, transition:'opacity 0.2s' }}
                             onMouseEnter={e=>e.currentTarget.style.opacity='1'} onMouseLeave={e=>e.currentTarget.style.opacity='0'}>
                            <i className="fas fa-play" style={{ fontSize:22, color:'#fff', filter:'drop-shadow(0 2px 4px rgba(0,0,0,0.6))' }} />
                          </div>
                          <div style={{ position:'absolute', top:8, left:8, background: isActive ? 'var(--primary)' : 'rgba(0,0,0,0.7)', color:'#fff', fontSize:11, fontWeight:800, padding:'2px 8px', borderRadius:4 }}>{ep.episode || ei + 1}</div>
                        </div>
                        <div style={{ padding:'12px 14px' }}>
                          <div style={{ fontSize:14, fontWeight:700, color: isActive?'#fff':'#ddd', overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap', marginBottom:4 }}>{ep.title || `Episode ${ep.episode || ei + 1}`}</div>
                          {isActive && <div style={{ fontSize:11, color:'var(--primary)', fontWeight:700 }}>▶ SEDANG DIPUTAR</div>}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </section>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
