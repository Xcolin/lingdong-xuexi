# 数据库备份/恢复维护入口（任务 6.3 正式交付物）。
# 封装 tools/mysql-maintenance 下的 MysqlRecovery（加密备份/隔离恢复/一致性核对）
# 与 MysqlMigration（Flyway 只读校验）。凭据从外置配置文件读取且不打印；
# 备份密钥为随机 32 字节，经 Windows 当前用户 DPAPI 保护存放于 -KeyFile，不写入任何脚本。
# 备份恢复原则：Flyway 失败采用修复迁移（repair + 修正后续迁移脚本），不回滚、不改写已成功历史。
# 用法示例：
#   pwsh tools/db-maintenance.ps1 -Mode backup
#   pwsh tools/db-maintenance.ps1 -Mode restore -BackupDir <目录> -Schema ld_verify_20260929_ab12cd34
#   pwsh tools/db-maintenance.ps1 -Mode validate -Schema ld_verify_20260929_ab12cd34 -BackupDir <目录>
param(
    [Parameter(Mandatory)][ValidateSet('backup','restore','verify-source','verify-retained','validate','migrate')][string]$Mode,
    [string]$Config,
    [string]$BackupDir,
    [string]$Schema,
    [string]$KeyFile
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $Config) { $Config = Join-Path $root 'lingdong-xuexi-server\src\main\resources\application-local.yml' }
if (-not (Test-Path $Config)) { throw "外置配置不存在：$Config" }
if (-not $KeyFile) { $KeyFile = Join-Path $root '.local-verification\mysql-backup-key.dpapi' }
if (-not $BackupDir) { $BackupDir = Join-Path $root ('.local-verification\mysql-backup-' + (Get-Date -Format yyyyMMdd-HHmmss)) }
if ($Mode -eq 'restore' -or $Mode -eq 'validate') {
    if (-not $Schema) { throw "$Mode 必须提供 -Schema（隔离库名，格式 ld_verify_<8位日期>_<8位十六进制>）" }
}

# 1) 构建最小 classpath（MySQL 驱动、snakeyaml、flyway-core、flyway-mysql）
$server = Join-Path $root 'lingdong-xuexi-server'
$classpathFile = Join-Path $env:TEMP 'lingdong-maintenance-classpath.txt'
Push-Location $server
try {
    mvn -q dependency:build-classpath "-Dmdep.outputFile=$classpathFile" `
        '-Dmdep.includeArtifactIds=mysql-connector-j,snakeyaml,flyway-core,flyway-mysql,jackson-core,jackson-databind,jackson-annotations,jackson-dataformat-toml'
    if ($LASTEXITCODE -ne 0) { throw '构建维护 classpath 失败。' }
} finally { Pop-Location }
$cp = (Get-Content $classpathFile -Raw).Trim()

# 2) 编译维护工具到临时目录
$classes = Join-Path $env:TEMP 'lingdong-maintenance-classes'
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
javac -encoding UTF-8 -cp $cp -d $classes (Join-Path $PSScriptRoot 'mysql-maintenance\MysqlRecovery.java') (Join-Path $PSScriptRoot 'mysql-maintenance\MysqlMigration.java')
if ($LASTEXITCODE -ne 0) { throw '维护工具编译失败。' }
$fullCp = "$classes;$cp"

# 3) 备份密钥：随机生成一次并用 DPAPI（当前用户）保护；此后只解密到内存
Add-Type -AssemblyName System.Security
function Get-BackupKeyBase64 {
    if (Test-Path $KeyFile) {
        $protected = [System.IO.File]::ReadAllBytes($KeyFile)
        $key = [System.Security.Cryptography.ProtectedData]::Unprotect($protected, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
    } else {
        if ($Mode -ne 'backup') { throw "恢复/校验必须使用既有密钥文件：$KeyFile 不存在，不能伪造密钥。" }
        $key = New-Object byte[] 32
        [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($key)
        $protected = [System.Security.Cryptography.ProtectedData]::Protect($key, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
        [System.IO.File]::WriteAllBytes($KeyFile, $protected)
        Write-Host "backup.keyGenerated=true（已用 DPAPI 当前用户保护：$KeyFile；跨机器恢复需另行受控转移密钥）"
    }
    $base64 = [Convert]::ToBase64String($key)
    [Array]::Clear($key, 0, $key.Length)
    return $base64
}

# 4) 分发执行
Push-Location $root
try {
    switch ($Mode) {
        'backup' {
            $key = Get-BackupKeyBase64
            New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
            $key | & java -cp $fullCp MysqlRecovery $Config backup $BackupDir
            if ($LASTEXITCODE -ne 0) { throw "备份失败（退出码 $LASTEXITCODE）。" }
        }
        'restore' {
            $key = Get-BackupKeyBase64
            $key | & java -cp $fullCp MysqlRecovery $Config restore $BackupDir $Schema
            if ($LASTEXITCODE -ne 0) { throw "恢复失败（退出码 $LASTEXITCODE）。" }
        }
        'verify-source' {
            $key = Get-BackupKeyBase64
            $key | & java -cp $fullCp MysqlRecovery $Config verify-source $BackupDir
            if ($LASTEXITCODE -ne 0) { throw "源库一致性核对失败（退出码 $LASTEXITCODE）。" }
        }
        'verify-retained' {
            $key = Get-BackupKeyBase64
            $key | & java -cp $fullCp MysqlRecovery $Config verify-retained $BackupDir
            if ($LASTEXITCODE -ne 0) { throw "原库保留数据核对失败（退出码 $LASTEXITCODE）。" }
        }
        'migrate' {
            # 受控迁移：须提供既有备份目录作为演练/保留证据；建议先在隔离库重演再对 configured 执行
            & java -cp $fullCp MysqlMigration $Config $Schema migrate $BackupDir
            if ($LASTEXITCODE -ne 0) { throw "迁移失败（退出码 $LASTEXITCODE）。" }
        }
        'validate' {
            # 只读校验指定库的 Flyway 历史；-Schema 为 configured 时校验配置指向的业务库
            & java -cp $fullCp MysqlMigration $Config $Schema validate $BackupDir
            if ($LASTEXITCODE -ne 0) { throw "迁移校验失败（退出码 $LASTEXITCODE）。" }
        }
    }
    Write-Output "maintenance.mode=$Mode 完成。"
} finally { Pop-Location }
