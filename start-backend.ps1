$ErrorActionPreference = "Stop"

$workspaceRoot = $PSScriptRoot
$jdkHome = Join-Path $workspaceRoot ".tools\jdk-17\jdk-17.0.20.1+1"
$backendDirectory = Join-Path $workspaceRoot "csp-vpy-api"

if (-not (Test-Path (Join-Path $jdkHome "bin\java.exe"))) {
    throw "JDK 17 was not found at: $jdkHome"
}

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

Set-Location $backendDirectory
& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE
