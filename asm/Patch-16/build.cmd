@echo off
if not exist out\ (mkdir out)
mads -hc:out\tracker_obx.h -l:out\tracker_obx.lst -o:out\tracker.obx rmtplayr.a65
rem mads -o:out\rmtplayer.xex rmtplayer.a65
pause

