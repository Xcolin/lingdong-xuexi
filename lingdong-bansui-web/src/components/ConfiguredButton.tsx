import { forwardRef } from 'react';
import { Button, type ButtonProps } from 'antd';
import { useMenuConfiguration } from '../app/MenuConfiguration';
import { menuActionCatalog } from '../app/menuCatalog';

/** Configuration only narrows visible actions; existing business guards still authorize execution. */
export const ConfiguredButton = forwardRef<HTMLAnchorElement | HTMLButtonElement, ButtonProps & { actionKey: string }>(function ConfiguredButton({ actionKey, children, ...props }, ref) {
  const configuration = useMenuConfiguration();
  const node = configuration?.nodes.find(item => item.type === 'BUTTON' && item.code === actionKey);
  const hidden = configuration !== null && (!node || node.status !== 'ENABLED');
  if (hidden) return <span className="configured-action" data-menu-code={actionKey} hidden />;
  const original = menuActionCatalog.find(item => item.code === actionKey)?.name;
  const renamed = node && node.name !== original;
  return <Button {...props} ref={ref} className={['configured-action', props.className].filter(Boolean).join(' ')} data-menu-code={actionKey}
    aria-label={renamed ? node.name : props['aria-label']}>{renamed ? node.name : children}</Button>;
});
