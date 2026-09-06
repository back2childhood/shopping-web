'use client';

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

type AuthSession = {
  token: string;
  userId: number;
  email: string;
  role: 'SELLER' | 'BUYER';
  isSeller: boolean;
};

type Item = {
  id: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  stock: number;
};

type Order = {
  id: number;
  totalPrice: number;
  currency: string;
  status: string;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

const api = async <T,>(path: string, options: RequestInit = {}, token?: string): Promise<T> => {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || body?.detail || `Request failed (${response.status})`);
  }
  return response.json();
};

export default function Home() {
  const [session, setSession] = useState<AuthSession | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const stored = localStorage.getItem('shopping-session');
      if (stored) setSession(JSON.parse(stored));
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  const onAuthenticated = (next: AuthSession) => {
    localStorage.setItem('shopping-session', JSON.stringify(next));
    setSession(next);
  };

  const logout = () => {
    localStorage.removeItem('shopping-session');
    setSession(null);
  };

  if (!session) {
    return <AuthPage onAuthenticated={onAuthenticated} />;
  }

  return session.isSeller
    ? <SellerPage session={session} onLogout={logout} />
    : <ShopPage session={session} onLogout={logout} />;
}

function AuthPage({ onAuthenticated }: { onAuthenticated: (session: AuthSession) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

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
      const result = await api<AuthSession>(`/api/auth/${mode}`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      onAuthenticated(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to continue');
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="auth-shell">
      <section className="brand-panel">
        <div className="brand-mark">S</div>
        <p className="eyebrow">Shopping Platform</p>
        <h1>A small store built like a real system.</h1>
        <p className="brand-copy">
          One account, two experiences. Sellers manage inventory while shoppers browse
          live catalog data and place orders through the same gateway.
        </p>
        <div className="architecture-note">
          <span>PostgreSQL</span><span>Cassandra</span><span>Redis</span>
        </div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <p className="eyebrow">{mode === 'login' ? 'Welcome back' : 'Create an account'}</p>
          <h2>{mode === 'login' ? 'Sign in to continue' : 'Join the marketplace'}</h2>
          <p className="muted">
            {mode === 'login'
              ? 'Your account role decides which workspace opens.'
              : 'Choose a seller account to open the management workspace.'}
          </p>

          <form onSubmit={submit}>
            {mode === 'register' && (
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
            {mode === 'register' && (
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
              {pending ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
            </button>
          </form>

          <button className="text-button" onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login');
            setError('');
          }}>
            {mode === 'login' ? 'Need an account? Register' : 'Already registered? Sign in'}
          </button>
        </div>
      </section>
    </main>
  );
}

function PageHeader({ title, label, email, onLogout }: { title: string; label: string; email: string; onLogout: () => void }) {
  return (
    <header className="app-header">
      <div>
        <div className="brand-lockup"><span className="brand-mark small">S</span> SHOPPING</div>
        <p className="workspace-label">{label}</p>
      </div>
      <div className="header-actions">
        <span className="muted">{email}</span>
        <span className="status-pill"><i /> Services connected</span>
        <button className="secondary-button" onClick={onLogout}>Sign out</button>
      </div>
      <h1>{title}</h1>
    </header>
  );
}

function SellerPage({ session, onLogout }: { session: AuthSession; onLogout: () => void }) {
  const [items, setItems] = useState<Item[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [pending, setPending] = useState(false);

  const loadItems = useCallback(async () => {
    try {
      setItems(await api<Item[]>('/api/items', {}, session.token));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load products');
    }
  }, [session.token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadItems(), 0);
    return () => window.clearTimeout(timer);
  }, [loadItems]);

  const createItem = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setNotice('');
    setPending(true);
    const form = event.currentTarget;
    const data = new FormData(form);

    try {
      await api<Item>('/api/items', {
        method: 'POST',
        body: JSON.stringify({
          name: data.get('name'),
          description: data.get('description'),
          price: Number(data.get('price')),
          currency: data.get('currency'),
          stock: Number(data.get('stock')),
        }),
      }, session.token);
      form.reset();
      setNotice('Product created and inventory initialized.');
      await loadItems();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not create product');
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="app-shell">
      <PageHeader title="Catalog management" label="Seller workspace" email={session.email} onLogout={onLogout} />
      <section className="metric-strip">
        <div><span>Products</span><strong>{items.length}</strong></div>
        <div><span>Units available</span><strong>{items.reduce((sum, item) => sum + item.stock, 0)}</strong></div>
        <div><span>Role</span><strong>Seller</strong></div>
      </section>

      <div className="seller-grid">
        <section className="surface form-surface">
          <p className="eyebrow">New listing</p>
          <h2>Add a product</h2>
          <form onSubmit={createItem}>
            <label>Product name<input name="name" required placeholder="Mechanical keyboard" /></label>
            <label>Description<textarea name="description" rows={4} placeholder="Describe the product and its key details." /></label>
            <div className="form-row">
              <label>Price<input name="price" type="number" min="0" step="0.01" required placeholder="99.00" /></label>
              <label>Currency<input name="currency" defaultValue="USD" maxLength={3} required /></label>
            </div>
            <label>Opening stock<input name="stock" type="number" min="0" required placeholder="20" /></label>
            {error && <p className="error-message">{error}</p>}
            {notice && <p className="success-message">{notice}</p>}
            <button className="primary-button" disabled={pending}>{pending ? 'Creating…' : 'Publish product'}</button>
          </form>
        </section>

        <section className="surface catalog-surface">
          <div className="section-heading">
            <div><p className="eyebrow">Live catalog</p><h2>Your products</h2></div>
            <button className="secondary-button" onClick={() => void loadItems()}>Refresh</button>
          </div>
          <ProductList items={items} emptyText="Create your first product to see it here." />
        </section>
      </div>
    </main>
  );
}

function ShopPage({ session, onLogout }: { session: AuthSession; onLogout: () => void }) {
  const [items, setItems] = useState<Item[]>([]);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  useEffect(() => {
    api<Item[]>('/api/items', {}, session.token).then(setItems)
      .catch(cause => setError(cause instanceof Error ? cause.message : 'Could not load products'));
  }, [session.token]);

  const selected = useMemo(() => items.filter(item => (quantities[item.id] || 0) > 0), [items, quantities]);
  const total = selected.reduce((sum, item) => sum + item.price * quantities[item.id], 0);

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
      setOrder(result);
      setQuantities({});
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not place order');
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="app-shell">
      <PageHeader title="Thoughtful goods, ready to ship." label="Customer shop" email={session.email} onLogout={onLogout} />
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
                      type="number" min="0" max={item.stock}
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
            {pending ? 'Placing order…' : 'Place order'}
          </button>
          {order && <div className="order-confirmation"><strong>Order #{order.id}</strong><span>{order.status} · {order.currency} {order.totalPrice}</span></div>}
        </aside>
      </div>
    </main>
  );
}

function ProductList({ items, emptyText }: { items: Item[]; emptyText: string }) {
  if (!items.length) return <p className="empty-state">{emptyText}</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Product</th><th>Price</th><th>Stock</th><th>ID</th></tr></thead>
        <tbody>{items.map(item => (
          <tr key={item.id}>
            <td><strong>{item.name}</strong><span>{item.description}</span></td>
            <td>{item.currency} {Number(item.price).toFixed(2)}</td>
            <td><span className={item.stock > 0 ? 'stock-ok' : 'stock-empty'}>{item.stock}</span></td>
            <td><code>{item.id.slice(0, 8)}</code></td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}
