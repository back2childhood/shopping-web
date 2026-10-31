import type { ComponentProps } from 'react';

// Use document navigation until Vinext's production client router is reliable.
// This also preserves normal link behavior without JavaScript.
export function PageLink(props: ComponentProps<'a'>) {
  return <a {...props} />;
}
