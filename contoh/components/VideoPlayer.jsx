import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { fmtTime } from '../api.js';

export default function VideoPlayer({
  url,
  title,
  subtitles = [],
  downloads = [],
  seasons = [],
  currentSeasonIdx = 0,
  currentEpIdx = -1,
  onEpisodeChange,
  onClose,
  onSaveCW,
  savedTime = 0,
}) {
  const videoRef    = useRef(null);
  const hlsRef      = useRef(null);
  const progressRef = useRef(null);
  const ctrlTimer   = useRef(null);
  const blobUrls    = useRef([]);
  const wrapRef     = useRef(null); // fullscreen target = entire player wrapper

  // ── Web Audio API ──────────────────────────────────────
  const audioCtxRef = useRef(null); // AudioContext
  const sourceRef   = useRef(null); // MediaElementSourceNode (created once per <video>)

  const [playing, setPlaying]         = useState(false);
  const [duration, setDuration]       = useState(0);
  const [curTime, setCurTime]         = useState(0);
  const [showCtrl, setShowCtrl]       = useState(true);
  const [showEpPanel, setShowEpPanel] = useState(false);
  const [showQuality, setShowQuality] = useState(false);
  const [showSubMenu, setShowSubMenu] = useState(false);
  const [showSizeMenu, setShowSizeMenu] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [hlsLevels, setHlsLevels]     = useState([]);
  const [curHlsLevel, setCurHlsLevel] = useState(-1);
  const [curDlIdx, setCurDlIdx]       = useState(0);
  const [subIdx, setSubIdx]           = useState(0);
  const [subSize, setSubSize]         = useState('medium'); // small=14 medium=24 large=32
  const [buffering, setBuffering]     = useState(false);
  const [bufferPct, setBufferPct]     = useState(0);
  const [dlSpeed, setDlSpeed]         = useState('');
  const [speed, setSpeed]             = useState(1);
  const [showSpeedMenu, setShowSpeedMenu] = useState(false);
  const [brightness, setBrightness]   = useState(1);
  const [wasPlayingBeforeEp, setWasPlayingBeforeEp] = useState(false);


  /* ── cleanup blobs ──────────────────────────────────── */
  useEffect(() => () => blobUrls.current.forEach(u => URL.revokeObjectURL(u)), []);

  // Cleanup AudioContext on unmount
  useEffect(() => () => {
    try { audioCtxRef.current?.close(); } catch {}
    audioCtxRef.current = null;
    sourceRef.current   = null;
  }, []);

  /* ── track fullscreen state ─────────────────────────── */
  useEffect(() => {
    function onFsChange() {
      const fs = !!(document.fullscreenElement || document.webkitFullscreenElement);
      setIsFullscreen(fs);
      if (!fs) {
        // Exited via browser back/gesture — also remove CSS rotation
        const el = wrapRef.current;
        if (el?.dataset.rotated === '1') {
          el.style.cssText = '';
          el.dataset.rotated = '';
        }
        try { (screen.orientation?.unlock || (() => {}))(); } catch {}
      }
    }
    document.addEventListener('fullscreenchange', onFsChange);
    document.addEventListener('webkitfullscreenchange', onFsChange);
    return () => {
      document.removeEventListener('fullscreenchange', onFsChange);
      document.removeEventListener('webkitfullscreenchange', onFsChange);
    };
  }, []);

  /* ── load source ────────────────────────────────────── */
  useEffect(() => {
    if (!url) return;
    const video = videoRef.current;
    if (!video) return;
    if (hlsRef.current) { hlsRef.current.destroy(); hlsRef.current = null; }
    setHlsLevels([]); setCurHlsLevel(-1); setCurDlIdx(0);

    function initAudio() {
      if (sourceRef.current) return; // already wired
      try {
        const ctx = new (window.AudioContext || window.webkitAudioContext)();
        audioCtxRef.current = ctx;

        const source = ctx.createMediaElementSource(video);
        sourceRef.current = source;

        // ── 1. High-pass 90Hz: remove sub-bass rumble ──────────
        const lowCut = ctx.createBiquadFilter();
        lowCut.type            = 'highpass';
        lowCut.frequency.value = 90;
        lowCut.Q.value         = 0.7;

        // ── 2. Low shelf +2dB @ 80Hz: bass warmth ────────────────
        const lowShelf = ctx.createBiquadFilter();
        lowShelf.type            = 'lowshelf';
        lowShelf.frequency.value = 80;
        lowShelf.gain.value      = 2;    // +2 dB

        // ── 3. Hi-mid peaking +3dB @ 3kHz: voice clarity ─────────
        const hiMid = ctx.createBiquadFilter();
        hiMid.type            = 'peaking';
        hiMid.frequency.value = 3000;
        hiMid.Q.value         = 0.9;
        hiMid.gain.value      = 3;       // +3 dB

        // ── 4. Compressor: tame dynamic swings ───────────────────
        const comp = ctx.createDynamicsCompressor();
        comp.threshold.value = -24;
        comp.knee.value      = 8;
        comp.ratio.value     = 4;
        comp.attack.value    = 0.003;
        comp.release.value   = 0.25;

        // ── 5. High shelf +1.5dB @ 8kHz: air / hi-fi ambience ────
        const highShelf = ctx.createBiquadFilter();
        highShelf.type            = 'highshelf';
        highShelf.frequency.value = 8000;
        highShelf.gain.value      = 1.5; // +1.5 dB

        // ── 6. Makeup gain ────────────────────────────────────────
        const gain = ctx.createGain();
        gain.gain.value = 1.4;

        // Chain: source → highpass → lowShelf → hiMid → comp → highShelf → gain → speakers
        source.connect(lowCut);
        lowCut.connect(lowShelf);
        lowShelf.connect(hiMid);
        hiMid.connect(comp);
        comp.connect(highShelf);
        highShelf.connect(gain);
        gain.connect(ctx.destination);

        // Resume context if suspended (browser autoplay policy)
        if (ctx.state === 'suspended') ctx.resume();
      } catch (e) {
        console.warn('Web Audio init failed:', e.message);
      }
    }

    function startPlay() {
      video.currentTime = savedTime > 10 ? savedTime : 0;
      initAudio();
      video.play().catch(() => {});
      setPlaying(true);
    }

    if (url.includes('.m3u8')) {
      if (Hls.isSupported()) {
        const hls = new Hls({
          enableWorker: true,
          fragLoadingMaxRetry: 10,
          startLevel: -1,
          autoLevelCapping: -1,
          abrEwmaDefaultEstimate: 10_000_000,
        });
        hls.loadSource(url);
        hls.attachMedia(video);
        hls.on(Hls.Events.MANIFEST_PARSED, (_, data) => {
          const levels = data.levels || [];
          setHlsLevels(levels);
          const highestIdx = levels.length - 1;
          if (highestIdx >= 0) {
            hls.startLevel   = highestIdx;
            hls.currentLevel = highestIdx;
            setCurHlsLevel(highestIdx);
          }
          startPlay();
        });

        hlsRef.current = hls;
      } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = url;
        video.addEventListener('loadedmetadata', startPlay, { once: true });
      }
    } else {
      video.src = url;
      video.addEventListener('loadedmetadata', startPlay, { once: true });
    }
    return () => { if (hlsRef.current) { hlsRef.current.destroy(); hlsRef.current = null; } };
  }, [url]);

  /* ── load subtitles as blob VTT ────────────────────── */
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    // Reset subtitle state on every change (episode switch etc)
    setSubIdx(0);
    Array.from(video.querySelectorAll('track')).forEach(t => { try { video.removeChild(t); } catch {} });
    blobUrls.current.forEach(u => URL.revokeObjectURL(u));
    blobUrls.current = [];
    if (!subtitles.length) return;

    let cancelled = false;
    (async () => {
      for (let i = 0; i < subtitles.length; i++) {
        if (cancelled) return;
        try {
          const res  = await fetch(subtitles[i].url);
          let   text = await res.text();
          if (!text.trimStart().startsWith('WEBVTT')) text = 'WEBVTT\n\n' + text;
          const blob    = new Blob([text], { type: 'text/vtt' });
          const blobUrl = URL.createObjectURL(blob);
          blobUrls.current.push(blobUrl);
          if (cancelled) return;
          const track   = document.createElement('track');
          track.kind    = 'subtitles';
          track.label   = subtitles[i].name;
          track.srclang = subtitles[i].language;
          track.src     = blobUrl;
          if (i === 0) track.default = true;
          video.appendChild(track);
        } catch (e) { console.warn('Sub load fail:', subtitles[i].name, e.message); }
      }
      if (!cancelled) {
        setTimeout(() => {
          Array.from(video.textTracks).forEach((t, i) => { t.mode = i === 0 ? 'showing' : 'disabled'; });
        }, 500);
      }
    })();
    return () => { cancelled = true; };
  }, [subtitles, url]);

  /* ── save progress every 30s ────────────────────────── */
  useEffect(() => {
    const tid = setInterval(() => {
      const v = videoRef.current;
      if (!v || v.paused) return;
      onSaveCW?.({ time: v.currentTime, duration: v.duration, episode: currentEpIdx, seasonIdx: currentSeasonIdx });
    }, 30000);
    return () => clearInterval(tid);
  }, [currentEpIdx, currentSeasonIdx, onSaveCW]);

  /* ── buffer progress + download speed ─────────────────── */
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    let prevEnd = 0, prevTime = Date.now();
    setBuffering(true); setDlSpeed('Menghubungkan...');
    const tid = setInterval(() => {
      if (!video.duration || video.duration === Infinity) return;
      if (video.buffered.length > 0) {
        const end = video.buffered.end(video.buffered.length - 1);
        setBufferPct((end / video.duration) * 100);
        const now = Date.now(), dt = (now - prevTime) / 1000;
        if (dt >= 0.8) {
          const delta = end - prevEnd;
          if (delta > 0) {
            const res = downloads[curDlIdx]?.resolution || 480;
            const brkbps = res >= 1080 ? 5000 : res >= 720 ? 2500 : res >= 480 ? 1200 : 600;
            const kbps = Math.round((delta * brkbps) / dt);
            setDlSpeed(kbps > 1000 ? (kbps/1000).toFixed(1)+' Mbps' : kbps+' Kbps');
          }
          prevEnd = end; prevTime = now;
        }
      }
      if (video.readyState >= 4 && !video.paused && !video.seeking) setBuffering(false);
    }, 500);
    return () => clearInterval(tid);
  }, [url, curDlIdx]);

  /* ── controls auto-hide ─────────────────────────────── */
  function showControls() {
    setShowCtrl(true);
    clearTimeout(ctrlTimer.current);
    ctrlTimer.current = setTimeout(() => {
      if (videoRef.current && !videoRef.current.paused) setShowCtrl(false);
    }, 3500);
  }

  function togglePlay(e) {
    e?.stopPropagation();
    const v = videoRef.current; if (!v) return;
    // Resume AudioContext on first user gesture (required by browsers)
    if (audioCtxRef.current?.state === 'suspended') audioCtxRef.current.resume();
    v.paused ? v.play() : v.pause();
    showControls();
  }

  function seekBy(sec) {
    const v = videoRef.current; if (!v) return;
    v.currentTime = Math.max(0, Math.min(v.duration || 0, v.currentTime + sec));
    showControls();
  }

  function onProgressClick(e) {
    e.stopPropagation();
    const rect = progressRef.current.getBoundingClientRect();
    const pct  = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    if (videoRef.current) videoRef.current.currentTime = pct * (duration || 0);
    showControls();
  }

  /* ── quality ─────────────────────────────────────────── */
  const usingHls   = hlsLevels.length > 1;
  const usingDl    = !usingHls && downloads.length > 1;
  const hasQuality = usingHls || usingDl;

  function setHlsQuality(idx) {
    if (hlsRef.current) hlsRef.current.currentLevel = idx;
    setCurHlsLevel(idx); setShowQuality(false);
  }

  function setManualQuality(idx) {
    if (!downloads[idx]) return;
    const video = videoRef.current;
    const t     = video?.currentTime || 0;
    setCurDlIdx(idx); setShowQuality(false);
    if (video) {
      if (hlsRef.current) { hlsRef.current.destroy(); hlsRef.current = null; }
      video.src = downloads[idx].url;
      video.load();
      video.addEventListener('loadedmetadata', () => { video.currentTime = t; video.play().catch(()=>{}); }, { once: true });
    }
  }

  function getCleanLabel(str) {
    if (!str) return 'Medium';
    if (str.toLowerCase().includes('high')) return 'High';
    if (str.toLowerCase().includes('medium')) return 'Medium';
    if (str.toLowerCase().includes('low')) return 'Low';
    return str.replace(/ \d+p$/, '');
  }

  function getLabelForHeight(h, fallback) {
    if (!h) return fallback;
    if (h >= 1080) return `High`;
    if (h >= 480) return `Medium`;
    return `Low`;
  }

  function qualityLabel() {
    if (usingHls) {
      return curHlsLevel === -1 ? 'Auto' : getCleanLabel(getLabelForHeight(hlsLevels[curHlsLevel]?.height, 'Medium'));
    }
    if (usingDl) {
      const l = getCleanLabel(downloads[curDlIdx]?.label);
      if (curDlIdx === 0) return `Auto : ${l}`;
      return l;
    }
    return 'Auto';
  }

  /* ── subtitle ────────────────────────────────────────── */
  const SUB_SIZES = { small: 14, medium: 24, large: 32 };

  useEffect(() => {
    const px = SUB_SIZES[subSize] || 24;
    let s = document.getElementById('oflix-cue-size');
    if (!s) { s = document.createElement('style'); s.id = 'oflix-cue-size'; document.head.appendChild(s); }
    const offset = showCtrl ? '-80px' : '0px';
    s.textContent = `
      video::-webkit-media-text-track-container { transform: translateY(${offset}) !important; transition: transform 0.35s ease !important; }
      ::cue { font-size: ${px}px !important; }
    `;
  }, [showCtrl, subSize]);

  function changeSubSize(size) {
    setSubSize(size);
  }

  function selectSub(i) {
    setSubIdx(i); setShowSubMenu(false);
    const video = videoRef.current; if (!video) return;
    Array.from(video.textTracks).forEach((t, idx) => { t.mode = idx === i ? 'showing' : 'disabled'; });
  }
  function turnOffSub() {
    setSubIdx(-1); setShowSubMenu(false);
    const video = videoRef.current; if (!video) return;
    Array.from(video.textTracks).forEach(t => { t.mode = 'disabled'; });
  }

  /* ── fullscreen + aggressive landscape lock ─────────── */
  function applyRotation() {
    // CSS transform fallback: rotate the overlay 90° to fake landscape
    if (window.innerWidth >= window.innerHeight) return; // already landscape
    const el = wrapRef.current; if (!el) return;
    const vw = window.innerHeight; // swap: height becomes width
    const vh = window.innerWidth;
    el.style.cssText = [
      'position:fixed',
      'top:0',
      'left:0',
      `width:${vw}px`,
      `height:${vh}px`,
      'transform:rotate(90deg) translateY(-100%)',
      'transform-origin:top left',
      'z-index:99999',
      'background:#000',
    ].join(';') + ';';
    el.dataset.rotated = '1';
    setIsFullscreen(true);
  }

  function removeRotation() {
    const el = wrapRef.current; if (!el) return;
    el.style.cssText = '';
    el.dataset.rotated = '';
    setIsFullscreen(false);
  }

  function lockLandscape() {
    try {
      const ori = screen.orientation;
      if (ori?.lock) {
        ori.lock('landscape').catch(() => {}); // best-effort
        return;
      }
    } catch {}
  }

  function toggleFullscreen(e) {
    e.stopPropagation();
    const el    = wrapRef.current; if (!el) return;
    const isFs  = !!(document.fullscreenElement || document.webkitFullscreenElement);
    const isCss = el.dataset.rotated === '1';

    if (isFs || isCss) {
      if (isFs) (document.exitFullscreen || document.webkitExitFullscreen)?.call(document);
      if (isCss) removeRotation();
      try { screen.orientation?.unlock?.(); } catch {}
    } else {
      const fsPromise = (el.requestFullscreen || el.webkitRequestFullscreen)?.call(el);
      if (fsPromise instanceof Promise) {
        fsPromise.then(() => lockLandscape()).catch(() => applyRotation());
      } else if (fsPromise === undefined) {
        applyRotation();
      } else {
        lockLandscape();
      }
    }
  }

  /* ── tap to toggle UI ───────────────────────────────── */
  function handleOverlayClick(e) {
    if (e.target.closest('.pctrl-btn') || e.target.closest('.pctrl-seek') || e.target.closest('.player-ep-panel') || e.target.closest('.pctrl-popup')) return;
    if (showCtrl) {
      setShowCtrl(false);
      clearTimeout(ctrlTimer.current);
    } else {
      showControls();
    }
  }

  /* ── episodes ────────────────────────────────────────── */
  const eps = seasons[currentSeasonIdx]?.episodes || [];
  function playEp(sIdx, eIdx) { setShowEpPanel(false); onEpisodeChange?.(sIdx, eIdx); }

  function openEpPanel() {
    setWasPlayingBeforeEp(playing);
    if (videoRef.current) videoRef.current.pause();
    setShowEpPanel(true);
    setShowSpeedMenu(false);
    setShowQuality(false);
    setShowSubMenu(false);
    setShowSizeMenu(false);
  }

  function closeEpPanel() {
    setShowEpPanel(false);
    if (wasPlayingBeforeEp && videoRef.current) {
      videoRef.current.play().catch(()=>{});
    }
  }

  const pct = duration ? (curTime / duration) * 100 : 0;

  return (
    <div
      ref={wrapRef}
      className="player-overlay"
      onClick={handleOverlayClick}
    >
      {/* ── VIDEO ────────────────────────────────────────── */}
      <div className="player-video-wrap">
        <video
          ref={videoRef}
          playsInline
          crossOrigin="anonymous"
          style={{ filter: `brightness(${brightness}) contrast(1.05) saturate(1.05)` }}
          onTimeUpdate={e => setCurTime(e.target.currentTime)}
          onDurationChange={e => setDuration(e.target.duration)}
          onPlay={() => { setPlaying(true); setBuffering(false); showControls(); }}
          onPause={() => setPlaying(false)}
          onWaiting={() => setBuffering(true)}
          onCanPlay={() => setBuffering(false)}
          onSeeking={() => setBuffering(true)}
          onSeeked={() => setBuffering(false)}
          onEnded={() => {
            onSaveCW?.({ time: 0, duration, episode: currentEpIdx, seasonIdx: currentSeasonIdx });
            if (currentEpIdx >= 0 && currentEpIdx < eps.length - 1) playEp(currentSeasonIdx, currentEpIdx + 1);
          }}
        />
      </div>

      {/* ── BUFFERING INDICATOR ────────────────────────────── */}
      {buffering && (
        <div style={{
          position:'absolute', top:'50%', left:'50%', transform:'translate(-50%,-50%)',
          zIndex:9050, pointerEvents:'none', textAlign:'center',
          background:'rgba(0,0,0,0.6)', borderRadius:14, padding:'20px 28px',
          backdropFilter:'blur(6px)',
        }}>
          <div className="spinner" style={{ width:36, height:36, borderWidth:3, margin:'0 auto 10px' }} />
          <div style={{ color:'#fff', fontSize:13, fontWeight:700, marginBottom:4 }}>Memuat Video...</div>
          {dlSpeed && <div style={{ color:'rgba(255,255,255,0.65)', fontSize:11, fontWeight:600 }}>{dlSpeed}</div>}
          {bufferPct > 0 && bufferPct < 99 && (
            <div style={{ marginTop:6, width:100, height:3, background:'rgba(255,255,255,0.15)', borderRadius:2, margin:'6px auto 0' }}>
              <div style={{ height:'100%', background:'var(--primary)', borderRadius:2, width:Math.min(bufferPct,100)+'%', transition:'width 0.3s' }} />
            </div>
          )}
        </div>
      )}

      {/* ── NETFLIX STYLE CONTROLS OVERLAY ──────────────────────── */}
      <div className={`player-ctrl ${showCtrl ? '' : 'player-ctrl--hidden'}`}>
        
        {/* TOP ROW: Title on left, Close (X) on right (Only visible in Landscape, or keep in portrait?) */}
        {/* Netflix actually keeps title and Close in Portrait too, but let's hide if minimal is strictly wanted. We will keep it for now as it's useful to close! */}
        <div className="player-row-top" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 24px', background: 'linear-gradient(to bottom, rgba(0,0,0,0.8) 0%, transparent 100%)' }}>
          <div className="pctrl-title" style={{ position: 'static', transform: 'none', maxWidth: '80%', textAlign: 'left', fontSize: 16, textShadow: '0 1px 4px rgba(0,0,0,0.8)' }}>
            {title}
          </div>
          <button className="pctrl-btn pctrl-back" style={{ padding: 8 }} onClick={(e) => {
            e.stopPropagation();
            onSaveCW?.({ time: videoRef.current?.currentTime||0, duration, episode: currentEpIdx, seasonIdx: currentSeasonIdx });
            onClose();
          }}>
            <i className="fas fa-times" style={{fontSize: 24}} />
          </button>
        </div>

        {/* CENTER AREA: Skip Back, Play/Pause, Skip Forward */}
        <div className="player-center" style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', display: 'flex', alignItems: 'center', gap: isFullscreen ? '60px' : '30px' }}>
          {isFullscreen && (
            <button className="pctrl-btn skip" onClick={(e) => { e.stopPropagation(); seekBy(-10); }} style={{ fontSize: 36, position: 'relative' }}>
              <i className="fas fa-undo" />
              <span style={{ fontSize: 11, position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -35%)', fontWeight: '900' }}>10</span>
            </button>
          )}
          
          <button className="pctrl-btn play" onClick={(e) => { e.stopPropagation(); togglePlay(); }} style={{ fontSize: 48, width: 60, height: 60, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <i className={`fas ${playing ? 'fa-pause' : 'fa-play'}`} />
          </button>
          
          {isFullscreen && (
            <button className="pctrl-btn skip" onClick={(e) => { e.stopPropagation(); seekBy(10); }} style={{ fontSize: 36, position: 'relative' }}>
              <i className="fas fa-redo" />
              <span style={{ fontSize: 11, position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -35%)', fontWeight: '900' }}>10</span>
            </button>
          )}
        </div>

        {/* BOTTOM AREA: Progress Bar & Bottom Menu Row */}
        <div className="player-row-bottom" style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '0 24px 24px', background: 'linear-gradient(to top, rgba(0,0,0,0.95) 0%, rgba(0,0,0,0.5) 75%, transparent 100%)', width: '100%' }}>
          
          {/* Progress Bar + Remaining Time */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div ref={progressRef} className="pctrl-seek" onClick={onProgressClick} style={{ flex: 1, height: 24, margin: 0, padding: 0, display: 'flex', alignItems: 'center', cursor: 'pointer', position: 'relative' }}>
              <div className="pctrl-seek-track" style={{ width: '100%', height: 4, background: 'rgba(255,255,255,0.2)', position: 'relative', borderRadius: 2 }}>
                <div style={{ position:'absolute', top:0, left:0, height:'100%', width: bufferPct+'%', background:'rgba(255,255,255,0.4)', borderRadius: 2, transition:'width 0.3s linear' }} />
                <div style={{ width: pct+'%', background: '#e50914', height: '100%', borderRadius: 2, position: 'relative' }}>
                  <div style={{ position: 'absolute', right: -7, top: -5, width: 14, height: 14, background: '#e50914', borderRadius: '50%' }} />
                </div>
              </div>
            </div>
            {duration > 0 && (
              <span style={{ fontSize: 13, fontWeight: 'bold', color: '#fff', opacity: 0.9, fontVariantNumeric: 'tabular-nums' }}>
                {fmtTime(duration - curTime)}
              </span>
            )}
          </div>

          {/* Additional Features Row: ONLY IN LANDSCAPE */}
          {isFullscreen && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '30px', marginTop: '12px' }}>
              
              {/* Speed */}
              <div className="pctrl-menu-wrap" style={{ display: 'flex', gap: 14 }}>
                <button className="pctrl-btn" onClick={e => { e.stopPropagation(); setShowSpeedMenu(v=>!v); setShowEpPanel(false); setShowSubMenu(false); setShowQuality(false); setShowSizeMenu(false); }} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 'bold' }}>
                  <i className="fas fa-tachometer-alt" style={{fontSize: 18}} /> Speed ({speed}x)
                </button>
                {showSpeedMenu && (<div className="pctrl-popup" style={{top: 'auto', bottom: '100%', left: '50%', transform: 'translateX(-50%)', marginBottom: 15, paddingBottom: 5, minWidth: 100, textAlign: 'center'}}><div className="pctrl-popup-head">Kecepatan</div>{[0.5, 0.75, 1, 1.25, 1.5, 2, 3].map((s) => (<div key={s} className={`pctrl-popup-item ${speed===s?'on':''}`} onClick={e=>{e.stopPropagation(); setSpeed(s); if(videoRef.current) videoRef.current.playbackRate = s; setShowSpeedMenu(false);}}>{s === 1 ? 'Normal' : s+'x'}</div>))}</div>)}
              </div>

              {/* Episodes */}
              {seasons.length > 0 && (
                <button className="pctrl-btn" onClick={e=>{e.stopPropagation(); openEpPanel(); }} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 'bold' }}>
                  <i className="fas fa-layer-group" style={{fontSize: 18}} /> Episodes
                </button>
              )}

              {/* Subtitles & Audio */}
              {subtitles.length > 0 && (
                <div className="pctrl-menu-wrap" style={{ display: 'flex', gap: 14 }}>
                  <button className="pctrl-btn" onClick={e => { e.stopPropagation(); setShowSubMenu(v=>!v); setShowSpeedMenu(false); setShowQuality(false); setShowSizeMenu(false); }} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 'bold' }}>
                    <i className="fas fa-comment-alt" style={{fontSize: 18}} /> Audio & Subtitles
                  </button>
                  {showSubMenu && (<div className="pctrl-popup" style={{top: 'auto', bottom: '100%', right: 0, marginBottom: 15, paddingBottom: 5}}><div className="pctrl-popup-head">Subtitle</div><div className={`pctrl-popup-item ${subIdx===-1?'on':''}`} onClick={e=>{e.stopPropagation();turnOffSub();}}>Off</div>{subtitles.map((s,i) => (<div key={i} className={`pctrl-popup-item ${subIdx===i?'on':''}`} onClick={e=>{e.stopPropagation();selectSub(i);}}>{s.name}</div>))}</div>)}
                </div>
              )}

              {/* Quality */}
              {hasQuality && (
                <div className="pctrl-menu-wrap" style={{ display: 'flex', gap: 14 }}>
                  <button className="pctrl-btn" onClick={e => { e.stopPropagation(); setShowQuality(v=>!v); setShowSpeedMenu(false); setShowSubMenu(false); setShowSizeMenu(false); }} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 'bold' }}>
                    <i className="fas fa-cog" style={{fontSize: 18}} /> {qualityLabel()}
                  </button>
                  {showQuality && (<div className="pctrl-popup" style={{top: 'auto', bottom: '100%', right: 0, marginBottom: 15, paddingBottom: 5}}><div className="pctrl-popup-head">Kualitas</div>{usingHls && <><div className={`pctrl-popup-item ${curHlsLevel===-1?'on':''}`} onClick={e=>{e.stopPropagation();setHlsQuality(-1);}}>Auto</div>{hlsLevels.map((l,i) => (<div key={i} className={`pctrl-popup-item ${curHlsLevel===i?'on':''}`} onClick={e=>{e.stopPropagation();setHlsQuality(i);}}>{getCleanLabel(getLabelForHeight(l.height, 'Q'+(i+1)))}</div>))}</>}{usingDl && downloads.map((d,i) => (<div key={i} className={`pctrl-popup-item ${curDlIdx===i?'on':''}`} onClick={e=>{e.stopPropagation();setManualQuality(i);}}>{getCleanLabel(d.label)}</div>))}</div>)}
                </div>
              )}

              {/* Next Episode */}
              {eps.length > 0 && currentEpIdx < eps.length - 1 && (
                <button className="pctrl-btn" onClick={e=>{e.stopPropagation(); playEp(currentSeasonIdx, currentEpIdx+1);}} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 'bold' }}>
                  <i className="fas fa-step-forward" style={{fontSize: 18}} /> Next Episode
                </button>
              )}
            </div>
          )}
        </div>

        {/* BRIGHTNESS SLIDER (Left edge) - ONLY IN LANDSCAPE */}
        {isFullscreen && (
          <div style={{ position: 'absolute', left: 40, top: '50%', transform: 'translateY(-50%)', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
            <i className="fas fa-sun" style={{ fontSize: 20, color: '#fff', textShadow: '0 2px 4px rgba(0,0,0,0.5)' }} />
            <input
              type="range"
              min="0.2" max="1" step="0.05"
              value={brightness}
              onChange={(e) => setBrightness(parseFloat(e.target.value))}
              onClick={(e) => e.stopPropagation()}
              onTouchStart={(e) => e.stopPropagation()}
              style={{
                WebkitAppearance: 'slider-vertical',
                width: 6,
                height: 120,
                outline: 'none',
                accentColor: '#e50914',
                cursor: 'pointer'
              }}
            />
          </div>
        )}

        {/* FULLSCREEN BUTTON: ALWAYS FLOATING RIGHT CENTER EXACTLY LIKE ORIGINAL */}
        <button className="pctrl-btn pctrl-fs" onClick={toggleFullscreen}>
          <i className={`fas ${isFullscreen ? 'fa-compress' : 'fa-expand'}`} />
        </button>

      </div>

      {/* ── EPISODE PANEL ────────────────────────────────── */}
      {seasons.length > 0 && (
        <div className={`player-ep-panel ${showEpPanel ? 'open' : ''}`} style={{ height: '100%', borderRadius: 0, paddingBottom: 24 }}>
          <div className="panel-header">
            <span className="panel-title">Episode</span>
            <button className="panel-close" onClick={closeEpPanel}>&times;</button>
          </div>
          {seasons.length > 1 && (
            <div className="season-tabs" style={{padding:'8px 14px 0'}}>
              {seasons.map((s,si) => (
                <button key={si} className={`season-tab ${si===currentSeasonIdx?'active':''}`}
                  onClick={()=>playEp(si,0)}>S{s.season||si+1}</button>
              ))}
            </div>
          )}
          <div className="panel-ep-scroll">
            {eps.map((ep,ei) => (
              <div key={ei} className="ep-item" onClick={()=>playEp(currentSeasonIdx,ei)}>
                <div className={`ep-num ${ei===currentEpIdx?'active':''}`}>{ep.episode||ei+1}</div>
                <div className="ep-info">
                  <div className="ep-title">{ep.title||`Episode ${ep.episode||ei+1}`}</div>
                  {ei===currentEpIdx && <div className="ep-sub">▶ SEDANG DIPUTAR</div>}
                </div>
                <i className="fas fa-play ep-play" />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
