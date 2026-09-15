'use client';

import { FormEvent, useCallback, useEffect, useState } from 'react';
import { LoadingScreen } from '@/components/LoadingScreen';
import { PageHeader } from '@/components/PageHeader';
import { useRequiredSession } from '@/hooks/useRequiredSession';
import { api } from '@/lib/api';
import type { Item, ItemInput } from '@/lib/types';

export default function SellerItemsPage() {
  const session = useRequiredSession('SELLER');
  const [items, setItems] = useState<Item[]>([]);
  const [editingItem, setEditingItem] = useState<Item | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [pending, setPending] = useState(false);

  const loadItems = useCallback(async () => {
    if (!session) return;
    setError('');
    try {
      setItems(await api<Item[]>('/api/items/mine', {}, session.token));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load products');
    }
  }, [session]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadItems(), 0);
    return () => window.clearTimeout(timer);
  }, [loadItems]);

  if (!session) return <LoadingScreen message="Loading seller workspace…" />;

  const submitItem = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setNotice('');
    setPending(true);
    const form = event.currentTarget;
    const data = new FormData(form);
    const input: ItemInput = {
      name: String(data.get('name') ?? ''),
      description: String(data.get('description') ?? ''),
      price: Number(data.get('price')),
      currency: String(data.get('currency') ?? '').toUpperCase(),
      stock: Number(data.get('stock')),
    };

    try {
      if (editingItem) {
        await api<Item>(`/api/items/${editingItem.id}`, {
          method: 'PUT',
          body: JSON.stringify(input),
        }, session.token);
        setNotice(`${input.name} was updated.`);
        setEditingItem(null);
      } else {
        await api<Item>('/api/items', {
          method: 'POST',
          body: JSON.stringify(input),
        }, session.token);
        form.reset();
        setNotice('Product created and inventory initialized.');
      }
      await loadItems();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not save product');
    } finally {
      setPending(false);
    }
  };

  const startEditing = (item: Item) => {
    setEditingItem(item);
    setError('');
    setNotice('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <main className="app-shell">
      <PageHeader title="Catalog management" label="Seller workspace" email={session.email} />
      <section className="metric-strip">
        <div><span>Products</span><strong>{items.length}</strong></div>
        <div><span>Units available</span><strong>{items.reduce((sum, item) => sum + item.stock, 0)}</strong></div>
        <div><span>Role</span><strong>Seller</strong></div>
      </section>

      <div className="seller-grid">
        <section className="surface form-surface">
          <p className="eyebrow">{editingItem ? 'Edit listing' : 'New listing'}</p>
          <h2>{editingItem ? 'Update product' : 'Add a product'}</h2>
          {editingItem && (
            <p className="edit-context">
              Editing <strong>{editingItem.name}</strong>. Change the current stock value to restock this item.
            </p>
          )}
          <form key={editingItem?.id ?? 'new-item'} onSubmit={submitItem}>
            <label>
              Product name
              <input name="name" required defaultValue={editingItem?.name ?? ''} placeholder="Mechanical keyboard" />
            </label>
            <label>
              Description
              <textarea name="description" rows={4} defaultValue={editingItem?.description ?? ''} placeholder="Describe the product and its key details." />
            </label>
            <div className="form-row">
              <label>
                Price
                <input name="price" type="number" min="0" step="0.01" required defaultValue={editingItem?.price ?? ''} placeholder="99.00" />
              </label>
              <label>
                Currency
                <input name="currency" defaultValue={editingItem?.currency ?? 'USD'} maxLength={3} required />
              </label>
            </div>
            <label>
              {editingItem ? 'Available stock' : 'Opening stock'}
              <input name="stock" type="number" min="0" required defaultValue={editingItem?.stock ?? ''} placeholder="20" />
              {editingItem && <small className="form-help">Current stock is {editingItem.stock}. Enter the new available quantity.</small>}
            </label>
            {error && <p className="error-message">{error}</p>}
            {notice && <p className="success-message">{notice}</p>}
            <div className="form-actions">
              <button className="primary-button" disabled={pending}>
                {pending ? 'Saving…' : editingItem ? 'Save changes' : 'Publish product'}
              </button>
              {editingItem && (
                <button type="button" className="secondary-button" disabled={pending} onClick={() => setEditingItem(null)}>
                  Cancel
                </button>
              )}
            </div>
          </form>
        </section>

        <section className="surface catalog-surface">
          <div className="section-heading">
            <div><p className="eyebrow">Live catalog</p><h2>Your products</h2></div>
            <button className="secondary-button" onClick={() => void loadItems()}>Refresh</button>
          </div>
          <SellerProductList items={items} onEdit={startEditing} />
        </section>
      </div>
    </main>
  );
}

function SellerProductList({ items, onEdit }: { items: Item[]; onEdit: (item: Item) => void }) {
  if (!items.length) return <p className="empty-state">Create your first product to see it here.</p>;

  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Product</th><th>Price</th><th>Stock</th><th>ID</th><th>Actions</th></tr></thead>
        <tbody>{items.map(item => (
          <tr key={item.id}>
            <td><strong>{item.name}</strong><span>{item.description}</span></td>
            <td>{item.currency} {Number(item.price).toFixed(2)}</td>
            <td><span className={item.stock > 0 ? 'stock-ok' : 'stock-empty'}>{item.stock}</span></td>
            <td><code>{item.id.slice(0, 8)}</code></td>
            <td><button className="table-action" onClick={() => onEdit(item)}>Edit price &amp; stock</button></td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}
