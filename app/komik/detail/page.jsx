import { Suspense } from 'react';
import KomikDetail from '@/components/pages/KomikDetail';

export const metadata = {
  title: 'Baca Komik - Oflix',
  description: 'Baca komik pilihan di Oflix.',
};

export default function Page() {
  return (
    <Suspense fallback={
      <div style={{ minHeight: '100vh', background: '#0a0a0f', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div className="spinner" />
      </div>
    }>
      <KomikDetail />
    </Suspense>
  );
}
