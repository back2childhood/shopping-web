'use client';

import { useEffect, useState } from 'react';
import { getWorkspacePath, readSession } from '@/lib/session';
import type { AuthSession } from '@/lib/types';

type RequiredRole = 'SELLER' | 'BUYER';

export function useRequiredSession(requiredRole: RequiredRole) {
  const [session, setSession] = useState<AuthSession | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const stored = readSession();
      if (!stored) {
        window.location.replace('/login');
        return;
      }

      const hasRequiredRole = requiredRole === 'SELLER' ? stored.isSeller : !stored.isSeller;
      if (!hasRequiredRole) {
        window.location.replace(getWorkspacePath(stored));
        return;
      }
      setSession(stored);
    }, 0);

    return () => window.clearTimeout(timer);
  }, [requiredRole]);

  return session;
}
