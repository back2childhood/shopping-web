// Creates clearly labelled demo users, a product and an order. Never logs tokens/passwords.
// Run only against the fresh demo deployment, not production customer data.
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';

const base = process.env.SMOKE_BASE_URL ?? 'http://127.0.0.1:8088';
const run = Date.now();
const password = randomBytes(24).toString('hex');
async function api(path, { method = 'GET', body, token } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', Origin: new URL(base).origin,
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(30000),
  });
  assert(response.ok, `${method} ${path} returned ${response.status}`);
  return response.json();
}
async function register(isSeller) {
  const email = `cloud-smoke-${isSeller ? 'seller' : 'buyer'}-${run}@example.com`;
  const registered = await api('/api/auth/register', { method: 'POST', body: {
    email, username: `Cloud smoke ${isSeller ? 'seller' : 'buyer'}`, password,
    isSeller, shippingAddress: 'Demo address', billingAddress: 'Demo address',
  } });
  const login = await api('/api/auth/login', { method: 'POST', body: { email, password } });
  assert.equal(login.userId, registered.userId);
  assert.equal(login.isSeller, isSeller);
  return login;
}

const seller = await register(true);
const buyer = await register(false);
console.log('PASS seller/buyer registration and login');
const input = { name: `Cloud smoke item ${run}`, description: 'Deployment verification product',
  price: 12.5, currency: 'USD', stock: 10 };
const item = await api('/api/items', { method: 'POST', body: input, token: seller.token });
assert.equal(item.userId, seller.userId);
const updated = await api(`/api/items/${item.id}`, { method: 'PUT',
  body: { ...input, price: 15, stock: 15 }, token: seller.token });
assert.equal(updated.stock, 15);
assert.equal(Number(updated.price), 15);
const mine = await api('/api/items/mine', { token: seller.token });
assert(mine.some(row => row.id === item.id));
console.log('PASS seller create/update and own listing');
for (let i = 0; i < 2; i++) {
  const list = await api('/api/items', { token: buyer.token });
  assert(Array.isArray(list));
  const detail = await api(`/api/items/${item.id}`, { token: buyer.token });
  assert.equal(Number(detail.price), 15);
}
console.log('PASS repeated catalog/detail reads (cache serialization path)');
const order = await api('/api/orders', { method: 'POST', token: buyer.token,
  body: { userId: buyer.userId, items: [{ itemId: item.id, quantity: 2 }] } });
assert.equal(Number(order.totalPrice), 30);
const history = await api(`/api/orders/user/${buyer.userId}`, { token: buyer.token });
assert(history.some(row => row.id === order.id));
for (let i = 0; i < 2; i++) {
  const saved = await api(`/api/orders/${order.id}`, { token: buyer.token });
  assert.equal(saved.id, order.id);
}
const remaining = await api(`/api/items/${item.id}`, { token: buyer.token });
assert.equal(remaining.stock, 13);
console.log(`PASS checkout, order history and inventory (order=${order.id}, item=${item.id})`);
console.log('Demo records intentionally retained; no customer data was modified.');
