@echo off
rem Build the forwarding dinput8.dll (32-bit): 20 exports forwarded to act4qol.dll,
rem plus a thread that removes the QoL announcement popup in memory.
cd /d "%~dp0"
call "C:\BuildTools\VC\Auxiliary\Build\vcvars32.bat" >nul
cl /nologo /utf-8 /LD /O2 /MT /D_USRDLL dinput8_fwd.cpp /Fo:dinput8_fwd.obj /link /MACHINE:X86 /SUBSYSTEM:WINDOWS /NOIMPLIB /NOEXP /OUT:dinput8_fwd.dll
