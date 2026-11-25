@echo off
rem build a4popuppatch.dll (32-bit, injected by the launcher)
call "C:\BuildTools\VC\Auxiliary\Build\vcvars32.bat" >nul
cl /nologo /utf-8 /LD /O2 /MT /D_USRDLL popuppatch.cpp /link /MACHINE:X86 /SUBSYSTEM:WINDOWS /NOIMPLIB /OUT:a4popuppatch.dll
