'use client';
import { Suspense } from 'react';
import DetailPage from '@/components/pages/Detail';

export default function Page() {
  return (
    <Suspense fallback={
      <div style={{ minHeight: '100vh', background: '#0a0a0f', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div className="spinner" />
      </div>
    }>
      <DetailPage />
    </Suspense>
  );
}
