import { isAuxiliaryAction } from '../app/auxiliaryActions';
import { forwardRef } from 'react';
import { Button, type ButtonProps } from 'antd';
import { configuredAccess, useMenuConfiguration } from '../app/MenuConfiguration';
import { menuActionCatalog } from '../app/menuCatalog';
import { Check, Eye, Link, LockKeyhole, Pencil, Plus, Power, Save, ShieldCheck, Trash2, X } from 'lucide-react';

function actionAppearance(name: string) {
  if (/删除|注销|清空/.test(name)) return { tone: 'danger', Icon: Trash2 };
  if (/停用|禁用|锁定/.test(name)) return { tone: 'danger', Icon: LockKeyhole };
  if (/驳回|拒绝/.test(name)) return { tone: 'danger', Icon: X };
  if (/批准|通过/.test(name)) return { tone: 'success', Icon: Check };
  if (/关联|绑定/.test(name)) return { tone: 'associate', Icon: Link };
  if (/授权|授予|权限|管理员|密码/.test(name)) return { tone: 'authorize', Icon: ShieldCheck };
  if (/编辑|修改|调整/.test(name)) return { tone: 'edit', Icon: Pencil };
  if (/启用|解锁/.test(name)) return { tone: 'success', Icon: Power };
  if (/保存|提交/.test(name)) return { tone: 'success', Icon: Save };
  if (/新增|添加|创建/.test(name)) return { tone: 'success', Icon: Plus };
  if (/查看|详情/.test(name)) return { tone: 'view', Icon: Eye };
  return undefined;
}

/** Configuration only narrows visible actions; existing business guards still authorize execution. */
export const ConfiguredButton = forwardRef<HTMLAnchorElement | HTMLButtonElement, ButtonProps & { actionKey: string; preserveLabel?: boolean }>(function ConfiguredButton({ actionKey, children, preserveLabel = false, ...props }, ref) {
  const configuration = useMenuConfiguration();
  const node = configuration?.nodes.find(item => item.type !== 'DIRECTORY' && (item.code === actionKey || item.permissionCode === actionKey));
  const auxiliary = isAuxiliaryAction(actionKey);
  const hidden = !auxiliary && !configuredAccess(configuration, actionKey);
  if (hidden) return <span className="configured-action" data-menu-code={auxiliary ? undefined : actionKey} hidden />;
  const definition = menuActionCatalog.find(item => item.code === actionKey);
  const original = definition?.name;
  const renamed = !preserveLabel && !auxiliary && node?.type === 'BUTTON' && !node.name.includes(' · ') && node.name !== original && node.name !== definition?.label;
  const appearance = actionAppearance(definition?.label ?? original ?? (typeof children === 'string' ? children : ''));
  const Icon = appearance?.Icon;
  return <Button {...props} icon={props.icon ?? (Icon ? <Icon size={14} aria-hidden="true" /> : undefined)} ref={ref} className={['configured-action', appearance && `action-tone-${appearance.tone}`, props.className].filter(Boolean).join(' ')} data-menu-code={auxiliary ? undefined : actionKey}
    aria-label={renamed ? node.name : props['aria-label'] ?? (auxiliary && typeof children === 'string' ? children : undefined)}>{renamed ? node.name : children}</Button>;
});
