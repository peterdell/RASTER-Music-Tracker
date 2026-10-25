@echo off
if not exist out\ (mkdir out)

rem The vendored, pinned MADS (lib/mads, the one build/check_drivers.sh proves the drivers with),
rem or mads from the PATH where the checkout lives elsewhere.
set MADS=%~dp0..\..\lib\mads\windows_x86_64\mads.exe
if not exist "%MADS%" set MADS=mads

rem The tracker driver: what rmt/resources/drivers/rmt_driver_v6.obx is (build/check_drivers.sh
rem proves the bytes on every push). The target selectors live on the command line, not in
rem rmt_feat.a65, so rmtplayr.a65 also assembles with xasm (/d: instead of -d:).
"%MADS%" -d:FEAT_IS_TRACKER=1 -d:FEAT_IS_SIMPLEP=0 -hc:out\tracker_obx.h -l:out\tracker_obx.lst -o:out\tracker.obx rmtplayr.a65

rem The Simple RMT Player with music.rmt, as XEX and as SAP: the same driver assembled with the
rem player routine at $3100 (the -d: values select the target and override the .ifndef defaults
rem in rmtplayer.a65, so no file needs editing).
"%MADS%" -d:FEAT_IS_TRACKER=0 -d:FEAT_IS_SIMPLEP=1 -o:out\rmtplayer.xex rmtplayer.a65
"%MADS%" -d:FEAT_IS_TRACKER=0 -d:FEAT_IS_SIMPLEP=1 -d:EXPORTXEX=0 -d:EXPORTSAP=1 -o:out\rmtplayer.sap rmtplayer.a65
pause
