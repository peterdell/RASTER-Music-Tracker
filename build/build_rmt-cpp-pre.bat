@echo off
rem Visual Studio Project - Pre-Compiler Step
rem This is called in the "src\cpp" folder.

rem Prepare template folder with the most recent contents: the HTML
rem documentation generated from doc\*.md by the Java port's DocGenerator
rem (plans/23_DOC_GENERATION_PLAN.md) when target\rmt.jar is built - the daily
rem build requires that - else the hand-written HTML files as they are, so
rem the C++ dev loop works without a Java build.
if exist ..\..\target\rmt.jar (
  java -cp ..\..\target\rmt.jar org.atari.raster.rmt.doc.DocGenerator ..\..\doc ..\..\rmt\docs
  if ERRORLEVEL 1 exit /b 1
) else (
  echo INFO: target\rmt.jar not built, copying the HTML documentation as it is.
  xcopy /Y ..\..\doc\*.html  ..\..\rmt\docs
  xcopy /Y ..\..\doc\*.gif   ..\..\rmt\docs
)

rem The output directory is deliberately NOT wiped here anymore - the
rem PostBuildEvent's "xcopy /d" (Rmt.vcxproj) relies on the destination's
rem existing file timestamps to skip files that haven't changed. The
rem release script (build_rmt-cpp-daily.bat) does its own full wipe instead,
rem since it needs a guaranteed-clean output for the zip it ships.

