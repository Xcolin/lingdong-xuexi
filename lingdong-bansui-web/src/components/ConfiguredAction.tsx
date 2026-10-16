import type { PropsWithChildren } from 'react';
import { configuredAccess, useMenuConfiguration } from '../app/MenuConfiguration';

/** Button configuration can narrow an action; it never grants business permission. */
export function ConfiguredAction({ actionKey, permissionCodes, children }: PropsWithChildren<{ actionKey: string; permissionCodes: string[] }>) {
  const configuration = useMenuConfiguration();
  if (!permissionCodes.includes(actionKey)) return null;
  if (!configuredAccess(configuration, actionKey)) return null;
  return <span className="configured-action" data-menu-code={actionKey}>{children}</span>;
}
