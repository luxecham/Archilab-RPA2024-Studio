# -*- mode: python ; coding: utf-8 -*-
"""Specification optionnelle pour un exécutable nommé directement ArchilabRPA2024.exe.

La commande demandée reste :
    pyinstaller --noconsole --onefile main.py
Ce fichier n'est utile que si les ressources de thème CustomTkinter doivent être
collectées explicitement :
    pyinstaller ArchilabRPA2024.spec
"""

from PyInstaller.utils.hooks import collect_data_files, collect_submodules

datas = collect_data_files("customtkinter")
hiddenimports = (
    collect_submodules("customtkinter")
    + ["access_parser", "construct", "pandas", "openpyxl", "docx", "reportlab"]
)

a = Analysis(
    ["main.py"],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=["matplotlib", "scipy", "PyQt5", "PySide6", "tkinter.test"],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="ArchilabRPA2024",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
