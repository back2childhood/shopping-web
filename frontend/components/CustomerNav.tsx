import Link from 'next/link';

export function CustomerNav({ active }: { active: 'shop' | 'orders' }) {
  return (
    <nav className="customer-nav" aria-label="Customer navigation">
      <Link className={active === 'shop' ? 'active' : ''} aria-current={active === 'shop' ? 'page' : undefined} href="/shop">
        Shop
      </Link>
      <Link className={active === 'orders' ? 'active' : ''} aria-current={active === 'orders' ? 'page' : undefined} href="/orders">
        My orders
      </Link>
    </nav>
  );
}
