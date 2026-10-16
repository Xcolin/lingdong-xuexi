import fs from 'node:fs';
import path from 'node:path';
import ts from '../lingdong-bansui-web/node_modules/typescript/lib/typescript.js';
const root=path.resolve('lingdong-bansui-web/src');
const parse=f=>ts.createSourceFile(f,fs.readFileSync(f,'utf8'),ts.ScriptTarget.Latest,true,f.endsWith('tsx')?ts.ScriptKind.TSX:ts.ScriptKind.TS);
const files=[];function walk(d){for(const x of fs.readdirSync(d,{withFileTypes:true})){const p=path.join(d,x.name);if(x.isDirectory())walk(p);else if(/\.tsx?$/.test(p)&&!p.includes('.test.'))files.push(p)}}walk(root);
const apiMap={};const actions=[];const guards=[];const prefixes=[];
for(const f of files){const sf=parse(f),relative=path.relative(root,f).replaceAll('\\','/'),line=n=>sf.getLineAndCharacterOfPosition(n.getStart()).line+1;
function visit(n){
if(ts.isVariableDeclaration(n)&&n.initializer&&ts.isObjectLiteralExpression(n.initializer)&&/Api$/.test(n.name.getText(sf))){for(const m of n.initializer.properties){if(!m.name)continue;const endpoints=[];function scan(x){if(ts.isCallExpression(x)&&x.expression.getText(sf).startsWith('apiClient.'))endpoints.push({transport:x.expression.getText(sf),arguments:x.arguments.map(a=>a.getText(sf))});ts.forEachChild(x,scan)}scan(m);apiMap[n.name.getText(sf)+'.'+m.name.getText(sf)]={source:relative,line:line(m),endpoints}}}
if(ts.isJsxAttribute(n)&&['actionKey','actionPrefix'].includes(n.name.getText(sf))){let code=n.initializer&&ts.isStringLiteral(n.initializer)?n.initializer.text:n.initializer?.getText(sf);let parent=n.parent.parent;const context=[];let p=parent.parent;for(let i=0;p&&i<8;i++,p=p.parent){if(ts.isBinaryExpression(p)||ts.isConditionalExpression(p))context.push(p.getText(sf).slice(0,700));}
const row={code,source:relative,line:line(n),element:parent.getText(sf),conditions:context};if(n.name.getText(sf)==='actionKey')actions.push(row);else prefixes.push(row)}
if(ts.isCallExpression(n)&&/\.(includes|some)$/.test(n.expression.getText(sf))&&/(permissionCodes|roleCodes)/.test(n.getText(sf)))guards.push({source:relative,line:line(n),expression:n.getText(sf)});
ts.forEachChild(n,visit)}visit(sf)}
for(const a of actions){const sf=parse(path.join(root,a.source));const calls=[];function v(n){if(ts.isCallExpression(n)&&apiMap[n.expression.getText(sf)])calls.push({method:n.expression.getText(sf),line:sf.getLineAndCharacterOfPosition(n.getStart()).line+1,...apiMap[n.expression.getText(sf)]});ts.forEachChild(n,v)}v(sf);a.sourceApiCandidates=calls}
const catalogFile=fs.readFileSync(path.join(root,'app/menuCatalog.ts'),'utf8');const catalog=JSON.parse(catalogFile.split('export const menuActionCatalog = ')[1].split('] as const')[0]+']');
fs.writeFileSync('docs/auth-audit-frontend-inventory-2026-10-09.json',JSON.stringify({note:'sourceApiCandidates are file-level candidates, not proof of handler binding; exact API call expressions and lines retained for manual verification.',catalog,actions,modalPrefixes:prefixes,apiMap,guards},null,2));
console.log(JSON.stringify({catalog:catalog.length,actions:actions.length,prefixes:prefixes.length,apiMethods:Object.keys(apiMap).length,guards:guards.length}));
console.log(JSON.stringify(catalog.filter(x=>/查询|详情|关闭|退出当前|重新加载|查看关系/.test(x.label)),null,2));
