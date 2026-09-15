import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Shopping Platform',
  description: 'A role-aware microservice marketplace for shoppers and sellers.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
