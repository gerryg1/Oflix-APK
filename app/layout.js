import './globals.css';
import './mobile.css';
import ClientProviders from '@/components/ClientProviders';

export const metadata = {
  title: 'Oflix — Nonton Film, Series, Anime & Komik',
  description: 'Platform streaming film, series, anime, donghua, dan baca komik online.',
};

export default function RootLayout({ children }) {
  return (
    <html lang="id">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="theme-color" content="#0f1014" />
        <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
        <link
          rel="stylesheet"
          href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css"
          crossOrigin="anonymous"
        />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,300..900;1,9..40,300..900&display=swap"
          rel="stylesheet"
        />
      </head>
      <body>
        <div id="root">
          <ClientProviders>
            {children}
          </ClientProviders>
        </div>
      </body>
    </html>
  );
}
