@echo off
if "%~1"=="-Z1" (
  tar -tf "%~2"
  goto :eof
)
echo Unsupported unzip invocation: %* 1>&2
exit /b 1
