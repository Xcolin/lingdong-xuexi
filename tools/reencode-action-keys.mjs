/**
 * 权限型按钮 actionKey 重编码替换工具（映射表驱动）。
 * 从 V91 迁移解析"旧 actionKey -> 新权限码"映射，批量替换 Web 前端源代码中的引用；
 * 纯 UI 按钮不在映射表中，编码保持不变。
 * 用法：node tools/reencode-action-keys.mjs
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const v91Path = join(repoRoot, 'lingdong-bansui-server/src/main/resources/db/migration/V91__unify_menu_permission_codes.sql');
const webSrc = join(repoRoot, 'lingdong-bansui-web/src');

// 1. 从 V91 解析权限型按钮重编码映射（仅 type='BUTTON' 行）
const sql = readFileSync(v91Path, 'utf8');
const mapping = [];
const linePattern = /UPDATE sys_menu SET code = '([^']+)', permission_code = '\1', grantable = 1 WHERE code = '([^']+)' AND type = 'BUTTON';/g;
for (const match of sql.matchAll(linePattern)) {
  mapping.push({ newCode: match[1], oldKey: match[2] });
}
if (mapping.length === 0) throw new Error('未从 V91 解析到映射，正则需检查');
console.log(`parsed ${mapping.length} mappings from V91`);

// 2. 收集前端源代码文件
function walk(dir) {
  const entries = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) entries.push(...walk(full));
    else if (['.ts', '.tsx'].includes(extname(full))) entries.push(full);
  }
  return entries;
}
const files = walk(webSrc);

// 3. 逐映射替换带引号的完整 code（旧 actionKey 均为独立字符串字面量）
let changedFiles = 0;
let totalReplacements = 0;
const details = [];
for (const file of files) {
  let content = readFileSync(file, 'utf8');
  let fileChanges = 0;
  for (const { newCode, oldKey } of mapping) {
    for (const quote of ["'", '"']) {
      const pattern = quote + oldKey + quote;
      while (content.includes(pattern)) {
        content = content.replace(pattern, quote + newCode + quote);
        fileChanges += 1;
        details.push(`${file.replace(webSrc, 'src')}: ${oldKey} -> ${newCode}`);
      }
    }
  }
  if (fileChanges > 0) {
    writeFileSync(file, content);
    changedFiles += 1;
    totalReplacements += fileChanges;
  }
}
console.log(`changed files: ${changedFiles}, replacements: ${totalReplacements}`);
writeFileSync(join(repoRoot, '.local-verification/actionkey-reencode-details.log'), details.join('\n') + '\n');
console.log('details written to .local-verification/actionkey-reencode-details.log');

// 4. 校验：前端不应再引用任何旧 actionKey
const leftovers = [];
for (const file of files) {
  const content = readFileSync(file, 'utf8');
  for (const { oldKey } of mapping) {
    if (content.includes(oldKey)) leftovers.push(`${file.replace(webSrc, 'src')}: ${oldKey}`);
  }
}
if (leftovers.length) {
  console.error('LEFTOVER old actionKey references:');
  console.error(leftovers.join('\n'));
  process.exit(1);
}
console.log('no leftover old actionKey references');
