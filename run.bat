@echo off
setlocal
pushd "%~dp0"

set "EXE_PATH=build\jpackage\PackageManager\PackageManager.exe"

:: Проверяем, существует ли exe. Если нет — собираем.
if not exist "%EXE_PATH%" (
    echo [INFO] Executable not found. Building PackageManager.exe...
    call gradlew.bat customJpackage
    if errorlevel 1 (
        echo [ERROR] Build failed.
        popd
        exit /b 1
    )
)

:: Запускаем exe, передавая ему ВСЕ аргументы, которые передали этому bat-файлу (%*)
"%EXE_PATH%" %*
popd