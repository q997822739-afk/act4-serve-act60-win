#include <windows.h>

extern "C" __declspec(dllimport) void A5Anchor(void);

extern "C" int __cdecl ImplicitExceptionHandler(void *info)
{
    UNREFERENCED_PARAMETER(info);
    return 0;
}

extern "C" int __cdecl InitStackTraceLibrary(void)
{
    return 1;
}

extern "C" int __cdecl RegisterExceptionCallback(void *callback)
{
    UNREFERENCED_PARAMETER(callback);
    return 1;
}

BOOL WINAPI DllMain(HINSTANCE inst, DWORD reason, LPVOID reserved)
{
    UNREFERENCED_PARAMETER(reserved);

    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(inst);
        A5Anchor();
    }
    return TRUE;
}
