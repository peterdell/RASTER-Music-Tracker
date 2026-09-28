<#
.SYNOPSIS
    Adds Windows Defender exclusions for this repository's C++ build output
    folders, to avoid real-time-scanning contention with MSVC's parallel
    (/MP) compilation writing many .obj files at once.

.DESCRIPTION
    Excludes:
      - out\                  (solution-level build output, Rmt.sln)
      - src\cpp\test\out\     (RmtTests.vcxproj's own build output, used
                                when building/running it directly)

    Requires Administrator privileges (Add-MpPreference always does) - this
    script re-launches itself elevated via UAC if not already running as
    Administrator.

.NOTES
    See plans/NOTES.md (2026-09-24 entry) for why this was added: enabling
    MultiProcessorCompilation for RmtTests.vcxproj measured *slower*
    (2m26s -> 6m18s) with Windows Defender's real-time protection active
    and no exclusion configured - the standard, well-documented cause of
    that symptom on Windows.
#>

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$exclusionPaths = @(
    (Join-Path $repoRoot 'out'),
    (Join-Path $repoRoot 'src\cpp\test\out')
)

$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$isAdmin = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "Not running as Administrator - relaunching elevated via UAC prompt..."
    $psi = @{
        FilePath     = 'powershell.exe'
        ArgumentList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"")
        Verb         = 'RunAs'
    }
    try {
        Start-Process @psi -Wait
    } catch {
        Write-Error "Elevation was cancelled or failed: $_"
        exit 1
    }
    exit 0
}

if (-not (Get-Command Add-MpPreference -ErrorAction SilentlyContinue)) {
    Write-Error "Add-MpPreference isn't available - Windows Defender's PowerShell module isn't present on this machine (e.g. a third-party antivirus is in use instead). Add an equivalent exclusion manually for:`n$($exclusionPaths -join "`n")"
    exit 1
}

foreach ($path in $exclusionPaths) {
    Write-Host "Adding Defender exclusion: $path"
    Add-MpPreference -ExclusionPath $path
}

Write-Host ""
Write-Host "Current exclusion list:"
(Get-MpPreference).ExclusionPath | ForEach-Object { Write-Host "  $_" }

Write-Host ""
Write-Host "Done. Re-run the build to see whether it's faster now."
