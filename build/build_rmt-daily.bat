@echo on
cd "%~dp0"

set RELEASE=Rmt
set BASE_DIR=C:\jac\system\Windows\Programming\Repositories\RASTER-Music-Tracker\cpp_src

set PRODUCTIONS=C:\jac\system\WWW\Sites\www.wudsn.com\productions
set TARGET_DIR=%PRODUCTIONS%\windows\rastermusictracker
set WINRAR=C:\jac\system\Windows\Tools\FIL\WinRAR\winrar
set UPLOAD=%PRODUCTIONS%\www\site\export\upload.bat

set MSBUILD="C:\Program Files\Microsoft Visual Studio\2022\Community\Msbuild\Current\Bin\MSBuild.exe"
REM When using the correct MSBUILD, no separate setting of target path seems to be required
REM set VCTargetsPath="C:\Program Files\Microsoft Visual Studio\2022\Community\Msbuild\Microsoft\VC\v170"
set SLN=%BASE_DIR%\%RELEASE%.sln
set RELEASE_BASE_DIR=%TEMP%\%RELEASE%\
rmdir /S /Q %RELEASE_BASE_DIR%
mkdir %RELEASE_BASE_DIR%

set CONFIGURATION=Debug
set CONFIGURATION_DIR=%CONFIGURATION%64
call :build_configuration
set CONFIGURATION=Release
set CONFIGURATION_DIR=%CONFIGURATION%64
call :build_configuration

call :upload
echo Done.
pause
goto :eof

:build_configuration
set OUTPUT_DIR=%BASE_DIR%\out\%CONFIGURATION_DIR%\output
if exist %OUTPUT_DIR%\%RELEASE%.exe del %OUTPUT_DIR%\%RELEASE%.exe
%MSBUILD% %SLN% /property:Configuration=%CONFIGURATION% -fl -flp:logfile=%OUTPUT_DIR%\msbuild.log
echo Hallo!
if not exist %OUTPUT_DIR%\%RELEASE%.exe goto :error
echo Knallo!

call :copy_output
goto :eof

:upload
echo on
set TARGET=%TARGET_DIR%\rmt134.1-daily.zip
del %TARGET%
cd %RELEASE_BASE_DIR%\..

%WINRAR% a -afzip -x*.bsc -*.exp -x*.lastcodeanalysissucceeded -x*.log -x*.lib -x*pdb %TARGET% %RELEASE%
if ERRORLEVEL 1 goto :error
start %TARGET_DIR%
cd %TARGET_DIR%


call %UPLOAD% productions
goto :eof

:copy_output
set RELEASE_DIR=%RELEASE_BASE_DIR%\%CONFIGURATION%
mkdir %RELEASE_DIR%
xcopy /E /Y  /EXCLUDE:build_rmt-daily-excluded-extensions.txt %OUTPUT_DIR%  %RELEASE_DIR%
if exist %RELEASE_DIR%\%RELEASE%.ini del %RELEASE_DIR%\%RELEASE%.ini
start %RELEASE_DIR%
goto :eof

:error
echo ERROR: See error messages above.
pause
exit
