<#
Creates a source zip from the project root while excluding local build, IDE, and VCS directories.
#>
param(
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Resolve the project root from the script location so the script works from any caller directory.
function Get-ProjectRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptPath
    )

    return (Resolve-Path (Join-Path (Split-Path -Parent $ScriptPath) "..")).Path
}

# Read simple Gradle properties without pulling in extra tooling just to build the archive name.
function Get-GradlePropertyValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot,
        [Parameter(Mandatory = $true)]
        [string]$PropertyName
    )

    $propertiesPath = Join-Path $ProjectRoot "gradle.properties"
    if (-not (Test-Path $propertiesPath)) {
        return $null
    }

    $escapedName = [regex]::Escape($PropertyName)
    $line = Get-Content $propertiesPath |
        Where-Object { $_ -match "^\s*$escapedName\s*=" } |
        Select-Object -First 1

    if ($null -eq $line) {
        return $null
    }

    return ($line -split "=", 2)[1].Trim()
}

# Enumerate files from the root while skipping directories that only make sense on the local machine.
function Get-FilesForSourceArchive {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot,
        [Parameter(Mandatory = $true)]
        [string[]]$ExcludedRootEntries
    )

    $rootItems = Get-ChildItem -LiteralPath $ProjectRoot -Force |
        Where-Object { $ExcludedRootEntries -notcontains $_.Name }

    foreach ($item in $rootItems) {
        if ($item.PSIsContainer) {
            Get-ChildItem -LiteralPath $item.FullName -Recurse -File -Force
        }
        else {
            $item
        }
    }
}

# Keep the archive entry names stable even on older Windows PowerShell runtimes.
function Get-RelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BasePath,
        [Parameter(Mandatory = $true)]
        [string]$TargetPath
    )

    $normalizedBasePath = $BasePath.TrimEnd("\") + "\"
    $baseUri = New-Object System.Uri($normalizedBasePath)
    $targetUri = New-Object System.Uri($TargetPath)
    return [System.Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString())
}

$projectRoot = Get-ProjectRoot -ScriptPath $MyInvocation.MyCommand.Path
$projectName = Split-Path $projectRoot -Leaf
$pluginVersion = Get-GradlePropertyValue -ProjectRoot $projectRoot -PropertyName "pluginVersion"
if ([string]::IsNullOrWhiteSpace($pluginVersion)) {
    $pluginVersion = "snapshot"
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $projectRoot "build\distributions\$projectName-source-$pluginVersion.zip"
}

$OutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $OutputPath
$excludedRootEntries = @(
    ".codexpotter",
    ".git",
    ".gradle",
    ".idea",
    ".intellijPlatform",
    "build",
    "out"
)

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
if (Test-Path $OutputPath) {
    Remove-Item $OutputPath -Force
}

$files = @(Get-FilesForSourceArchive -ProjectRoot $projectRoot -ExcludedRootEntries $excludedRootEntries)

$zipArchive = [System.IO.Compression.ZipFile]::Open($OutputPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relativePath = Get-RelativePath -BasePath $projectRoot -TargetPath $file.FullName
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zipArchive,
            $file.FullName,
            $relativePath,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
}
finally {
    $zipArchive.Dispose()
}

Write-Host "Source zip created: $OutputPath"
Write-Host "Excluded root entries: $($excludedRootEntries -join ', ')"
