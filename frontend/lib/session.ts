import type { AuthSession } from '@/lib/types';

const SESSION_KEY = 'shopping-session';

export function readSession(): AuthSession | null {
  if (typeof window === 'undefined') return null;

  const stored = window.localStorage.getItem(SESSION_KEY);
  if (!stored) return null;

  try {
    const session = JSON.parse(stored) as Partial<AuthSession>;
    if (!session.token || !session.userId || !session.email || typeof session.isSeller !== 'boolean') {
      window.localStorage.removeItem(SESSION_KEY);
      return null;
    }
    return session as AuthSession;
  } catch {
    window.localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function saveSession(session: AuthSession) {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_KEY);
}

export function getWorkspacePath(session: AuthSession) {
  return session.isSeller ? '/seller/items' : '/shop';
}
