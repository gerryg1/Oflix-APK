'use client';
import { Suspense } from 'react';
import DonghuaDetailPage from '@/components/pages/DonghuaDetail';

export default function Page() {
  return (
    <Suspense fallback={
      <div style={{ minHeight: '100vh', background: '#0a0a0f', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div className="spinner" />
      </div>
    }>
      <DonghuaDetailPage />
    </Suspense>
  );
}
