'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { getWorkspacePath, readSession } from '@/lib/session';
import type { AuthSession } from '@/lib/types';

type RequiredRole = 'SELLER' | 'BUYER';

export function useRequiredSession(requiredRole: RequiredRole) {
  const router = useRouter();
  const [session, setSession] = useState<AuthSession | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const stored = readSession();
      if (!stored) {
        router.replace('/login');
        return;
      }

      const hasRequiredRole = requiredRole === 'SELLER' ? stored.isSeller : !stored.isSeller;
      if (!hasRequiredRole) {
        router.replace(getWorkspacePath(stored));
        return;
      }
      setSession(stored);
    }, 0);

    return () => window.clearTimeout(timer);
  }, [requiredRole, router]);

  return session;
}
