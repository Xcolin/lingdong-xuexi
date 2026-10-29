// Observe top-level test entry promises without treating process exit 0 as completion.
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');
const pending = new Set();
exports.track = function track(value) {
  if (value && typeof value.then === 'function') {
    const observed = Promise.resolve(value);
    pending.add(observed);
    observed.then(() => pending.delete(observed), error => {
      pending.delete(observed);
      process.exitCode = 1;
      console.error(error);
    });
  }
  return value;
};
if (require.main === module) {
  const file = path.resolve(process.argv[2]);
  const source = ts.createSourceFile(file, fs.readFileSync(file, 'utf8'), ts.ScriptTarget.ES2020, true, ts.ScriptKind.JS);
  const f = ts.factory;
  const wrap = expr => ts.isVoidExpression(expr) ? f.createVoidExpression(wrap(expr.expression)) : f.createCallExpression(f.createIdentifier('__ldTrackCompletion'), undefined, [expr]);
  // Only instrument source-file entries. Awaited page/API promises inside those
  // entries remain untouched; intentionally pending mock APIs are not false failures.
  const statements = source.statements.map(statement => {
    if (ts.isExpressionStatement(statement) && !ts.isStringLiteral(statement.expression)) {
      return f.updateExpressionStatement(statement, wrap(statement.expression));
    }
    if (ts.isVariableStatement(statement)) {
      return f.updateVariableStatement(statement, statement.modifiers,
        f.updateVariableDeclarationList(statement.declarationList, statement.declarationList.declarations.map(d =>
          f.updateVariableDeclaration(d, d.name, d.exclamationToken, d.type, d.initializer ? wrap(d.initializer) : undefined))));
    }
    return statement;
  });
  const output = ts.createPrinter().printFile(f.updateSourceFile(source, statements));
  const directives=[];
  for(const statement of source.statements){
    if(!ts.isExpressionStatement(statement)||!ts.isStringLiteral(statement.expression))break;
    directives.push(statement.expression.getText(source)+';');
  }
  const guard = setTimeout(() => {
    console.error('测试级超时：异步入口或活动句柄未结束');
    process.exit(1);
  }, 25000);
  guard.unref();
  let completed = false;
  process.on('beforeExit', () => {
    clearTimeout(guard);
    if (pending.size) {
      console.error(`未完成异步入口：${pending.size}，禁止以退出码 0 签收`);
      process.exitCode = 1;
    } else if (!process.exitCode && !completed) {
      completed = true;
      console.log(`LD_TEST_COMPLETE ${path.basename(file)}`);
    }
  });
  const target = new Module(file);
  target.filename = file;
  target.paths = Module._nodeModulePaths(path.dirname(file));
  require.cache[file]=target;
  process.mainModule=target;
  process.argv=[process.execPath,file,...process.argv.slice(3)];
  target._compile(`${directives.join('\n')}\nconst __ldTrackCompletion = require(${JSON.stringify(__filename)}).track;\n${output}`, file);
}
