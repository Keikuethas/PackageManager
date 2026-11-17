@echo off
pushd "%~dp0"
call ..\..\run.bat "%~dp0input.csv" --D --rev D
popd
pause