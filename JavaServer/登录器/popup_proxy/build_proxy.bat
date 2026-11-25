@echo off
rem Build the dinput8.dll proxy (32-bit). Forwards 21 exports to act4qol.dll
rem and patches the QoL announcement window from DllMain.
call "C:\BuildTools\VC\Auxiliary\Build\vcvars32.bat" >nul
cl /nologo /utf-8 /LD /O2 /MT /D_USRDLL dinput8_proxy.cpp /link /DEF:dinput8.def /MACHINE:X86 /SUBSYSTEM:WINDOWS /NOIMPLIB /OUT:dinput8.dll
