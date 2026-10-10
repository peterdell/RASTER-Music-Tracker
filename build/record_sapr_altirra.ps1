# Records a SAP type R register dump of an Atari executable by playing it in
# Altirra. The result is the per-frame POKEY register stream the program
# really produced on the emulated hardware, comparable register by register
# with RMT's own SAP-R export of the same module.
#
# How it works (see plans/32_RITMO_FORK_ANALYSIS_PLAN.md for the find):
# Altirra's autotest subsystem (/autotest) registers .autotest_* debugger
# commands, and the debugger auto-runs startup.atdbg from the portable
# folder. That script invokes the UI command Record.SAPTypeR; the one
# non-scriptable step, its file dialog, is answered with SendKeys. The
# recording is stopped and the emulator closed through the single-instance
# handoff (/singleinstance /debugcmd), because only a clean stop flushes
# the recording - a killed instance leaves a header-only file.
#
# Runs on a private portable copy (its own Altirra.ini in the work folder),
# so the user's real Altirra settings are never touched.
#
# Example:
#   powershell -File build\record_sapr_altirra.ps1 `
#     -Xex asm\Patch-16\out\rmtplayer.xex -OutFile captain.sapr `
#     -Seconds 30 -Region pal -Stereo

param(
    [Parameter(Mandatory = $true)][string]$Xex,
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$Seconds = 30,
    [ValidateSet("pal", "ntsc")][string]$Region = "pal",
    [switch]$Stereo,
    [string]$Altirra = "C:\jac\system\Atari800\Tools\EMU\Altirra 4.40\Altirra64.exe"
)

$ErrorActionPreference = "Stop"

$Xex = (Resolve-Path $Xex).Path
$OutFile = [System.IO.Path]::GetFullPath($OutFile)
if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

# The private portable copy of Altirra.
$work = Join-Path $env:TEMP "rmt_altirra_sapr"
New-Item -ItemType Directory -Force $work | Out-Null
$exe = Join-Path $work "Altirra64.exe"
if (-not (Test-Path $exe)) { Copy-Item $Altirra $exe }

# The private ini must not pause the emulation while the window is in the
# background - the harness runs unattended, so the window rarely has focus
# and the recording would crawl. (Profile 00000000 is the base profile all
# others inherit from.)
$ini = Join-Path $work "Altirra.ini"
if (Test-Path $ini) {
    (Get-Content $ini) -replace '"Pause when inactive" = 1', '"Pause when inactive" = 0' | Set-Content $ini -Encoding utf8
    # The exit confirmation ("About to exit - any unsaved work ... will be
    # lost") persists its "don't ask this again" checkbox as the
    # DialogDefaults key DiscardMemory; pre-answer it with "ok".
    if (-not (Select-String -Path $ini -Pattern "DiscardMemory" -Quiet)) {
        Add-Content $ini -Encoding utf8 -Value @(
            ''
            '[User\Software\virtualdub.org\Altirra\DialogDefaults]'
            '"DiscardMemory" = "ok"'
        )
    }
} else {
    @(
        '; Altirra settings file. EDIT AT YOUR OWN RISK.'
        ''
        '[User\Software\virtualdub.org\Altirra]'
        '"ShownSetupWizard" = 1'
        ''
        '[User\Software\virtualdub.org\Altirra\Profiles\00000000]'
        '"Pause when inactive" = 0'
        ''
        '[User\Software\virtualdub.org\Altirra\DialogDefaults]'
        '"DiscardMemory" = "ok"'
    ) | Set-Content $ini -Encoding utf8
}

# The startup script: the debugger runs it automatically.
Set-Content -Path (Join-Path $work "startup.atdbg") -Encoding Ascii -Value ".autotest_cmd Record.SAPTypeR"

# No other instance may be running, or the single-instance handoff would
# reach the wrong one.
$running = Get-Process Altirra64 -ErrorAction SilentlyContinue
if ($running) { throw "Altirra is already running - close it first." }

$switches = @("/portable", "/skipsetup", "/autotest", "/singleinstance", "/$Region")
if ($Stereo) { $switches += "/stereo" }

Write-Host "Starting Altirra ($Region$(if ($Stereo) { ', stereo' })) with $Xex"
Start-Process -FilePath $exe -WorkingDirectory $work -ArgumentList ($switches + @("/debug", "/run", "`"$Xex`""))

# Answer the recording's file dialog with the output path.
Add-Type -AssemblyName System.Windows.Forms
$shell = New-Object -ComObject WScript.Shell
$answered = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 500
    if ($shell.AppActivate("Record SAP type R music file")) {
        Start-Sleep -Milliseconds 500
        [System.Windows.Forms.SendKeys]::SendWait("`"$OutFile`"{ENTER}")
        $answered = $true
        break
    }
}
if (-not $answered) { throw "The recording dialog did not appear." }
Write-Host "Recording to $OutFile for $Seconds s..."
Start-Sleep -Seconds $Seconds

# Stop the recording (this flushes the file), then exit.
# The argument after /debugcmd must stay ONE argument: PowerShell joins
# -ArgumentList without quoting, so the quotes are embedded explicitly.
Start-Process -FilePath $exe -WorkingDirectory $work -Wait -ArgumentList ($switches + @("/debugcmd", "`".autotest_cmd Record.Stop`""))
Start-Sleep -Seconds 2
Start-Process -FilePath $exe -WorkingDirectory $work -Wait -ArgumentList ($switches + @("/debugcmd", ".autotest_exit"))
for ($i = 0; $i -lt 40; $i++) {
    if (-not (Get-Process Altirra64 -ErrorAction SilentlyContinue)) { break }
    Start-Sleep -Seconds 1
}
if (Get-Process Altirra64 -ErrorAction SilentlyContinue) {
    # Altirra has no setting to skip its exit confirmation, so if one holds
    # the exit up, answer it (its default button is the exit, and the
    # shutdown still flushes the recording).
    if ($shell.AppActivate("Altirra")) {
        Start-Sleep -Milliseconds 400
        [System.Windows.Forms.SendKeys]::SendWait("{ENTER}")
        Start-Sleep -Seconds 5
    }
}
if (Get-Process Altirra64 -ErrorAction SilentlyContinue) {
    Stop-Process -Name Altirra64 -Force
    Write-Warning "Altirra had to be killed; the validation below judges the recording."
}

# A header-only file means the recording was never flushed.
$size = (Get-Item $OutFile).Length
if ($size -le 100) { throw "The recording is empty ($size bytes) - it was not stopped cleanly." }
$bytes = [System.IO.File]::ReadAllBytes($OutFile)
$text = [System.Text.Encoding]::ASCII.GetString($bytes, 0, [Math]::Min(200, $bytes.Length))
$m = [regex]::Match($text, "TIME (\d+):(\d+)\.(\d+)")
$dur = [int]$m.Groups[1].Value * 60 + [int]$m.Groups[2].Value + [int]$m.Groups[3].Value / 1000
$headerEnd = $text.IndexOf("`r`n`r`n") + 4
$fps = if ($Region -eq "pal") { 50 } else { 60 }
$perFrame = ($size - $headerEnd) / ($dur * $fps)
Write-Host ("Recorded {0} bytes, TIME {1:f1} s, {2:f1} bytes/frame ({3})" -f $size, $dur, $perFrame, $(if ($perFrame -gt 13) { "stereo" } else { "mono" }))
