param([switch]$ArtifactOnly)
$ErrorActionPreference = 'Stop'
$server = Join-Path $PSScriptRoot '../lingdong-xuexi-server'
if (-not $ArtifactOnly) {
    $version = (& mvn --version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $version -notmatch 'Apache Maven 4\.\d+\.\d+(?:\s|\()') {
        throw '发布要求 Maven 4 正式版；当前工具链不满足要求（预览版也不作为生产准入）。仅检查产物请使用 -ArtifactOnly，不能作为发布准入。'
    }
    if ($version -notmatch 'Java version: 17[.,]') { throw '发布要求 JDK 17。' }
}
if (Get-ChildItem (Join-Path $server 'target/classes') -Recurse -File -Filter 'application-local.*' -ErrorAction SilentlyContinue) {
    throw '构建资源存在本地配置，拒绝发布。'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jars = @(Get-ChildItem (Join-Path $server 'target') -File -Filter '*.jar')
if ($jars.Count -ne 1) { throw '必须存在且仅存在一个待发布 JAR。' }
$zip = [IO.Compression.ZipFile]::OpenRead($jars[0].FullName)
try {
    if ($zip.Entries | Where-Object { $_.FullName -match '(^|/)application-local\.' }) {
        throw '最终 JAR 存在本地配置，拒绝发布。'
    }
    if (-not ($zip.Entries | Where-Object { $_.FullName -eq 'BOOT-INF/classes/application.yml' })) {
        throw '未发现 Spring Boot 应用配置，产物不完整。'
    }
} finally { $zip.Dispose() }
Write-Output '产物检查通过：构建资源和最终 JAR 均不含 application-local 配置。'
if ($ArtifactOnly) { Write-Output '仅产物检查，不代表 Maven 4 工具链或上线验收通过。' }
