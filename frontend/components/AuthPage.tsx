'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { getWorkspacePath, readSession, saveSession } from '@/lib/session';
import type { AuthSession } from '@/lib/types';

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const router = useRouter();
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const session = readSession();
      if (session) router.replace(getWorkspacePath(session));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [router]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setPending(true);
    const data = new FormData(event.currentTarget);
    const body = mode === 'login'
      ? { email: data.get('email'), password: data.get('password') }
      : {
          email: data.get('email'),
          username: data.get('username'),
          password: data.get('password'),
          shippingAddress: data.get('shippingAddress'),
          billingAddress: data.get('billingAddress'),
          isSeller: data.get('isSeller') === 'on',
        };

    try {
      const session = await api<AuthSession>(`/api/auth/${mode}`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      saveSession(session);
      router.replace(getWorkspacePath(session));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to continue');
    } finally {
      setPending(false);
    }
  };

  const isLogin = mode === 'login';
  return (
    <main className="auth-shell">
      <section className="brand-panel">
        <div className="brand-mark">S</div>
        <p className="eyebrow">Shopping Platform</p>
        <h1>Commerce built around every buyer and seller.</h1>
        <p className="brand-copy">
          One account, two focused workspaces. Sellers control catalog and inventory while
          buyers browse live product data and place orders through the same gateway.
        </p>
        <div className="architecture-note">
          <span>PostgreSQL</span><span>Cassandra</span><span>Redis</span>
        </div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <p className="eyebrow">{isLogin ? 'Welcome back' : 'Create an account'}</p>
          <h2>{isLogin ? 'Sign in to continue' : 'Join the marketplace'}</h2>
          <p className="muted">
            {isLogin
              ? 'Your account role decides which workspace opens.'
              : 'Choose a seller account to open the management workspace.'}
          </p>

          <form onSubmit={submit}>
            {!isLogin && (
              <label>
                Display name
                <input name="username" required placeholder="Alex Morgan" />
              </label>
            )}
            <label>
              Email address
              <input name="email" type="email" required placeholder="alex@example.com" />
            </label>
            <label>
              Password
              <input name="password" type="password" minLength={8} required placeholder="At least 8 characters" />
            </label>
            {!isLogin && (
              <>
                <label>
                  Shipping address
                  <input name="shippingAddress" placeholder="123 Market Street" />
                </label>
                <label>
                  Billing address
                  <input name="billingAddress" placeholder="Same as shipping" />
                </label>
                <label className="check-row">
                  <input name="isSeller" type="checkbox" />
                  <span>I am registering as a seller</span>
                </label>
              </>
            )}
            {error && <p className="error-message">{error}</p>}
            <button className="primary-button" disabled={pending}>
              {pending ? 'Please wait…' : isLogin ? 'Sign in' : 'Create account'}
            </button>
          </form>

          <Link className="text-button auth-link" href={isLogin ? '/register' : '/login'}>
            {isLogin ? 'Need an account? Register' : 'Already registered? Sign in'}
          </Link>
        </div>
      </section>
    </main>
  );
}
