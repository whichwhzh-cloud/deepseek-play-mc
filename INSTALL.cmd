@echo off
if "%~1"=="" (
    set /p "XIXI_MINECRAFT_INSTANCE=Enter Minecraft instance directory: "
    python "%~dp0install.py"
) else (
    python "%~dp0install.py" %*
)
if errorlevel 1 echo Installation failed. Read the error above.
pause
