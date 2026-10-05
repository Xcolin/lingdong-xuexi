import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { menuActionCatalog } from './menuCatalog';

describe('菜单动作种子编码一致性', () => {
  it('使用V91迁移后的唯一按钮编码，前后端动作目录一致', () => {
    const migration = readFileSync(resolve(process.cwd(), '../lingdong-bansui-server/src/main/resources/db/migration/V91__unify_menu_permission_codes.sql'), 'utf8');
    const mappings = [...migration.matchAll(/UPDATE sys_menu SET code = '([^']+)'[^;]+WHERE code = '([^']+)' AND type = 'BUTTON';/g)];
    expect(mappings).toHaveLength(46);
    const codes: string[] = menuActionCatalog.map(action=>action.code);
    for (const [, canonical, legacy] of mappings) {
      expect(codes).not.toContain(legacy);
      if (canonical !== 'IAM_PERMISSION_CREATE') expect(codes).toContain(canonical);
    }
    expect(new Set(codes).size).toBe(codes.length);
    const server = JSON.parse(readFileSync(resolve(process.cwd(), '../lingdong-bansui-server/src/main/resources/web-menu-actions.json'), 'utf8'));
    expect(server).toEqual(menuActionCatalog);
  });
});
