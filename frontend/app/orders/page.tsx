'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { CustomerNav } from '@/components/CustomerNav';
import { LoadingScreen } from '@/components/LoadingScreen';
import { PageHeader } from '@/components/PageHeader';
import { useRequiredSession } from '@/hooks/useRequiredSession';
import { api } from '@/lib/api';
import type { Order } from '@/lib/types';

export default function OrdersPage() {
  const session = useRequiredSession('BUYER');
  const [orders, setOrders] = useState<Order[]>([]);
  const [createdOrderId, setCreatedOrderId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(true);

  const loadOrders = useCallback(async () => {
    if (!session) return;
    setPending(true);
    setError('');
    try {
      const result = await api<Order[]>(`/api/orders/user/${session.userId}`, {}, session.token);
      setOrders([...result].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load orders');
    } finally {
      setPending(false);
    }
  }, [session]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const value = new URLSearchParams(window.location.search).get('created');
      if (value && /^\d+$/.test(value)) setCreatedOrderId(Number(value));
      void loadOrders();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadOrders]);

  if (!session) return <LoadingScreen message="Loading your orders…" />;

  const totalUnits = orders.reduce(
    (total, order) => total + order.items.reduce((sum, item) => sum + item.quantity, 0),
    0,
  );

  return (
    <main className="app-shell">
      <PageHeader title="Your orders, clearly organized." label="Customer account" email={session.email} />
      <CustomerNav active="orders" />
      <section className="metric-strip">
        <div><span>Orders</span><strong>{orders.length}</strong></div>
        <div><span>Items purchased</span><strong>{totalUnits}</strong></div>
        <div><span>Latest status</span><strong>{orders[0]?.status ?? '—'}</strong></div>
      </section>

      <section className="surface orders-surface">
        <div className="section-heading">
          <div><p className="eyebrow">Purchase history</p><h2>My orders</h2></div>
          <button className="secondary-button" onClick={() => void loadOrders()} disabled={pending}>Refresh</button>
        </div>

        {createdOrderId !== null && (
          <p className="success-message order-created-message">
            Order #{createdOrderId} was placed successfully and is now being processed.
          </p>
        )}
        {error && <p className="error-message">{error}</p>}
        {pending && <p className="orders-message">Loading your orders…</p>}
        {!pending && !error && !orders.length && (
          <div className="empty-state orders-empty">
            <strong>No orders yet</strong>
            <span>Your completed checkouts will appear here.</span>
            <Link className="secondary-button" href="/shop">Start shopping</Link>
          </div>
        )}

        {!pending && orders.length > 0 && (
          <div className="order-list">
            {orders.map(order => (
              <article className={`order-card${order.id === createdOrderId ? ' newly-created' : ''}`} key={order.id}>
                <header className="order-card-header">
                  <div>
                    <p className="eyebrow">Order #{order.id}</p>
                    <h3>{formatOrderDate(order.createdAt)}</h3>
                  </div>
                  <span className="order-status">{order.status}</span>
                </header>
                <div className="order-lines">
                  {order.items.map(item => (
                    <div className="order-line" key={item.itemId}>
                      <div><strong>{item.itemName}</strong><span>Quantity {item.quantity}</span></div>
                      <strong>{order.currency} {(Number(item.price) * item.quantity).toFixed(2)}</strong>
                    </div>
                  ))}
                </div>
                <footer className="order-card-footer">
                  <span>{order.items.length} {order.items.length === 1 ? 'product' : 'products'}</span>
                  <div><span>Total</span><strong>{order.currency} {Number(order.totalPrice).toFixed(2)}</strong></div>
                </footer>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}

function formatOrderDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Date unavailable';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}
