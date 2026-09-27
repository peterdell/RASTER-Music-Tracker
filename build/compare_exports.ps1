# Cross-program export comparison (plans/22_CPP_SCRIPTING_PLAN.md, section 4):
# runs every script in test-resources\scripts through the Windows program
# (out\Release\output\Rmt.exe) and through the Java port (target\rmt.jar),
# with the same rmt.ini/tuning.ini (the C++ program folder's), and compares
# the two output trees byte for byte. WAV files are reported but not compared
# (8-bit in C++, 16-bit in Java). Exit code 0 when everything matches, 1 on
# any difference or failed script, 2 when one of the programs is not built.
#
#   powershell -ExecutionPolicy Bypass -File build\compare_exports.ps1 [-Keep]
#
# -Keep leaves the output folders (%TEMP%\rmt-compare\cpp, ...\java) for
# inspection. The JUnit CrossProgramExportTest runs the same comparison
# with every "mvn test" when Rmt.exe is built.

param(
    [switch]$Keep
)

$ErrorActionPreference = "Stop"
$baseDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$rmtExe = Join-Path $baseDir "out\Release\output\Rmt.exe"
$rmtJar = Join-Path $baseDir "target\rmt.jar"
$scriptsDir = Join-Path $baseDir "test-resources\scripts"
$workDir = Join-Path $env:TEMP "rmt-compare"

if (-not (Test-Path $rmtExe)) {
    Write-Host "ERROR: $rmtExe is not built."
    exit 2
}
if (-not (Test-Path $rmtJar)) {
    Write-Host "ERROR: $rmtJar is not built (mvn -o package)."
    exit 2
}
$scripts = @(Get-ChildItem -Path $scriptsDir -Filter "*.rmtscript" | Sort-Object Name)
if ($scripts.Count -eq 0) {
    Write-Host "ERROR: no scripts in $scriptsDir."
    exit 2
}

if (Test-Path $workDir) {
    Remove-Item -Recurse -Force $workDir
}
New-Item -ItemType Directory -Force $workDir | Out-Null

$differences = @()
$infos = @()

foreach ($script in $scripts) {
    $name = [System.IO.Path]::GetFileNameWithoutExtension($script.Name)
    $cppOut = Join-Path $workDir "cpp\$name"
    $javaOut = Join-Path $workDir "java\$name"
    New-Item -ItemType Directory -Force $cppOut | Out-Null
    New-Item -ItemType Directory -Force $javaOut | Out-Null

    # The Windows program: a GUI executable, so wait for it explicitly; its
    # console output goes to a log file whatever console this script has.
    Write-Host "INFO: $($script.Name) through Rmt.exe"
    $cppLog = Join-Path $workDir "cpp\$name.cpp.log"
    $env:RMT_SCRIPT_OUTPUT = $cppOut
    $env:RMT_SCRIPT_LOG = $cppLog
    $process = Start-Process -FilePath $rmtExe -ArgumentList "/SCRIPT:`"$($script.FullName)`"" -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        $logText = if (Test-Path $cppLog) { Get-Content $cppLog -Raw } else { "(no log)" }
        $differences += "${name}: Rmt.exe exit code $($process.ExitCode)`n$logText"
        continue
    }

    # The Java port: the same rmt.ini/tuning.ini as the C++ program.
    Write-Host "INFO: $($script.Name) through rmt.jar"
    $javaLog = Join-Path $workDir "java\$name.java.log"
    $env:RMT_SCRIPT_OUTPUT = $javaOut
    Remove-Item Env:RMT_SCRIPT_LOG
    $process = Start-Process -FilePath "java" -ArgumentList @("-Drmt.config.dir=`"$(Split-Path -Parent $rmtExe)`"", "-jar", "`"$rmtJar`"", "/SCRIPT:`"$($script.FullName)`"") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $javaLog -RedirectStandardError "$javaLog.err"
    if ($process.ExitCode -ne 0) {
        $differences += "${name}: rmt.jar exit code $($process.ExitCode)`n$(Get-Content $javaLog -Raw)$(Get-Content "$javaLog.err" -Raw)"
        continue
    }

    $names = @(Get-ChildItem $cppOut | ForEach-Object { $_.Name }) + @(Get-ChildItem $javaOut | ForEach-Object { $_.Name }) | Sort-Object -Unique
    foreach ($file in $names) {
        $c = Join-Path $cppOut $file
        $j = Join-Path $javaOut $file
        if (-not (Test-Path $c)) {
            $differences += "$name/$file`: only in Java"
            continue
        }
        if (-not (Test-Path $j)) {
            $differences += "$name/$file`: only in C++"
            continue
        }
        $cb = [System.IO.File]::ReadAllBytes($c)
        $jb = [System.IO.File]::ReadAllBytes($j)
        if ($file.ToLower().EndsWith(".wav")) {
            $infos += "$name/$file`: $($cb.Length) bytes (C++, 8-bit) / $($jb.Length) bytes (Java, 16-bit)"
            continue
        }
        $n = [Math]::Min($cb.Length, $jb.Length)
        $offset = -1
        for ($i = 0; $i -lt $n; $i++) {
            if ($cb[$i] -ne $jb[$i]) {
                $offset = $i
                break
            }
        }
        if ($offset -lt 0 -and $cb.Length -ne $jb.Length) {
            $offset = $n
        }
        if ($offset -ge 0) {
            $cHex = ($cb[$offset..([Math]::Min($cb.Length, $offset + 12) - 1)] | ForEach-Object { $_.ToString("x2") }) -join " "
            $jHex = ($jb[$offset..([Math]::Min($jb.Length, $offset + 12) - 1)] | ForEach-Object { $_.ToString("x2") }) -join " "
            $differences += "$name/$file`: differs at offset $offset ($($cb.Length) vs $($jb.Length) bytes) C++ $cHex | Java $jHex"
        }
    }
}

Remove-Item Env:RMT_SCRIPT_OUTPUT -ErrorAction SilentlyContinue

foreach ($info in $infos) {
    Write-Host "INFO: $info"
}
if ($differences.Count -gt 0) {
    Write-Host "ERROR: $($differences.Count) difference(s) between Rmt.exe and the Java port:"
    foreach ($difference in $differences) {
        Write-Host "  $difference"
    }
    Write-Host "INFO: outputs kept in $workDir"
    exit 1
}

Write-Host "INFO: $($scripts.Count) script(s), every exported file identical in both programs."
if (-not $Keep) {
    Remove-Item -Recurse -Force $workDir
}
exit 0
