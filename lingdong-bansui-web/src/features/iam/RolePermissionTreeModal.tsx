import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Modal, Select, Spin, Tree, message } from 'antd';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { iamApi, type Permission, type PermissionAssignment, type Role } from '../../api/iam';
import { menuApi, type MenuNode } from '../../api/menus';
import { ApiRequestError } from '../../api/http';
import { buildRolePermissionTree, treeCheckState, togglePermissionBranch, type PermissionTreeNode } from './rolePermissionTree';

interface Props { open: boolean; roles: Role[]; role?:Role; permissions: Permission[]; onClose: () => void; }
export function RolePermissionTreeModal({ open, roles, role, permissions, onClose }: Props) {
  const [roleId, setRoleId] = useState<string>();
  const [menus, setMenus] = useState<MenuNode[]>([]);
  const [assignments, setAssignments] = useState<PermissionAssignment[]>([]);
  const [selected, setSelected] = useState(new Set<string>());
  const [loading, setLoading] = useState(false);
  const [ready, setReady] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const request = useRef(0);
  const saveLock = useRef(false);
  useEffect(() => {
    if (!open) { request.current++; setRoleId(undefined); setReady(false); setAssignments([]); setMenus([]); setSelected(new Set()); setError(undefined); }
  }, [open]);
  useEffect(() => () => { request.current++; }, []);
  useEffect(()=>{if(open&&role)void load(role.id);},[open,role?.id]);
  const tree = useMemo(() => buildRolePermissionTree(menus, permissions, assignments), [menus, permissions, assignments]);
  const checked = useMemo(() => treeCheckState(tree, selected), [tree, selected]);
  async function load(id: string) {
    const version = ++request.current;
    setRoleId(id); setReady(false); setLoading(true); setError(undefined); setMenus([]); setAssignments([]); setSelected(new Set());
    try {
      const [nodes, current] = await Promise.all([
        menuApi.list().catch(error => {
          if (error instanceof ApiRequestError && error.status === 403) return iamApi.listRolePermissionMenus();
          throw error;
        }), iamApi.listRolePermissions(id)
      ]);
      if (request.current !== version) return;
      setMenus(nodes); setAssignments(current); setSelected(new Set(current.filter(a => a.effect === 'ALLOW').map(a => a.permissionId))); setReady(true);
    } catch (error) {
      if (request.current === version) setError(error instanceof Error ? error.message : '权限树加载失败');
    } finally { if (request.current === version) setLoading(false); }
  }
  async function save() {
    if (!roleId || !ready || saveLock.current) return;
    saveLock.current = true; setSaving(true); setError(undefined);
    const version = request.current;
    // Disabled permissions (including existing DENY) remain outside the editable scope.
    const managedPermissionIds = [...new Set(tree.flatMap(node => node.permissionIds))];
    const permissionIds = managedPermissionIds.filter(id => selected.has(id));
    try {
      await iamApi.batchRolePermissions(roleId, { permissionIds, managedPermissionIds, expectedAssignments: assignments });
      if (request.current === version) {
        setAssignments([...assignments.filter(a => !managedPermissionIds.includes(a.permissionId)), ...permissionIds.map(permissionId => ({ permissionId, effect: 'ALLOW' as const }))]);
        message.success('角色权限已保存');
      }
    } catch (error) {
      if (request.current === version) {
        setError(error instanceof Error ? error.message : '角色权限保存失败');
        if (error instanceof ApiRequestError && error.status === 409) setReady(false);
      }
    } finally { saveLock.current = false; setSaving(false); }
  }
  return <Modal title="配置角色权限" centered open={open} onCancel={saving ? undefined : onClose} closable={!saving} maskClosable={!saving} keyboard={!saving} width="min(880px, 92vw)" footer={[
    <Button actionKey="iam.role-permission-tree.cancel" key="cancel" disabled={saving} onClick={onClose}>取消</Button>,
    <Button actionKey="iam.role-permission-tree.save" key="save" type="primary" loading={saving} disabled={!roleId || !ready || loading} onClick={() => void save()}>保存角色权限</Button>
  ]}>
    <label htmlFor="role-permission-subject">角色</label>
    <Select id="role-permission-subject" style={{ width: '100%', marginBottom: 16 }} value={roleId} showSearch optionFilterProp="label" disabled={saving||!!role} options={roles.map(role => ({ value: role.id, label: `${role.name}（${role.code}）` }))} onChange={id => void load(id)} />
    {error && <Alert type="error" showIcon message={error} action={<Button actionKey="iam.role-permission-tree.retry" disabled={saving} onClick={() => roleId && void load(roleId)}>重新加载</Button>} />}
    <Spin spinning={loading}>
      {ready && <Tree<PermissionTreeNode> height={Math.max(180,window.innerHeight-300)} checkable checkStrictly treeData={tree} checkedKeys={checked} disabled={saving} onCheck={(_, info) => setSelected(previous => togglePermissionBranch(info.node, previous, info.checked))} />}
      {!roleId && <p>请先选择角色</p>}
    </Spin>
  </Modal>;
}
