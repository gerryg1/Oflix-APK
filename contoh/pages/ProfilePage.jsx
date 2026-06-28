import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar } from '../components/ProfilePicker.jsx';

const AUTH_API = '/auth_api.php';

async function apiFetch(action, body = null) {
  const token = localStorage.getItem('oflix_token');
  const res = await fetch(`${AUTH_API}?action=${action}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, token }),
  });
  return res.json();
}

export default function ProfilePage() {
  const nav = useNavigate();
  const [data, setData]       = useState(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab]         = useState('cw');

  useEffect(() => {
    apiFetch('profilePage').then(res => {
      if (res.ok) setData(res);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  function handleItem(item) {
    if (item.type === 'komik') nav(`/komik/detail?d=${encodeURIComponent(item.detailPath)}`);
    else nav(`/detail?p=${encodeURIComponent(item.detailPath)}`);
  }

  async function handleAvatarUpload(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async () => {
      if (reader.result.length > 500000) { alert('Gambar terlalu besar. Max 500KB.'); return; }
      await apiFetch('updateAvatar', { avatar_url: reader.result });
      setData(prev => prev ? { ...prev, profile: { ...prev.profile, avatar_url: reader.result } } : prev);
    };
    reader.readAsDataURL(file);
  }

  if (loading) return <div className="spinner-center" style={{ minHeight:'60vh' }}><div className="spinner" /></div>;
  if (!data) return <div style={{ padding:40, color:'#555', textAlign:'center' }}>Gagal memuat profil.</div>;

  const { profile, cw, watchlist, likes, history } = data;
  const tabs = [
    { key:'cw',        label:'Nonton',  items: cw },
    { key:'likes',     label:'Suka',    items: likes },
    { key:'watchlist', label:'Daftar',  items: watchlist },
    { key:'history',   label:'Riwayat', items: history },
  ];
  const active = tabs.find(t => t.key === tab) || tabs[0];

  return (
    <div style={{ paddingBottom: 80 }}>
      {/* Profile header */}
      <div style={{ padding:'16px 14px', display:'flex', alignItems:'center', gap:14, borderBottom:'1px solid #1a1a1a' }}>
        <div style={{ position:'relative' }}>
          <Avatar profile={profile} size={64} />
          <label style={{
            position:'absolute', bottom:-2, right:-2,
            width:24, height:24, borderRadius:'50%',
            background:'var(--primary)', border:'2px solid var(--bg)',
            display:'flex', alignItems:'center', justifyContent:'center', cursor:'pointer',
          }}>
            <i className="fas fa-camera" style={{ fontSize:9, color:'#fff' }} />
            <input type="file" accept="image/*" onChange={handleAvatarUpload} style={{ display:'none' }} />
          </label>
        </div>
        <div>
          <div style={{ fontSize:18, fontWeight:900, color:'#fff', fontFamily:'var(--font-display)' }}>{profile.username}</div>
          <div style={{ display:'flex', gap:12, marginTop:4 }}>
            <span style={{ fontSize:10, color:'#666' }}><strong style={{ color:'#ccc' }}>{watchlist.length}</strong> Daftar</span>
            <span style={{ fontSize:10, color:'#666' }}><strong style={{ color:'#ccc' }}>{likes.length}</strong> Suka</span>
            <span style={{ fontSize:10, color:'#666' }}><strong style={{ color:'#ccc' }}>{history.length}</strong> Riwayat</span>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display:'flex', borderBottom:'1px solid #1a1a1a', padding:'0 14px' }}>
        {tabs.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)} style={{
            flex:1, padding:'10px 0', background:'none', border:'none',
            color: tab===t.key?'#fff':'#555', fontSize:11, fontWeight:700, cursor:'pointer',
            borderBottom: tab===t.key?'2px solid var(--primary)':'2px solid transparent',
          }}>{t.label} ({t.items.length})</button>
        ))}
      </div>

      {/* Items */}
      {active.items.length === 0 ? (
        <div style={{ textAlign:'center', padding:'50px 20px', color:'#333' }}>
          <div style={{ fontSize:32, marginBottom:8 }}>📭</div>
          <p style={{ fontSize:12 }}>Belum ada data</p>
        </div>
      ) : (
        <div style={{ display:'grid', gridTemplateColumns:'repeat(3, 1fr)', gap:8, padding:'12px 14px' }}>
          {active.items.map((item, i) => (
            <div key={i} className="movie-card" style={{ width:'100%' }} onClick={() => handleItem(item)}>
              {item.poster ? (
                <img src={item.poster} alt="" loading="lazy"
                  style={{ width:'100%', aspectRatio:'2/3', objectFit:'cover', display:'block' }}
                  onError={e => { e.target.style.opacity='0.1'; }} />
              ) : (
                <div style={{ width:'100%', aspectRatio:'2/3', background:'#111', display:'flex', alignItems:'center', justifyContent:'center' }}>
                  <i className="fas fa-film" style={{ fontSize:20, color:'#333' }} />
                </div>
              )}
              <div className="card-label" style={{ fontSize:9, padding:'4px 6px' }}>{item.title || 'Untitled'}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
