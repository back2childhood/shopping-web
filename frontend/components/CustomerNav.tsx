import { PageLink } from '@/components/PageLink';

export function CustomerNav({ active }: { active: 'shop' | 'orders' }) {
  return (
    <nav className="customer-nav" aria-label="Customer navigation">
      <PageLink className={active === 'shop' ? 'active' : ''} aria-current={active === 'shop' ? 'page' : undefined} href="/shop">
        Shop
      </PageLink>
      <PageLink className={active === 'orders' ? 'active' : ''} aria-current={active === 'orders' ? 'page' : undefined} href="/orders">
        My orders
      </PageLink>
    </nav>
  );
}
