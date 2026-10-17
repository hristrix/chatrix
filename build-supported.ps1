$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $targets = @('1.21.10', '1.21.11', '26.1', '26.2', '26.3')
    $buildTasks = @($targets | ForEach-Object { ':' + $_ + ':buildAndCollect' })
    & .\gradlew.bat @buildTasks --configure-on-demand --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed (exit $LASTEXITCODE). See Gradle output above."
    }
    Write-Host 'Installable jars and source jars: build/libs/2.2.0/'
} finally {
    Pop-Location
}
