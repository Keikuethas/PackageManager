@echo off
pushd "%~dp0"
call ..\..\run.bat "%~dp0non_existent.csv"
popd
pause