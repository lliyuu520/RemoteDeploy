<#
Builds the IntelliJ plugin distribution zip and prints the generated artifact path.
#>
param(
    [string]$GradleTask = "buildPlugin"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# Resolve the project root from the script location so the script works from any caller directory.
function Get-ProjectRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptPath
    )

    return (Resolve-Path (Join-Path (Split-Path -Parent $ScriptPath) "..")).Path
}

# Pick the newest distribution archive after Gradle finishes packaging.
function Get-LatestPluginZip {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot
    )

    $distributionDir = Join-Path $ProjectRoot "build\distributions"
    if (-not (Test-Path $distributionDir)) {
        throw "Distribution directory not found: $distributionDir"
    }

    $zipFile = Get-ChildItem -Path $distributionDir -Filter "*.zip" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $zipFile) {
        throw "No plugin zip was generated under $distributionDir"
    }

    return $zipFile
}

$projectRoot = Get-ProjectRoot -ScriptPath $MyInvocation.MyCommand.Path

Push-Location $projectRoot
try {
    & ".\gradlew.bat" $GradleTask
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task '$GradleTask' failed with exit code $LASTEXITCODE."
    }

    $zipFile = Get-LatestPluginZip -ProjectRoot $projectRoot
    Write-Host "Plugin zip created: $($zipFile.FullName)"
}
finally {
    Pop-Location
}
