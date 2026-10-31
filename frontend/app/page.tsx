'use client';

import { useEffect } from 'react';
import { LoadingScreen } from '@/components/LoadingScreen';
import { getWorkspacePath, readSession } from '@/lib/session';

export default function Home() {

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const session = readSession();
      window.location.replace(session ? getWorkspacePath(session) : '/login');
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  return <LoadingScreen message="Opening your workspace…" />;
}
