param(
    [Parameter(Mandatory)][string]$Schema,
    [Parameter(Mandatory)][string]$BackupDir,
    [string]$Config,
    [string]$Credentials
)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if(-not $Config){$Config=Join-Path $root 'lingdong-bansui-server/src/main/resources/application-local.yml'}
if(-not $Credentials){$Credentials=Join-Path $root '.local-verification/bansui-demo-credentials.properties'}
$java=Join-Path $env:JAVA_HOME 'bin/java.exe'
$javac=Join-Path $env:JAVA_HOME 'bin/javac.exe'
if(-not (Test-Path $javac)){throw 'Set JAVA_HOME to JDK 17 first.'}
$cpFile=Join-Path $root '.local-verification/bootstrap-classpath.txt'
$classes=Join-Path $root '.local-verification/bootstrap-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
Push-Location (Join-Path $root 'lingdong-bansui-server')
try {
    & mvn -q dependency:build-classpath "-Dmdep.outputFile=$cpFile"
    if($LASTEXITCODE -ne 0){throw 'Classpath generation failed.'}
} finally {Pop-Location}
$cp=(Get-Content $cpFile -Raw).Trim()
& $javac -encoding UTF-8 -cp $cp -d $classes (Join-Path $PSScriptRoot 'BootstrapDemo.java')
if($LASTEXITCODE -ne 0){throw 'Bootstrap compiler failed.'}
Push-Location $root
try {
    & $java -cp "$classes;$cp" BootstrapDemo $Config $Schema $Credentials $BackupDir
    if($LASTEXITCODE -ne 0){throw 'Bootstrap failed. Inspect the migration state before retrying.'}
} finally {Pop-Location}
