import { Modal, type ModalProps, type ModalFuncProps } from 'antd';
import type { ComponentType } from 'react';
import { configuredAccess, getMenuConfigurationSnapshot, MenuConfigurationScope, useMenuConfiguration, type MenuConfiguration } from '../app/MenuConfiguration';
import { menuActionCatalog } from '../app/menuCatalog';

export type ConfiguredModalProps = ModalProps & { actionPrefix?: string };
type ConfiguredConfirmProps = ModalFuncProps & { actionPrefix?: string };
function FooterAction({ code, Button }: { code: string; Button: ComponentType }) {
  const configuration = useMenuConfiguration();
  const node = configuration?.nodes.find(item => item.type === 'BUTTON' && item.code === code);
  if (!configuredAccess(configuration, code)) return null;
  return <span className="configured-action" data-menu-code={code} style={{ display: 'inline-flex', order: node?.sortOrder }}><Button /></span>;
}
function configuredProps<T extends ModalProps | ModalFuncProps>(props: T, prefix: string | undefined, configuration: MenuConfiguration | null): T {
  if (!prefix || props.footer !== undefined) return props;
  const actionCode = /^[A-Z][A-Z0-9_]*$/.test(prefix) ? prefix : `${prefix}.ok`;
  const name = (suffix: string) => {
    if (suffix === 'cancel') return undefined;
    const code = actionCode;
    const configured = configuration?.nodes.find(item => item.type === 'BUTTON' && item.code === code)?.name;
    const original = menuActionCatalog.find(item => item.code === code)?.name ?? (suffix === 'ok' ? '确定' : '取消');
    // Unchanged catalog labels must not replace a business-supplied dynamic label.
    return configured && !configured.includes(' · ') && configured !== original ? configured : undefined;
  };
  return { ...props, okText: name('ok') ?? props.okText, cancelText: props.cancelText ?? '取消',
    footer: (_origin, { OkBtn, CancelBtn }) => <MenuConfigurationScope value={configuration}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        <CancelBtn />
        <FooterAction code={actionCode} Button={OkBtn} />
      </div>
    </MenuConfigurationScope> };
}
function ConfiguredModalView({ actionPrefix, ...props }: ConfiguredModalProps) {
  const configuration = useMenuConfiguration();
  return <Modal {...configuredProps(props, actionPrefix, configuration)} />;
}
function confirm({ actionPrefix, ...props }: ConfiguredConfirmProps) {
  return Modal.confirm(configuredProps(props, actionPrefix, getMenuConfigurationSnapshot()));
}
export const ConfiguredModal = Object.assign(ConfiguredModalView, Modal, { confirm });
