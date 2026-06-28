import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useState, useEffect } from 'react';

export default function BottomNav({ onAccountClick }) {
  const nav  = useNavigate();
  const loc  = useLocation();
  const { user } = useAuth();
  const [installPrompt, setInstallPrompt] = useState(null);
  const [isInstalled, setIsInstalled]     = useState(false);

  useEffect(() => {
    // Already installed as PWA
    if (window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone) {
      setIsInstalled(true);
      return;
    }
    function onPrompt(e) {
      e.preventDefault();
      setInstallPrompt(e);
    }
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', () => { setIsInstalled(true); setInstallPrompt(null); });
    return () => window.removeEventListener('beforeinstallprompt', onPrompt);
  }, []);

  async function handleInstall() {
    if (installPrompt) {
      installPrompt.prompt();
      const result = await installPrompt.userChoice;
      if (result.outcome === 'accepted') setIsInstalled(true);
      setInstallPrompt(null);
    } else {
      const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent);
      if (isIOS) {
        alert('Tap tombol Share (kotak dengan panah) di Safari, lalu pilih "Add to Home Screen"');
      } else {
        alert('Klik ikon Install (⊕) di address bar browser, atau buka menu browser → "Install app" / "Add to Home Screen"');
      }
    }
  }

  const items = [
    { icon: 'fas fa-home',    label: 'Beranda', path: '/'       },
    { icon: 'fas fa-search',  label: 'Cari',    path: '/search' },
    { icon: 'fas fa-user',    label: 'Akun',    path: '__account' },
  ];

  function handleClick(item) {
    if (item.path === '__account') {
      onAccountClick?.();
    } else {
      nav(item.path);
    }
  }

  function isActive(item) {
    if (item.path === '__account') return false;
    if (item.path === '/') return loc.pathname === '/';
    return loc.pathname.startsWith(item.path);
  }

  return (
    <nav className="bottom-nav">
      {items.map(item => (
        <button
          key={item.label}
          className={`nav-item ${isActive(item) ? 'active' : ''}`}
          onClick={() => handleClick(item)}
        >
          <i className={item.icon}></i>
          <span>{item.label}</span>
        </button>
      ))}
      {!isInstalled && (
        <button className="nav-item nav-install" onClick={handleInstall} style={{ color: '#e50914' }}>
          <i className="fas fa-download"></i>
          <span>Install</span>
        </button>
      )}
    </nav>
  );
}
