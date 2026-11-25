#pragma once

#include <windows.h>

#define A5_PATCH_UNWRAP         1
#define A5_PATCH_TRACE          0
#define A5_PATCH_NOTRACE        1
#define A5_PATCH_FEMALEGUNNER   1
#define A5_PATCH_FEMGUNSHOP     1

#define A5_PATCH_NOTN           0
#define A5_PATCH_GORE18         1

#define A5_PATCH_OBJANIM        1

#define A5_PATCH_NONOTICE       1

#define A5_PATCH_HUDICON16      0

#define A5_PATCH_PKGWIZARD      1

#define A5_PATCH_ANTIEVIL       1

#define A5_PATCH_PETICON        1

#define A5_PATCH_NOMTUPROBE     1

#define A5_PATCH_NORELAY        1

#define A5_WATCH_ENABLED        1
#define A5_WATCH_SECONDS        60

#define A5_FONT_ENABLED         1
#define A5_FONT_FILE            L"fonts\\SimSun.A5.ttc"
#define A5_FONT_FACE            L""
#define A5_FONT_MATCH           L"SimSun"
#define A5_FONT_WEIGHT          0
#define A5_FONT_QUALITY         (-1)
#define A5_FONT_OUTPRECISION    (-1)

#define A5_TERSAFE_PROBE        0
#define A5_TERSAFE_STUB         1

#ifndef A5_LOG
#define A5_LOG 0
#endif

#if A5_LOG
void A5_Log(const char *fmt, ...);
#else
#define A5_Log(...) ((void)0)
#endif

extern wchar_t g_clientDir[MAX_PATH];

void CodePatch_Install(void);

void FontHook_Install(void);

void FontHook_Report(void);

void TerSafeProbe_Install(void);
