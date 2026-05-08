@echo off
REM Entry point used by Windows Task Scheduler.
REM Run this every 15 minutes and at logon.
setlocal
set "ROOT=%~dp0.."
pushd "%ROOT%"
java -jar target\ohayo.jar capture
popd
endlocal
