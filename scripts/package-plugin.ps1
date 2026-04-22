<#
Builds the IntelliJ plugin distribution zip and prints the generated artifact path.

Default packaging is release-only:
- run Gradle in-process to avoid the local daemon socket failure
- skip searchable options because it starts sandbox IDE processes
#>
param(
    [string]$GradleTask = "buildPlugin",

    [switch]$IncludeSearchableOptions,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraGradleArgs
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
$previousJavaOpts = $env:JAVA_OPTS
try {
    $gradleArgs = @($GradleTask, "--no-daemon")
    if (-not $IncludeSearchableOptions) {
        $gradleArgs += @("-x", "buildSearchableOptions")
    }
    if ($ExtraGradleArgs) {
        $gradleArgs += $ExtraGradleArgs
    }

    # Ensure the wrapper JVM has enough heap when Gradle is forced to run in-process.
    if ([string]::IsNullOrWhiteSpace($previousJavaOpts)) {
        $env:JAVA_OPTS = "-Xmx2048m -Dfile.encoding=UTF-8"
    }
    else {
        $env:JAVA_OPTS = "$previousJavaOpts -Xmx2048m -Dfile.encoding=UTF-8"
    }

    & ".\gradlew.bat" @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task '$GradleTask' failed with exit code $LASTEXITCODE."
    }

    $zipFile = Get-LatestPluginZip -ProjectRoot $projectRoot
    Write-Host "Plugin zip created: $($zipFile.FullName)"
}
finally {
    if ($null -eq $previousJavaOpts) {
        Remove-Item Env:JAVA_OPTS -ErrorAction SilentlyContinue
    }
    else {
        $env:JAVA_OPTS = $previousJavaOpts
    }
    Pop-Location
}
