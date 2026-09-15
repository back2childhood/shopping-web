'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { LoadingScreen } from '@/components/LoadingScreen';
import { getWorkspacePath, readSession } from '@/lib/session';

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const session = readSession();
      router.replace(session ? getWorkspacePath(session) : '/login');
    }, 0);
    return () => window.clearTimeout(timer);
  }, [router]);

  return <LoadingScreen message="Opening your workspace…" />;
}
