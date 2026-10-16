param(
    [ValidateSet('inspect','rehearse','RESET-lingdong_learning')][string]$Mode='inspect',
    [string]$JavaHome='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot',
    [string]$BackupDir,
    [string]$Schema
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$jdk=$JavaHome
$classpathPath=Join-Path $projectRoot '.local-verification/bootstrap-classpath.txt'
$classDirectory=Join-Path $projectRoot '.local-verification/bootstrap-classes'
if(!(Test-Path $classpathPath)){throw 'Generate bootstrap-classpath.txt using Maven dependency:build-classpath first.'}
$classpath=(Get-Content $classpathPath -Raw).Trim()
New-Item -ItemType Directory -Force $classDirectory | Out-Null
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $classpath -d $classDirectory (Join-Path $PSScriptRoot 'ResetProjectDatabase.java')
if($LASTEXITCODE -ne 0){throw 'Reset tool compilation failed.'}
Push-Location $projectRoot
try {
    $arguments=@('lingdong-bansui-server/src/main/resources/application-local.yml','.local-verification/bansui-demo-credentials.properties',$Mode)
    if($Mode -ne 'inspect') { if(-not $BackupDir){throw 'Verified backup directory required'}; $arguments+=$BackupDir }
    if($Mode -eq 'rehearse') { if(-not $Schema){throw 'Isolated schema required'}; $arguments+=$Schema }
    & "$jdk/bin/java.exe" -cp "$classDirectory;$classpath" ResetProjectDatabase @arguments
    if($LASTEXITCODE -ne 0){throw 'Project reset failed. Inspect logs before any retry.'}
} finally {Pop-Location}
