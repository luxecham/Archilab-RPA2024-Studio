@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo  Archilab RPA2024 NoteDeCalcul Studio - build Windows
echo ============================================================

echo.
echo [1/2] Construction de l'executable one-file sans console...
rem Commande de reference : pyinstaller --noconsole --onefile main.py
rem On resout le chemin complet pour fonctionner aussi hors environnement virtuel.
set "PYI="
for /f "delims=" %%I in ('where pyinstaller 2^>nul') do if not defined PYI set "PYI=%%I"
if defined PYI (
    "%PYI%" --noconsole --onefile main.py
) else (
    echo pyinstaller absent du PATH : utilisation de python -m PyInstaller.
    python -m PyInstaller --noconsole --onefile main.py
)
if errorlevel 1 (
    echo [ERREUR] La construction PyInstaller a echoue.
    echo Installer les dependances : python -m pip install -r requirements.txt
    exit /b 1
)

echo.
echo [2/2] Copie vers le nom de livraison...
if exist "dist\main.exe" (
    copy /Y "dist\main.exe" "dist\ArchilabRPA2024.exe" >nul
    if errorlevel 1 (
        echo [ERREUR] Copie impossible : fermer ArchilabRPA2024.exe puis relancer.
        exit /b 1
    )
    echo Executable pret : dist\ArchilabRPA2024.exe
) else (
    echo [ERREUR] dist\main.exe introuvable.
    exit /b 1
)

echo.
echo Termine.
endlocal
