'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { CustomerNav } from '@/components/CustomerNav';
import { LoadingScreen } from '@/components/LoadingScreen';
import { PageHeader } from '@/components/PageHeader';
import { useRequiredSession } from '@/hooks/useRequiredSession';
import { api } from '@/lib/api';
import type { Item, Order } from '@/lib/types';

export default function ShopPage() {
  const router = useRouter();
  const session = useRequiredSession('BUYER');
  const [items, setItems] = useState<Item[]>([]);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!session) return;
    api<Item[]>('/api/items', {}, session.token).then(setItems)
      .catch(cause => setError(cause instanceof Error ? cause.message : 'Could not load products'));
  }, [session]);

  const selected = useMemo(
    () => items.filter(item => (quantities[item.id] || 0) > 0),
    [items, quantities],
  );
  const total = selected.reduce((sum, item) => sum + item.price * quantities[item.id], 0);

  if (!session) return <LoadingScreen message="Loading the shop…" />;

  const placeOrder = async () => {
    setPending(true);
    setError('');
    try {
      const result = await api<Order>('/api/orders', {
        method: 'POST',
        body: JSON.stringify({
          userId: session.userId,
          items: selected.map(item => ({ itemId: item.id, quantity: quantities[item.id] })),
        }),
      }, session.token);
      router.push(`/orders?created=${result.id}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not place order');
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="app-shell">
      <PageHeader title="Thoughtful goods, ready to ship." label="Customer shop" email={session.email} />
      <CustomerNav active="shop" />
      <div className="shop-grid">
        <section className="catalog-surface">
          <div className="section-heading">
            <div><p className="eyebrow">Current collection</p><h2>Browse products</h2></div>
            <span className="muted">{items.length} products</span>
          </div>
          {error && <p className="error-message">{error}</p>}
          <div className="product-grid">
            {items.map((item, index) => (
              <article className="product-card" key={item.id}>
                <div className={`product-visual tone-${index % 4}`}><span>{item.name.slice(0, 1)}</span></div>
                <div className="product-body">
                  <div><h3>{item.name}</h3><p>{item.description || 'A carefully selected marketplace product.'}</p></div>
                  <div className="product-footer">
                    <div><strong>{item.currency} {Number(item.price).toFixed(2)}</strong><small>{item.stock} in stock</small></div>
                    <input
                      aria-label={`Quantity for ${item.name}`}
                      type="number"
                      min="0"
                      max={item.stock}
                      value={quantities[item.id] || 0}
                      onChange={event => setQuantities({ ...quantities, [item.id]: Number(event.target.value) })}
                    />
                  </div>
                </div>
              </article>
            ))}
          </div>
          {!items.length && !error && <p className="empty-state">No products have been published yet.</p>}
        </section>

        <aside className="surface order-summary">
          <p className="eyebrow">Your order</p>
          <h2>Order summary</h2>
          {selected.length ? selected.map(item => (
            <div className="summary-line" key={item.id}>
              <span>{quantities[item.id]} × {item.name}</span>
              <strong>{(item.price * quantities[item.id]).toFixed(2)}</strong>
            </div>
          )) : <p className="muted summary-empty">Choose a quantity to add a product.</p>}
          <div className="summary-total"><span>Total</span><strong>{selected[0]?.currency ?? 'USD'} {total.toFixed(2)}</strong></div>
          <button className="primary-button" disabled={!selected.length || pending} onClick={placeOrder}>
            {pending ? 'Placing order…' : selected.length ? 'Place order' : 'Select an item first'}
          </button>
        </aside>
      </div>
    </main>
  );
}
