# 一键 CI 门禁（R09-6.1）：后端全量测试与打包、Flyway 迁移校验、发布产物检查、
# 敏感信息扫描、Web 类型检查/测试/构建、小程序类型检查与双目标构建。
# 任一步骤失败立即中断并以非零退出码结束；不连接外部数据库/Redis，不发送真实短信或微信消息。
param([switch]$SkipBackend, [switch]$SkipFrontend)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$server = Join-Path $root 'lingdong-xuexi-server'
$web = Join-Path $root 'lingdong-xuexi-web'
$miniapp = Join-Path $root 'lingdong-xuexi-miniapp'
$logDir = Join-Path $root '.local-verification'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Invoke-Step([string]$Name, [scriptblock]$Action) {
    Write-Output "=== CI 步骤开始：$Name ==="
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "CI 步骤失败：$Name（退出码 $LASTEXITCODE），阻断后续步骤。" }
    Write-Output "=== CI 步骤通过：$Name ==="
}

function Test-NoRealSecrets {
    # 敏感信息扫描：源码与配置中不得出现真实私钥、云厂商 AccessKey 或非占位密钥赋值。
    # 占位符（${ENV:}、空串、含 test/placeholder/example 的样例值）不视为泄露。
    $patterns = @(
        '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----',
        'AKID[0-9A-Za-z]{10,}',
        '(?i)(app-?secret|access-?key-?secret|api-?key)\s*[=:]\s*["''][^"''$\r\n]{16,}["'']'
    )
    $targets = Get-ChildItem $server\src, $web\src, (Join-Path $miniapp 'src') -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -match '^\.(java|ts|tsx|vue|yml|yaml|xml|properties|json|ps1)$' }
    $hits = @()
    foreach ($file in $targets) {
        $text = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
        if (-not $text) { continue }
        foreach ($pattern in $patterns) {
            foreach ($match in [regex]::Matches($text, $pattern)) {
                $value = $match.Value
                if ($value -match '(?i)test|placeholder|example|sample|dummy') { continue }
                $hits += "$($file.FullName): $value"
            }
        }
    }
    if ($hits.Count -gt 0) { throw ("敏感信息扫描发现疑似真实密钥：`n" + ($hits -join "`n")) }
    Write-Output "敏感信息扫描通过：$($targets.Count) 个文件未发现真实密钥。"
}

if (-not $SkipBackend) {
    Invoke-Step '后端全量测试与打包（含全部测试）' {
        Push-Location $server
        try { mvn package '-DforkCount=0' 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'ci-backend-package.log') | Select-Object -Last 5 }
        finally { Pop-Location }
    }
    Invoke-Step 'Flyway 迁移校验（真实迁移建表核对）' {
        Push-Location $server
        try { mvn '-Dtest=FlywayMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'ci-flyway-migration.log') | Select-String -Pattern 'Tests run: \d+, Fail' | Select-Object -Last 1 }
        finally { Pop-Location }
    }
    Invoke-Step '发布产物检查（JAR 无本地配置）' {
        & (Join-Path $PSScriptRoot 'check-release.ps1') -ArtifactOnly
    }
    Invoke-Step '敏感信息扫描' { Test-NoRealSecrets }
}

if (-not $SkipFrontend) {
    Invoke-Step 'Web 类型检查、全量测试与生产构建' {
        Push-Location $web
        try {
            npm run typecheck 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'ci-web-typecheck.log') | Select-Object -Last 3
            npm test 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'ci-web-tests.log') | Select-Object -Last 5
            npm run build 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'ci-web-build.log') | Select-Object -Last 3
        } finally { Pop-Location }
    }
    Invoke-Step '小程序类型检查与 H5/微信双目标构建' {
        Push-Location $miniapp
        try {
            npm run type-check 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'ci-miniapp-typecheck.log') | Select-Object -Last 3
            npm run build:h5 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'ci-miniapp-h5-build.log') | Select-Object -Last 3
            npm run build:mp-weixin 2>&1 | Tee-Object -FilePath (Join-Path $logDir 'ci-miniapp-weixin-build.log') | Select-Object -Last 3
        } finally { Pop-Location }
    }
}

Write-Output 'CI 全部门禁通过。'
