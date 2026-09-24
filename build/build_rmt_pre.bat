@echo off

rem This is called in the "src\cpp" folder.

rem Prepare template folder with the most recent contents.
xcopy /Y ..\..\doc\*.html  ..\..\rmt\docs
xcopy /Y ..\..\doc\*.gif   ..\..\rmt\docs

rem The output directory is deliberately NOT wiped here anymore - the
rem PostBuildEvent's "xcopy /d" (Rmt.vcxproj) relies on the destination's
rem existing file timestamps to skip files that haven't changed. The
rem release script (build_rmt-daily.bat) does its own full wipe instead,
rem since it needs a guaranteed-clean output for the zip it ships.

