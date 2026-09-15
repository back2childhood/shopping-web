'use client';

import { useRouter } from 'next/navigation';
import { clearSession } from '@/lib/session';

export function PageHeader({ title, label, email }: { title: string; label: string; email: string }) {
  const router = useRouter();

  const logout = () => {
    clearSession();
    router.replace('/login');
  };

  return (
    <header className="app-header">
      <div>
        <div className="brand-lockup"><span className="brand-mark small">S</span> SHOPPING</div>
        <p className="workspace-label">{label}</p>
      </div>
      <div className="header-actions">
        <span className="muted">{email}</span>
        <span className="status-pill"><i /> Services connected</span>
        <button className="secondary-button" onClick={logout}>Sign out</button>
      </div>
      <h1>{title}</h1>
    </header>
  );
}
