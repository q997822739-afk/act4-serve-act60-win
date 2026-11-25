@echo off
call "C:\BuildTools\VC\Auxiliary\Build\vcvars32.bat" >nul
cl /nologo /utf-8 /LD /O2 /MT /D_USRDLL dinput8_proxy.cpp /link /DEF:dinput8.def /MACHINE:X86 /OUT:dinput8.dll /SUBSYSTEM:WINDOWS /NOIMPLIB
