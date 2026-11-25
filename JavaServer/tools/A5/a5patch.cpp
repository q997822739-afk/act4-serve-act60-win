#include <windows.h>
#include <stdio.h>
#include <string.h>

#include "A5.h"

static const BYTE kSigUnwrap[] = {
    0xE8, 0xCC, 0x15, 0x01, 0x00,
    0x84, 0xC0,
    0x74, 0x54,
    0x8B, 0x0D, 0x40, 0xA3, 0x13, 0x01
};
static const BYTE kUnwrapFrom[] = { 0x74 };
static const BYTE kUnwrapTo[]   = { 0xEB };

static const BYTE kSigTraceLen[] = {
    0xA1, 0xD0, 0x04, 0x14, 0x01,
    0x3D, 0x00, 0x04, 0x00, 0x00,
    0x7C, 0x40
};
static const BYTE kTraceLenFrom[] = { 0xA1, 0xD0, 0x04, 0x14, 0x01 };
static const BYTE kTraceLenTo[]   = { 0xB8, 0x00, 0x10, 0x00, 0x00 };

static const BYTE kSigTraceTotal[] = {
    0x51,
    0x6A, 0x01,
    0x50,
    0x03, 0xD8,
    0x52,
    0x89, 0x1D, 0xD4, 0x04, 0x14, 0x01
};
static const BYTE kTraceTotalFrom[] = { 0x03, 0xD8 };
static const BYTE kTraceTotalTo[]   = { 0x90, 0x90 };

static const BYTE kSigNoTrace[] = {
    0x57,
    0x68, 0x00, 0x10, 0x00, 0x00,
    0xE8, 0x7D, 0x43, 0x0C, 0x00,
    0x8B, 0xF8
};
static const BYTE kNoTraceFrom[] = { 0x57, 0x68, 0x00 };
static const BYTE kNoTraceTo[]   = { 0x33, 0xC0, 0xC3 };

static const BYTE kSigNoTn[] = {
    0xE8, 0x00, 0x0C, 0x09, 0x00,
    0x83, 0xC4, 0x04,
    0x83, 0xF8, 0x02,
    0x74, 0x11,
    0x66, 0xC7, 0x05, 0xC8, 0x2E, 0x26, 0x01, 0x00, 0x00
};
static const BYTE kNoTnFrom[] = { 0x74, 0x11 };
static const BYTE kNoTnTo[]   = { 0x90, 0x90 };

static const BYTE kSigGore18[] = {
    0x31, 0x00, 0x38, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x54, 0x00, 0x4E, 0x00, 0x00, 0x00, 0x00, 0x00
};
static const BYTE kGore18From[] = { 0x54, 0x00, 0x4E, 0x00 };
static const BYTE kGore18To[]   = { 0x31, 0x00, 0x38, 0x00 };

static const BYTE kSigPetIconNew[] = {
    0xA1, 0xC0, 0x81, 0x13, 0x01,
    0x85, 0xC0,
    0x75, 0x3B,
    0x68, 0xB8, 0x00, 0x00, 0x00
};
static const BYTE kPetIconNewFrom[] = { 0x68, 0xB8, 0x00, 0x00, 0x00 };
static const BYTE kPetIconNewTo[]   = { 0x68, 0x14, 0x01, 0x00, 0x00 };

static const BYTE kSigPetIconLoop[] = {
    0x8B, 0x0D, 0xC0, 0x81, 0x13, 0x01,
    0x89, 0x04, 0xB1,
    0x46,
    0x83, 0xFE, 0x2E,
    0x7C, 0xD9
};
static const BYTE kPetIconLoopFrom[] = { 0x83, 0xFE, 0x2E };
static const BYTE kPetIconLoopTo[]   = { 0x83, 0xFE, 0x45 };

static const BYTE kSigObjAnim[] = {
    0xFF, 0x90, 0x00, 0x01, 0x00, 0x00,
    0x8A, 0x88, 0x6C, 0x01, 0x00, 0x00,
    0x05, 0x6C, 0x01, 0x00, 0x00,
    0xF6, 0xC1, 0x20,
    0x74, 0x0A
};
static const BYTE kObjAnimFrom[] = {
    0x8A, 0x88, 0x6C, 0x01, 0x00, 0x00,
    0x05, 0x6C, 0x01, 0x00, 0x00
};
static const BYTE kObjAnimTo[] = {
    0x85, 0xC0,
    0x74, 0x16,
    0x8A, 0x88, 0x6C, 0x01, 0x00, 0x00,
    0x90
};

static const BYTE kSigObjAlpha[] = {
    0xFF, 0x92, 0x00, 0x01, 0x00, 0x00,
    0x8B, 0xC8,
    0xE8, 0xF0, 0xCC, 0x62, 0x00,
    0x8B, 0x07,
    0x8B, 0xCF,
    0xFF, 0x90, 0x5C, 0x03, 0x00, 0x00
};
static const BYTE kObjAlphaFrom[] = {
    0x8B, 0xC8,
    0xE8, 0xF0, 0xCC, 0x62, 0x00
};
static BYTE kObjAlphaTo[] = {
    0xE9, 0x00, 0x00, 0x00, 0x00,
    0x90, 0x90
};

static const BYTE kSigNoNotice[] = {
    0x6B, 0xF6, 0x64,
    0x03, 0xF7,
    0x6B, 0xF6, 0x64,
    0x03, 0xF0,
    0x3B, 0xF3,
    0x7E, 0x29,
    0x8B, 0x0D, 0x64, 0x49, 0x06, 0x01,
    0x6A, 0x00,
    0x6A, 0x00,
    0x6A, 0x45,
    0xE8, 0x11, 0x0B, 0xF0, 0xFF
};
static const BYTE kNoNoticeFrom[] = { 0x7E };
static const BYTE kNoNoticeTo[]   = { 0xEB };

static const BYTE kSigHudIcon16[] = {
    0x6A, 0x10,
    0x8B, 0xCE,
    0xE8, 0xD0, 0xDB, 0xFF, 0xFF,
    0x8B, 0x0D
};
static const BYTE kHudIcon16From[] = {
    0x6A, 0x10, 0x8B, 0xCE, 0xE8, 0xD0, 0xDB, 0xFF, 0xFF
};
static BYTE kHudIcon16To[] = {
    0xE8, 0x00, 0x00, 0x00, 0x00,
    0x90, 0x90, 0x90, 0x90
};

static const BYTE kSigHudTip16[] = {
    0x83, 0xFF, 0x10,
    0x0F, 0x84, 0xA8, 0x00, 0x00, 0x00,
    0x83, 0xFF, 0x11
};
static const BYTE kHudTip16From[] = { 0xA8, 0x00, 0x00, 0x00 };
static BYTE       kHudTip16To[]   = { 0x00, 0x00, 0x00, 0x00 };

static const BYTE kSigHudClick16[] = {
    0x82, 0xAB, 0xA5, 0x00,
    0x0B, 0xAC, 0xA5, 0x00,
    0x1F, 0xAF, 0xA5, 0x00,
    0xEA, 0xAE, 0xA5, 0x00
};
static const BYTE kHudClick16From[] = { 0x1F, 0xAF, 0xA5, 0x00 };
static BYTE       kHudClick16To[]   = { 0x00, 0x00, 0x00, 0x00 };

static const BYTE kSigPkgWizard[] = {
    0xE8, 0x04, 0x66, 0xFF, 0xFF,
    0x83, 0xC4, 0x04,
    0x84, 0xC0,
    0x74, 0xA0,
    0x8D, 0x8D, 0x70, 0xFF, 0xFF, 0xFF,
    0x51,
    0x8B, 0xCE,
    0xE8, 0x3F, 0xF4, 0xFF, 0xFF,
    0xEB, 0xA3
};
static const BYTE kPkgWizardFrom[] = { 0xE8, 0x3F, 0xF4, 0xFF, 0xFF };
static BYTE kPkgWizardTo[] = { 0xE8, 0x00, 0x00, 0x00, 0x00 };

static const BYTE kSigFGShow[] = {
    0xE8, 0xA8, 0xDB, 0x0A, 0x00,
    0x8B, 0x8E, 0xDC, 0x00, 0x00, 0x00,
    0x8B, 0x11,
    0x53,
    0xFF, 0x52, 0x10,
    0x8D, 0x45, 0xCC
};
static const BYTE kFGShowFrom[] = { 0x53, 0xFF, 0x52, 0x10 };
static const BYTE kFGShowTo[]   = { 0x90, 0x90, 0x90, 0x90 };

static const BYTE kSigFGLayout[] = {
    0x8B, 0x8C, 0x86, 0xC8, 0x00, 0x00, 0x00,
    0x68, 0x86, 0x01, 0x00, 0x00,
    0x7E, 0x06,
    0x8D, 0x57, 0xA0,
    0x52,
    0xEB, 0x01,
    0x57
};
static const BYTE kFGLayoutFrom[] = { 0x7E };
static const BYTE kFGLayoutTo[]   = { 0xEB };

static const BYTE kSigFGCreate[] = {
    0x83, 0xBE, 0xC0, 0x00, 0x00, 0x00, 0x05,
    0x74, 0x68,
    0x33, 0xC0,
    0xB9, 0x81, 0x00, 0x00, 0x00
};
static const BYTE kFGCreateFrom[] = { 0x74, 0x68 };
static const BYTE kFGCreateTo[]   = { 0x90, 0x90 };

static const BYTE kSigFGSend[] = {
    0x83, 0xBE, 0x04, 0x01, 0x00, 0x00, 0x05,
    0x74, 0x51,
    0x8B, 0x0D, 0x40, 0xA3, 0x13, 0x01,
    0x6A, 0x05,
    0xE8, 0xE4, 0xF3, 0x19, 0x00
};
static const BYTE kFGSendFrom[] = { 0x74, 0x51 };
static const BYTE kFGSendTo[]   = { 0x90, 0x90 };

static const BYTE kSigFGSCombo[] = {
    0x89, 0x5D, 0xDC,
    0x33, 0xFF,
    0x8D, 0xA4, 0x24, 0x00, 0x00, 0x00, 0x00,
    0x83, 0xFF, 0x05,
    0x74, 0x36,
    0x8B, 0x44, 0xBD, 0xC8
};
static const BYTE kFGSComboFrom[] = {
    0x89, 0x5D, 0xDC, 0x33, 0xFF, 0x8D, 0xA4, 0x24, 0x00, 0x00, 0x00, 0x00,
    0x83, 0xFF, 0x05, 0x74, 0x36
};
static const BYTE kFGSComboTo[] = {
    0xC7, 0x45, 0xDC, 0x05, 0x00, 0x00, 0x00,
    0x33, 0xFF,
    0x90, 0x90, 0x90, 0x90, 0x90, 0x90, 0x90, 0x90
};

static const BYTE kSigFGSSwitch[] = {
    0x8B, 0x7D, 0x08,
    0x83, 0xFF, 0x06,
    0x8B, 0xF1,
    0x0F, 0x8D, 0x21, 0x01, 0x00, 0x00,
    0x83, 0xFF, 0x05,
    0x0F, 0x84, 0x18, 0x01, 0x00, 0x00
};
static const BYTE kFGSSwitchFrom[] = { 0x83, 0xFF, 0x05, 0x0F, 0x84, 0x18, 0x01, 0x00, 0x00 };
static const BYTE kFGSSwitchTo[]   = { 0x90, 0x90, 0x90, 0x90, 0x90, 0x90, 0x90, 0x90, 0x90 };

static const BYTE kSigFGSName[] = {
    0x00, 0x5A, 0x00, 0x00,
    0x01, 0x5A, 0x00, 0x00,
    0x02, 0x5A, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00
};
static const BYTE kFGSNameFrom[] = { 0x00, 0x00, 0x00, 0x00 };
static const BYTE kFGSNameTo[]   = { 0x03, 0x5A, 0x00, 0x00 };

static const BYTE kSigFGSRange[] = {
    0x58, 0xE3, 0x00, 0x00, 0xE7, 0xE4, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};
static const BYTE kFGSRangeFrom[80] = { 0 };
static const BYTE kFGSRangeTo[] = {
    0x00, 0x6A, 0x18, 0x00, 0x0F, 0x91, 0x18, 0x00,
    0x10, 0x91, 0x18, 0x00, 0x1F, 0xB8, 0x18, 0x00,
    0x20, 0xB8, 0x18, 0x00, 0x2F, 0xDF, 0x18, 0x00,
    0x40, 0x06, 0x19, 0x00, 0x4F, 0x2D, 0x19, 0x00,
    0x50, 0x2D, 0x19, 0x00, 0x5F, 0x54, 0x19, 0x00,
    0x70, 0x7B, 0x19, 0x00, 0x7F, 0xA2, 0x19, 0x00,
    0x30, 0xDF, 0x18, 0x00, 0x3F, 0x06, 0x19, 0x00,
    0x60, 0x54, 0x19, 0x00, 0x6F, 0x7B, 0x19, 0x00,
    0x80, 0xA2, 0x19, 0x00, 0x8F, 0xC9, 0x19, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};

static const BYTE kSigAntiEvil[] = {
    0x8B, 0xCF,
    0xFF, 0x50, 0x68,
    0xFF, 0x06,
    0x8B, 0x4D, 0x0C,
    0x83, 0xC1, 0x20,
    0xE8, 0x34, 0x1F, 0xA7, 0xFF
};
static const BYTE kAntiEvilFrom[] = {
    0x8B, 0x4D, 0x0C,
    0x83, 0xC1, 0x20
};
static BYTE kAntiEvilTo[] = {
    0xE9, 0x00, 0x00, 0x00, 0x00,
    0x90
};

static const BYTE kSigStatLimit[] = {
    0x0F, 0x8C, 0xC8, 0x20, 0x00, 0x00,
    0x83, 0xFB, 0x1D,
    0x0F, 0x8D, 0xBF, 0x20, 0x00, 0x00,
    0x8B, 0x8E, 0xDC, 0x00, 0x00, 0x00
};
static const BYTE kStatLimitFrom[] = { 0x83, 0xFB, 0x1D };
static const BYTE kStatLimitTo[]   = { 0x83, 0xFB, 0x1E };

static const BYTE kSigStatLoop[] = {
    0x8D, 0x3C, 0x00,
    0x83, 0xFF, 0x1D,
    0x7D, 0x09,
    0x56, 0x57, 0x8B, 0xCB,
    0xE8, 0x62, 0xCE, 0xFF, 0xFF,
    0x8D, 0x47, 0x01,
    0x83, 0xF8, 0x1D
};
static const BYTE kStatLoopFrom[] = {
    0x83, 0xFF, 0x1D, 0x7D, 0x09, 0x56, 0x57, 0x8B, 0xCB,
    0xE8, 0x62, 0xCE, 0xFF, 0xFF, 0x8D, 0x47, 0x01, 0x83, 0xF8, 0x1D
};
static const BYTE kStatLoopTo[] = {
    0x83, 0xFF, 0x1E, 0x7D, 0x09, 0x56, 0x57, 0x8B, 0xCB,
    0xE8, 0x62, 0xCE, 0xFF, 0xFF, 0x8D, 0x47, 0x01, 0x83, 0xF8, 0x1E
};

static const BYTE kSigStatTail[] = {
    0xE9, 0x4A, 0xE4, 0xFF, 0xFF,
    0x83, 0xFB, 0x1C,
    0x0F, 0x85, 0xF1, 0x00, 0x00, 0x00,
    0x8B, 0x0D, 0x8C, 0x01, 0x14, 0x01
};
static const BYTE kStatTailFrom[] = { 0x0F, 0x85, 0xF1, 0x00, 0x00, 0x00 };
static BYTE kStatTailTo[] = { 0x0F, 0x85, 0x00, 0x00, 0x00, 0x00 };

static const BYTE kSigStatIcon[] = {
    0x8D, 0x4E, 0x01,
    0x83, 0xF9, 0x0F,
    0x7C, 0x05,
    0xBF, 0x01, 0x00, 0x00, 0x00,
    0x8D, 0x44, 0x3E, 0x01,
    0x83, 0xF8, 0x1D,
    0x7F, 0x3E,
    0x83, 0xF9, 0x1D,
    0x7D, 0x23
};
static const BYTE kStatIconFrom[] = {
    0x83, 0xF8, 0x1D, 0x7F, 0x3E, 0x83, 0xF9, 0x1D, 0x7D, 0x23
};
static BYTE kStatIconTo[] = {
    0xE9, 0x00, 0x00, 0x00, 0x00,
    0x90, 0x90, 0x90, 0x90, 0x90
};

static const BYTE kSigNoMtuProbe[] = {
    0x55,
    0x8B, 0xEC,
    0x83, 0xEC, 0x28,
    0x53, 0x56, 0x57,
    0x8B, 0xF9,
    0x68, 0x48, 0x92, 0xE8, 0x00,
    0x89, 0x7D, 0xFC,
    0xFF
};
static const BYTE kNoMtuProbeFrom[] = {
    0x55, 0x8B, 0xEC, 0x83, 0xEC, 0x28, 0x53, 0x56,
    0x57, 0x8B, 0xF9, 0x68, 0x48, 0x92, 0xE8, 0x00
};
static const BYTE kNoMtuProbeTo[] = {
    0xC7, 0x81, 0xA4, 0x01, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00,
    0xB8, 0x01, 0x00, 0x00, 0x00,
    0xC3
};

static const BYTE kSigNoRelay[] = {
    0x55,
    0x8B, 0xEC,
    0x6A, 0xFF,
    0x68, 0x8A, 0x12, 0xCF, 0x00,
    0x64, 0xA1, 0x00, 0x00, 0x00, 0x00
};
static const BYTE kNoRelayFrom[] = { 0x55, 0x8B, 0xEC };
static const BYTE kNoRelayTo[]   = { 0x33, 0xC0, 0xC3 };

typedef enum {
    GRP_UNWRAP = 0,
    GRP_TRACE,
    GRP_NOTRACE,
    GRP_FEMALEGUNNER,
    GRP_FEMGUNSHOP,
    GRP_NOTN,
    GRP_GORE18,
    GRP_OBJANIM,
    GRP_NONOTICE,
    GRP_HUDICON16,
    GRP_PKGWIZARD,
    GRP_ANTIEVIL,
    GRP_ANTIEVILSTAT,
    GRP_PETICON,
    GRP_NOMTUPROBE,
    GRP_NORELAY
} PatchGroup;

#if A5_LOG
#define PATCH_NAME(s) s
#else
#define PATCH_NAME(s) NULL
#endif

typedef struct {
    PatchGroup  group;
    const char *name;
    DWORD       sigVa;
    const BYTE *sig;
    SIZE_T      sigLen;
    SIZE_T      patchOff;
    const BYTE *from;
    const BYTE *to;
    SIZE_T      len;
    BYTE       *addr;
} Patch;

static Patch g_patches[] = {
    { GRP_UNWRAP, PATCH_NAME("TP 出站换包"), 0x00B47A4Fu, kSigUnwrap, sizeof(kSigUnwrap), 7,
      kUnwrapFrom, kUnwrapTo, sizeof(kUnwrapFrom), NULL },
    { GRP_TRACE, PATCH_NAME("DNF.trc 整块落盘"), 0x00B8B0F3u, kSigTraceLen, sizeof(kSigTraceLen), 0,
      kTraceLenFrom, kTraceLenTo, sizeof(kTraceLenFrom), NULL },
    { GRP_TRACE, PATCH_NAME("DNF.trc 不轮转"), 0x00B8B111u, kSigTraceTotal, sizeof(kSigTraceTotal), 4,
      kTraceTotalFrom, kTraceTotalTo, sizeof(kTraceTotalFrom), NULL },
    { GRP_NOTRACE, PATCH_NAME("DNF.trc 不生成"), 0x00B8B440u, kSigNoTrace, sizeof(kSigNoTrace), 0,
      kNoTraceFrom, kNoTraceTo, sizeof(kNoTraceFrom), NULL },
    { GRP_FEMALEGUNNER, PATCH_NAME("女枪手 按钮放出来"), 0x00B22523u, kSigFGShow, sizeof(kSigFGShow), 13,
      kFGShowFrom, kFGShowTo, sizeof(kFGShowFrom), NULL },
    { GRP_FEMALEGUNNER, PATCH_NAME("女枪手 六格布局"), 0x00B2254Bu, kSigFGLayout, sizeof(kSigFGLayout), 12,
      kFGLayoutFrom, kFGLayoutTo, sizeof(kFGLayoutFrom), NULL },
    { GRP_FEMALEGUNNER, PATCH_NAME("女枪手 放行创建请求"), 0x00B20E47u, kSigFGCreate, sizeof(kSigFGCreate), 7,
      kFGCreateFrom, kFGCreateTo, sizeof(kFGCreateFrom), NULL },
    { GRP_FEMALEGUNNER, PATCH_NAME("女枪手 放行 CMD 5 组包"), 0x009A83E6u, kSigFGSend, sizeof(kSigFGSend), 7,
      kFGSendFrom, kFGSendTo, sizeof(kFGSendFrom), NULL },
    { GRP_FEMGUNSHOP, PATCH_NAME("商城 下拉框第六项"), 0x00A93374u, kSigFGSCombo, sizeof(kSigFGSCombo), 0,
      kFGSComboFrom, kFGSComboTo, sizeof(kFGSComboFrom), NULL },
    { GRP_FEMGUNSHOP, PATCH_NAME("商城 放行切到职业5"), 0x00A8F758u, kSigFGSSwitch, sizeof(kSigFGSSwitch), 14,
      kFGSSwitchFrom, kFGSSwitchTo, sizeof(kFGSSwitchFrom), NULL },
    { GRP_FEMGUNSHOP, PATCH_NAME("商城 职业名表补第六项"), 0x00E98AF0u, kSigFGSName, sizeof(kSigFGSName), 12,
      kFGSNameFrom, kFGSNameTo, sizeof(kFGSNameFrom), NULL },
    { GRP_FEMGUNSHOP, PATCH_NAME("商城 itemId区间表补job5"), 0x00E98CF0u, kSigFGSRange, sizeof(kSigFGSRange), 16,
      kFGSRangeFrom, kFGSRangeTo, sizeof(kFGSRangeFrom), NULL },
    { GRP_NOTN, PATCH_NAME("前缀 setter 永远置 0"), 0x00BCFA88u, kSigNoTn, sizeof(kSigNoTn), 11,
      kNoTnFrom, kNoTnTo, sizeof(kNoTnFrom), NULL },
    { GRP_GORE18, PATCH_NAME("前缀代码 TN 换成 18"), 0x00D06640u, kSigGore18, sizeof(kSigGore18), 8,
      kGore18From, kGore18To, sizeof(kGore18From), NULL },
    { GRP_NORELAY, PATCH_NAME("relay客户端 不启动"), 0x00BE68E0u,
      kSigNoRelay, sizeof(kSigNoRelay), 0,
      kNoRelayFrom, kNoRelayTo, sizeof(kNoRelayFrom), NULL },
    { GRP_NOMTUPROBE, PATCH_NAME("MTU探测 不做直接填1024"), 0x0094A730u,
      kSigNoMtuProbe, sizeof(kSigNoMtuProbe), 0,
      kNoMtuProbeFrom, kNoMtuProbeTo, sizeof(kNoMtuProbeFrom), NULL },
    { GRP_PETICON, PATCH_NAME("宠物头像表 ①扩到69项"), 0x00A50A99u, kSigPetIconNew, sizeof(kSigPetIconNew), 9,
      kPetIconNewFrom, kPetIconNewTo, sizeof(kPetIconNewFrom), NULL },
    { GRP_PETICON, PATCH_NAME("宠物头像表 ②填满69帧"), 0x00A50ACEu, kSigPetIconLoop, sizeof(kSigPetIconLoop), 10,
      kPetIconLoopFrom, kPetIconLoopTo, sizeof(kPetIconLoopFrom), NULL },
    { GRP_OBJANIM, PATCH_NAME("物件销毁条件 补判空"), 0x004B20D7u, kSigObjAnim, sizeof(kSigObjAnim), 6,
      kObjAnimFrom, kObjAnimTo, sizeof(kObjAnimFrom), NULL },
    { GRP_OBJANIM, PATCH_NAME("物件半透明 补判空"), 0x004B2CE3u, kSigObjAlpha, sizeof(kSigObjAlpha), 6,
      kObjAlphaFrom, kObjAlphaTo, sizeof(kObjAlphaFrom), NULL },
    { GRP_NONOTICE, PATCH_NAME("每日公告窗 不开"), 0x00A5C030u, kSigNoNotice, sizeof(kSigNoNotice), 12,
      kNoNoticeFrom, kNoNoticeTo, sizeof(kNoNoticeFrom), NULL },
    { GRP_HUDICON16, PATCH_NAME("HUD第16项 ①显示"), 0x00A5B3C7u, kSigHudIcon16, sizeof(kSigHudIcon16), 0,
      kHudIcon16From, kHudIcon16To, sizeof(kHudIcon16From), NULL },
    { GRP_HUDICON16, PATCH_NAME("HUD第16项 ②悬停提示"), 0x00A5E0DDu, kSigHudTip16, sizeof(kSigHudTip16), 5,
      kHudTip16From, kHudTip16To, sizeof(kHudTip16From), NULL },
    { GRP_HUDICON16, PATCH_NAME("HUD第16项 ③点击打开商城"), 0x00A5AF78u, kSigHudClick16, sizeof(kSigHudClick16), 8,
      kHudClick16From, kHudClick16To, sizeof(kHudClick16From), NULL },
#if A5_PATCH_PKGWIZARD
    { GRP_PKGWIZARD, PATCH_NAME("礼包向导 观测购物车项"), 0x00A947E7u, kSigPkgWizard, sizeof(kSigPkgWizard), 21,
      kPkgWizardFrom, kPkgWizardTo, sizeof(kPkgWizardFrom), NULL },
#endif
#if A5_PATCH_ANTIEVIL
    { GRP_ANTIEVIL, PATCH_NAME("装备说明 补抗魔一行"), 0x009CC2AAu, kSigAntiEvil, sizeof(kSigAntiEvil), 7,
      kAntiEvilFrom, kAntiEvilTo, sizeof(kAntiEvilFrom), NULL },
    { GRP_ANTIEVILSTAT, PATCH_NAME("属性面板 放开索引上界"), 0x00A0FA02u, kSigStatLimit, sizeof(kSigStatLimit), 6,
      kStatLimitFrom, kStatLimitTo, sizeof(kStatLimitFrom), NULL },
    { GRP_ANTIEVILSTAT, PATCH_NAME("属性面板 外层循环两处上界"), 0x00A12B7Du, kSigStatLoop, sizeof(kSigStatLoop), 3,
      kStatLoopFrom, kStatLoopTo, sizeof(kStatLoopFrom), NULL },
    { GRP_ANTIEVILSTAT, PATCH_NAME("属性面板 链尾接第30格"), 0x00A119D0u, kSigStatTail, sizeof(kSigStatTail), 8,
      kStatTailFrom, kStatTailTo, sizeof(kStatTailFrom), NULL },
    { GRP_ANTIEVILSTAT, PATCH_NAME("属性面板 第30格行首图标"), 0x00A0EB58u, kSigStatIcon, sizeof(kSigStatIcon), 17,
      kStatIconFrom, kStatIconTo, sizeof(kStatIconFrom), NULL },
#endif
};

#define PATCH_COUNT (sizeof(g_patches) / sizeof(g_patches[0]))

#define PATCH_BUF 160

#define WATCH_INTERVAL_MS 250

static LONG g_rewrites;
static int  g_watchSeconds = 60;

static BOOL WriteBytes(BYTE *addr, const BYTE *bytes, SIZE_T len)
{
    DWORD old = 0;
    if (!VirtualProtect(addr, len, PAGE_EXECUTE_READWRITE, &old))
        return FALSE;

    memcpy(addr, bytes, len);

    DWORD tmp = 0;
    VirtualProtect(addr, len, old, &tmp);
    FlushInstructionCache(GetCurrentProcess(), addr, len);
    return TRUE;
}

static void HexLine(const BYTE *p, int len, char *out, size_t cch)
{
    size_t used = 0;
    out[0] = 0;
    for (int i = 0; i < len && used + 4 < cch; i++) {
        int n = _snprintf(out + used, cch - used, i ? " %02X" : "%02X", p[i]);
        if (n < 0)
            break;
        used += (size_t)n;
    }
}

static BOOL SafeRead(const void *p, void *out, SIZE_T len)
{
    if (!p)
        return FALSE;
    __try {
        memcpy(out, p, len);
    } __except (EXCEPTION_EXECUTE_HANDLER) {
        return FALSE;
    }
    return TRUE;
}

#if A5_LOG

typedef int(__cdecl *MakeActorFn)(int job);

static MakeActorFn g_makeActor;
static DWORD       g_rosterProbeHits;
static DWORD       g_rosterProbeFails;

static DWORD *g_readCursor;
static DWORD  g_lastCursor;

static int __cdecl RosterActorProbeC(int job, int userId, int index, int subtype, int cnuser)
{
    DWORD cursor = g_readCursor ? *g_readCursor : 0;
    DWORD ate = (index > 0 && g_lastCursor) ? cursor - g_lastCursor : 0;
    g_lastCursor = cursor;

    int actor = g_makeActor(job);

    g_rosterProbeHits++;
    if (!actor) {
        g_rosterProbeFails++;
        A5_Log("[roster] ★★ 子类型%d 第%d条 userId=%u CNUser=%p job=%d -> 造不出角色"
               "（sub_7B5CC0 的 default）—— 名单循环在这里 break，"
               "这条和排在它后面的队友全都不会被建出来。第 %lu 次",
               subtype, index, (unsigned)userId, (void *)cnuser, job, g_rosterProbeFails);
    } else {
        A5_Log("[roster] 子类型%d 第%d条 userId=%u CNUser=%p job=%d -> %p 建出来了",
               subtype, index, (unsigned)userId, (void *)cnuser, job, (void *)actor);
    }

    if (index > 0)
        A5_Log("[roster]    游标 %08X，吃掉上一条 %lu 字节", cursor, ate);
    else
        A5_Log("[roster]    游标 %08X（第一条，没有上一条可比）", cursor);

    if (cursor) {
        BYTE around[32];
        if (SafeRead((const void *)(cursor - 16), around, sizeof(around))) {
            char before[16 * 3 + 8], after[16 * 3 + 8];
            HexLine(around, 16, before, sizeof(before));
            HexLine(around + 16, 16, after, sizeof(after));
            A5_Log("[roster]    游标前 %s", before);
            A5_Log("[roster]    游标后 %s", after);
        }
    }
    return actor;
}

static void __declspec(naked) RosterActorProbe(void)
{
    __asm {
        push esi
        movzx eax, byte ptr [ebp+0x0B]
        push eax
        mov  eax, dword ptr [ebp-0x38]
        push eax
        movzx eax, word ptr [ebp-0x140]
        push eax
        push dword ptr [esp+20]
        call RosterActorProbeC
        add  esp, 20
        ret
    }
}

static void RosterProbe_Install(DWORD_PTR delta)
{
    BYTE *site   = (BYTE *)(0x00426EC0u + delta);
    BYTE *next   = site + 5;
    BYTE *factory = (BYTE *)(0x007B5CC0u + delta);

    BYTE now[5];
    if (!SafeRead(site, now, sizeof(now))) {
        A5_Log("[roster] %p 读不出来，探针不装", site);
        return;
    }
    if (now[0] != 0xE8) {
        A5_Log("[roster] %p 不是 E8 call（是 %02X），探针不装 —— 换客户端了？", site, now[0]);
        return;
    }
    BYTE *target = next + *(LONG *)(now + 1);
    if (target != factory) {
        A5_Log("[roster] %p 的 call 指向 %p，不是职业工厂 %p，探针不装",
               site, target, factory);
        return;
    }

    g_makeActor = (MakeActorFn)factory;
    g_readCursor = (DWORD *)(0x01162298u + delta);
    g_lastCursor = 0;

    BYTE patch[5];
    patch[0] = 0xE8;
    *(LONG *)(patch + 1) = (LONG)((BYTE *)&RosterActorProbe - next);
    if (!WriteBytes(site, patch, sizeof(patch))) {
        A5_Log("[roster] %p 写不进去 (err=%lu)，探针不装", site, GetLastError());
        return;
    }
    A5_Log("[roster] 探针已装在 %p（职业工厂 %p）—— 组队进副本后看 [roster] 那几行："
           "每条名单记录打一行，出现 ★★ 就说明 break 真的发生了", site, factory);
}

#else
#define RosterProbe_Install(delta) ((void)0)
#endif

static BYTE *g_icon16IsActive = (BYTE *)0x00A58FA0u;
static BYTE *g_icon16AddIcon  = (BYTE *)0x00A5A6E0u;

#define HUDICON16_INDEX 0x10

__declspec(naked) static void HudIcon16Thunk(void)
{
    __asm {
        pushfd
        pushad
        push HUDICON16_INDEX
        mov  ecx, esi
        call dword ptr [g_icon16IsActive]
        test al, al
        jne  already_shown
        push HUDICON16_INDEX
        mov  ecx, esi
        call dword ptr [g_icon16AddIcon]
    already_shown:
        popad
        popfd
        ret
    }
}

static BYTE *g_tip16GetStr = (BYTE *)0x00B39AA0u;
static BYTE *g_tip16Label36 = (BYTE *)0x00A5E174u;

#define HUDICON16_BTN_OFF  0x1430
#define HUDICON16_TIP_STR  0xB3E8

__declspec(naked) static void Tooltip16Thunk(void)
{
    __asm {
        mov  eax, dword ptr [esi + HUDICON16_BTN_OFF]
        mov  edi, dword ptr [eax + 0x18]
        mov  ebx, dword ptr [eax + 0x14]
        push 0
        push 0
        push 0x640
        push 0
        push 0
        push HUDICON16_TIP_STR
        call dword ptr [g_tip16GetStr]
        add  edi, -5
        jmp  dword ptr [g_tip16Label36]
    }
}

static BYTE *g_click16SceneChk1 = (BYTE *)0x00445C50u;
static BYTE *g_click16SceneChk2 = (BYTE *)0x00445C70u;
static BYTE *g_click16OpenShop  = (BYTE *)0x00A977A0u;
static BYTE *g_click16Break     = (BYTE *)0x00A5AF1Fu;
static BYTE *g_click16SceneVar  = (BYTE *)0x01063C30u;
static BYTE *g_click16GameVar   = (BYTE *)0x01064964u;

__declspec(naked) static void Click16Thunk(void)
{
    __asm {
        mov  eax, dword ptr [g_click16SceneVar]
        mov  ecx, dword ptr [eax]
        call dword ptr [g_click16SceneChk1]
        test al, al
        jne  do_break
        mov  eax, dword ptr [g_click16SceneVar]
        mov  ecx, dword ptr [eax]
        call dword ptr [g_click16SceneChk2]
        test al, al
        jne  do_break
        mov  eax, dword ptr [g_click16GameVar]
        mov  edx, dword ptr [eax]
        mov  ecx, dword ptr [edx + 0x1B04]
        push 1
        call dword ptr [g_click16OpenShop]
    do_break:
        jmp  dword ptr [g_click16Break]
    }
}

static BYTE *g_objAlphaSetup  = (BYTE *)0x00ADF9E0u;
static BYTE *g_objAlphaResume = (BYTE *)0x004B2CF0u;
static BYTE *g_objAlphaBail   = (BYTE *)0x004B2D21u;

__declspec(naked) static void ObjAlphaThunk(void)
{
    __asm {
        test eax, eax
        jz   no_anim
        mov  ecx, eax
        call dword ptr [g_objAlphaSetup]
        jmp  dword ptr [g_objAlphaResume]
    no_anim:
        jmp  dword ptr [g_objAlphaBail]
    }
}

#if A5_PATCH_PKGWIZARD
static BYTE *g_pkgWizardOrig = (BYTE *)0x00A93C40u;

#define PKGWIZ_TRANSACTION_OFF 0x8AACu

#define PKGWIZ_ITEMID_OFF 0x10u

static BYTE *g_pkgWizCashShopVt = (BYTE *)0x00E9903Cu;

typedef void (__thiscall *PFN_PkgWizClear)(void *self);
typedef void (__thiscall *PFN_PkgWizCreate)(void *self, int type, int itemId);
typedef char (__thiscall *PFN_PkgWizHasSteps)(void *self);

static LONG g_pkgWizProbes;

static int __cdecl PkgWizardStart(const BYTE *shop, const BYTE *entry)
{
    LONG n = InterlockedIncrement(&g_pkgWizProbes);

    if (!shop || !entry) {
        A5_Log("[pkgwizard] 第 %ld 次：商城或购物车项是空的，退回预览窗", n);
        return 0;
    }

    DWORD trans = 0;
    if (!SafeRead(shop + PKGWIZ_TRANSACTION_OFF, &trans, sizeof(trans)) || !trans) {
        A5_Log("[pkgwizard] 第 %ld 次：取不到 transaction（+%X），退回预览窗",
               n, PKGWIZ_TRANSACTION_OFF);
        return 0;
    }

    DWORD vt = 0;
    if (!SafeRead((const void *)(DWORD_PTR)trans, &vt, sizeof(vt))
        || vt != (DWORD)(DWORD_PTR)g_pkgWizCashShopVt) {
        A5_Log("[pkgwizard] 第 %ld 次：transaction=%p 的 vtable 是 %p，不是 %p，退回预览窗",
               n, (void *)(DWORD_PTR)trans, (void *)(DWORD_PTR)vt, g_pkgWizCashShopVt);
        return 0;
    }

    DWORD itemId = 0;
    if (!SafeRead(entry + PKGWIZ_ITEMID_OFF, &itemId, sizeof(itemId)) || !itemId) {
        A5_Log("[pkgwizard] 第 %ld 次：购物车项 +%X 取不到 itemId，退回预览窗",
               n, PKGWIZ_ITEMID_OFF);
        return 0;
    }

    void *self = (void *)(DWORD_PTR)trans;
    const DWORD *slots = (const DWORD *)(DWORD_PTR)vt;
    char has = 0;
    int steps = 0;

    __try {
        ((PFN_PkgWizClear)(DWORD_PTR)slots[3])(self);
        ((PFN_PkgWizCreate)(DWORD_PTR)slots[1])(self, 1, (int)itemId);
        has = ((PFN_PkgWizHasSteps)(DWORD_PTR)slots[5])(self);

        DWORD list[2] = { 0, 0 };
        if (SafeRead((const BYTE *)(DWORD_PTR)trans + 20, list, sizeof(list)) && list[0])
            steps = (int)((list[1] - list[0]) / 4);
    } __except (EXCEPTION_EXECUTE_HANDLER) {
        A5_Log("[pkgwizard] 第 %ld 次：建向导时抛异常（code=%08X），退回预览窗",
               n, (unsigned)GetExceptionCode());
        return 0;
    }

    A5_Log("[pkgwizard] 第 %ld 次：礼包 %lu -> 建了 %d 步，HasSteps=%d %s",
           n, (unsigned long)itemId, steps, (int)(unsigned char)has,
           has ? "→ 向导接管" : "→ 没步骤，退回预览窗");
    return has ? 1 : 0;
}

__declspec(naked) static void PkgWizardThunk(void)
{
    __asm {
        push ecx
        push dword ptr [esp+8]
        push ecx
        call PkgWizardStart
        add  esp, 8
        pop  ecx
        test eax, eax
        jnz  taken
        jmp  dword ptr [g_pkgWizardOrig]
    taken:
        ret  4
    }
}
#endif

static BYTE *g_aeGetter   = (BYTE *)0x00402100u;
static BYTE *g_aeGetStr   = (BYTE *)0x00B39AA0u;
static BYTE *g_aeFormat   = (BYTE *)0x00C62DB2u;
static BYTE *g_aeRowStyle = (BYTE *)0x0101D07Cu;
static BYTE *g_aeResume   = (BYTE *)0x009CC2B7u;

#define ANTIEVIL_FIELD_OFF  0x36C
#define ANTIEVIL_STR_ID     0x9C63
#define ANTIEVIL_NEXT_OFF   0x20

static const wchar_t kAntiEvilFmt[] = L"%s +%d";

__declspec(naked) static void AntiEvilThunk(void)
{
    __asm {
        mov  ecx, dword ptr [ebp+0x0C]
        add  ecx, ANTIEVIL_FIELD_OFF
        call dword ptr [g_aeGetter]
        test eax, eax
        jz   no_row
        mov  ecx, dword ptr [ebp+0x0C]
        add  ecx, ANTIEVIL_FIELD_OFF
        call dword ptr [g_aeGetter]
        push eax
        push ANTIEVIL_STR_ID
        call dword ptr [g_aeGetStr]
        add  esp, 4
        push eax
        push offset kAntiEvilFmt
        lea  edx, dword ptr [ebp-0x464]
        push 0x200
        push edx
        call dword ptr [g_aeFormat]
        mov  edx, dword ptr [esi]
        mov  eax, dword ptr [edi]
        add  esp, 0x14
        push ebx
        push dword ptr [g_aeRowStyle]
        lea  ecx, dword ptr [ebp-0x464]
        push ecx
        push edx
        mov  ecx, edi
        call dword ptr [eax+0x68]
        inc  dword ptr [esi]
    no_row:
        mov  ecx, dword ptr [ebp+0x0C]
        add  ecx, ANTIEVIL_NEXT_OFF
        jmp  dword ptr [g_aeResume]
    }
}

static BYTE *g_seTextObj = (BYTE *)0x0114018Cu;
static BYTE *g_seGetStr  = (BYTE *)0x00B39AA0u;
static BYTE *g_seTotal   = (BYTE *)0x00499850u;
static BYTE *g_seFormat  = (BYTE *)0x00401CB0u;
static BYTE *g_seNumFmt  = (BYTE *)0x00D06DD8u;
static BYTE *g_seDrawEnd = (BYTE *)0x00A0FE1Fu;
static BYTE *g_seNoMatch = (BYTE *)0x00A11ACFu;

#define STAT_ANTIEVIL_INDEX   0x1D
#define STAT_ANTIEVIL_STR_ID  0x9C63

__declspec(naked) static void StatAntiEvilThunk(void)
{
    __asm {
        cmp  ebx, STAT_ANTIEVIL_INDEX
        jne  not_mine

        mov  ecx, dword ptr [g_seTextObj]
        mov  ecx, dword ptr [ecx]
        mov  edx, dword ptr [ecx]
        lea  eax, dword ptr [esi+0xEC]
        push eax
        call dword ptr [edx+0x48]

        mov  ecx, dword ptr [g_seTextObj]
        mov  ecx, dword ptr [ecx]
        mov  eax, dword ptr [ecx]
        push STAT_ANTIEVIL_STR_ID
        mov  dword ptr [ebp-0x04], eax
        call dword ptr [g_seGetStr]
        mov  edx, dword ptr [ebp+0x08]
        mov  ecx, dword ptr [ebp-0x14]
        mov  ebx, dword ptr [ebp-0x10]
        add  esp, 4
        push eax
        push -1
        lea  eax, dword ptr [edi+edx+0x85]
        push eax
        mov  eax, dword ptr [ebp-0x04]
        add  ecx, ebx
        push ecx
        mov  ecx, dword ptr [g_seTextObj]
        mov  ecx, dword ptr [ecx]
        call dword ptr [eax+0x54]

        push dword ptr [esi+0xDC]
        call dword ptr [g_seTotal]
        add  esp, 4
        push eax
        push dword ptr [g_seNumFmt]
        lea  edx, dword ptr [ebp-0x820]
        push 0x800
        push edx
        call dword ptr [g_seFormat]
        add  esp, 0x10
        mov  dword ptr [ebp-0x08], -1

        mov  ecx, dword ptr [g_seTextObj]
        mov  ecx, dword ptr [ecx]
        mov  edx, dword ptr [ecx]
        lea  eax, dword ptr [esi+0xE0]
        push eax
        call dword ptr [edx+0x48]
        mov  word ptr [esi+0xE2], 0x4E
        jmp  dword ptr [g_seDrawEnd]

    not_mine:
        jmp  dword ptr [g_seNoMatch]
    }
}

static BYTE *g_icoResume = (BYTE *)0x00A0EB73u;
static BYTE *g_icoBail   = (BYTE *)0x00A0EBACu;
static BYTE *g_icoSkip   = (BYTE *)0x00A0EB96u;

#define STAT_ICON_FRAME  0x0F

__declspec(naked) static void StatIconThunk(void)
{
    __asm {
        cmp  eax, 0x1E
        jg   bail
        cmp  ecx, 0x1E
        jge  skip
        cmp  ecx, STAT_ANTIEVIL_INDEX
        jne  keep
        mov  eax, STAT_ICON_FRAME
    keep:
        jmp  dword ptr [g_icoResume]
    bail:
        jmp  dword ptr [g_icoBail]
    skip:
        jmp  dword ptr [g_icoSkip]
    }
}

static DWORD WINAPI WatchThread(LPVOID)
{
    BOOL alive[PATCH_COUNT];
    int  live = 0;
    for (SIZE_T k = 0; k < PATCH_COUNT; k++) {
        alive[k] = (g_patches[k].addr != NULL);
        if (alive[k])
            live++;
    }
    if (live == 0)
        return 0;

    int rounds = g_watchSeconds * 1000 / WATCH_INTERVAL_MS;
    for (int i = 0; i < rounds; i++) {
        Sleep(WATCH_INTERVAL_MS);

        for (SIZE_T k = 0; k < PATCH_COUNT; k++) {
            Patch *p = &g_patches[k];
            if (!p->addr || !alive[k])
                continue;

            BYTE now[PATCH_BUF];
            if (p->len > sizeof(now) || !SafeRead(p->addr, now, p->len))
                continue;
            if (memcmp(now, p->to, p->len) == 0)
                continue;

            if (memcmp(now, p->from, p->len) == 0) {
                if (WriteBytes(p->addr, p->to, p->len)) {
                    LONG n = InterlockedIncrement(&g_rewrites);
                    A5_Log("[%s] 被改回原样（累计第 %ld 次），已重写。%.2fs",
                           p->name, n, i * WATCH_INTERVAL_MS / 1000.0);
                } else {
                    A5_Log("[%s] 被改回原样，重写失败 (err=%lu)，这一处停止看护",
                           p->name, GetLastError());
                    alive[k] = FALSE;
                }
            } else {
                char got[64];
                HexLine(now, (int)p->len, got, sizeof(got));
                A5_Log("[%s] 目标字节变成了 %s —— 既不是补丁也不是原样，这一处停止看护",
                       p->name, got);
                alive[k] = FALSE;
            }
        }
    }

    if (g_rewrites)
        A5_Log("看护结束：%d 秒内共重写 %ld 次", g_watchSeconds, g_rewrites);
    else
        A5_Log("看护结束：%d 秒内没有任何一处被改回去", g_watchSeconds);
    return 0;
}

static BOOL GroupSignaturesOk(PatchGroup group, DWORD_PTR delta, int *outMissing)
{
    BOOL ok = TRUE;
    *outMissing = 0;

    for (SIZE_T k = 0; k < PATCH_COUNT; k++) {
        Patch *p = &g_patches[k];
        if (p->group != group)
            continue;

        BYTE now[PATCH_BUF], expect[PATCH_BUF];
        BYTE *sigAddr = (BYTE *)(p->sigVa + delta);
        if (p->sigLen > sizeof(now) || !SafeRead(sigAddr, now, p->sigLen)) {
            (*outMissing)++;
            ok = FALSE;
            continue;
        }
        if (memcmp(now, p->sig, p->sigLen) == 0)
            continue;

        memcpy(expect, p->sig, p->sigLen);
        memcpy(expect + p->patchOff, p->to, p->len);
        if (memcmp(now, expect, p->sigLen) == 0)
            continue;

        (*outMissing)++;
        ok = FALSE;
    }
    return ok;
}

void CodePatch_Install(void)
{
    int wantUnwrap  = A5_PATCH_UNWRAP;
    int wantTrace   = A5_PATCH_TRACE;
    int wantNoTrace = A5_PATCH_NOTRACE;
    int wantFemGun  = A5_PATCH_FEMALEGUNNER;
    int wantFemShop = A5_PATCH_FEMGUNSHOP;
    int wantNoTn    = A5_PATCH_NOTN;
    int wantGore18  = A5_PATCH_GORE18;
    int wantObjAnim = A5_PATCH_OBJANIM;
    int wantNoNotice = A5_PATCH_NONOTICE;
    int wantIcon16  = A5_PATCH_HUDICON16;
    int wantPkgWiz  = A5_PATCH_PKGWIZARD;
    int wantAntiEvil = A5_PATCH_ANTIEVIL;
    int wantAntiEvilStat = A5_PATCH_ANTIEVIL;
    int wantPetIcon = A5_PATCH_PETICON;
    int wantNoMtu   = A5_PATCH_NOMTUPROBE;
    int wantNoRelay = A5_PATCH_NORELAY;

    if (wantNoTrace && wantTrace) {
        A5_Log("[patch] notrace=1 与 trace=1 冲突，按 notrace 来（trace 关掉）");
        wantTrace = 0;
    }
    if (wantNoTn && wantGore18) {
        A5_Log("[patch] notn=1 与 gore18=1 冲突（一个关掉前缀、一个换成 18），"
               "按 notn 来（gore18 关掉）");
        wantGore18 = 0;
    }
    A5_Log("[patch] unwrap=%d trace=%d notrace=%d femalegunner=%d femgunshop=%d "
           "notn=%d gore18=%d objanim=%d nonotice=%d hudicon16=%d pkgwizard=%d antievil=%d "
           "peticon=%d nomtuprobe=%d norelay=%d",
           wantUnwrap, wantTrace, wantNoTrace, wantFemGun, wantFemShop,
           wantNoTn, wantGore18, wantObjAnim, wantNoNotice, wantIcon16, wantPkgWiz, wantAntiEvil,
           wantPetIcon, wantNoMtu, wantNoRelay);

    HMODULE exe = GetModuleHandleW(NULL);
    if (!exe) {
        A5_Log("[patch] 取不到主模块句柄，全部跳过");
        return;
    }
    DWORD_PTR base = (DWORD_PTR)exe;
    DWORD_PTR delta = base - 0x00400000u;
    if (delta)
        A5_Log("[patch] 主模块基址 %p（相对默认 0x400000 位移 %+lld），地址已按此换算",
               exe, (long long)delta);

    if (wantFemGun) {
        int missing = 0;
        if (!GroupSignaturesOk(GRP_FEMALEGUNNER, delta, &missing)) {
            A5_Log("[patch] femalegunner 四处里有 %d 处签名对不上（换客户端了？），整组跳过"
                   " —— 只打一半会让女枪手和神枪手叠在一格上、或者点了创建角色没反应", missing);
            wantFemGun = 0;
        }
    }
    if (wantFemShop) {
        int missing = 0;
        if (!GroupSignaturesOk(GRP_FEMGUNSHOP, delta, &missing)) {
            A5_Log("[patch] femgunshop 四处里有 %d 处签名对不上（换客户端了？），整组跳过"
                   " —— 只打一半会让商城下拉框点了没反应，或者选到了那一格却是空列表", missing);
            wantFemShop = 0;
        }
    }

    if (wantObjAnim) {
        g_objAlphaSetup  += delta;
        g_objAlphaResume += delta;
        g_objAlphaBail   += delta;

        BYTE *siteAlpha = (BYTE *)(0x004B2CE9u + delta);
        *(LONG *)(kObjAlphaTo + 1) = (LONG)((BYTE *)&ObjAlphaThunk - (siteAlpha + 5));

        A5_Log("[patch] objanim ② 蹦床：%p（非空回 %p / 空跳 %p）",
               &ObjAlphaThunk, g_objAlphaResume, g_objAlphaBail);
    }

    if (wantIcon16) {
        g_icon16IsActive    += delta;
        g_icon16AddIcon     += delta;
        g_tip16GetStr       += delta;
        g_tip16Label36      += delta;
        g_click16SceneChk1  += delta;
        g_click16SceneChk2  += delta;
        g_click16OpenShop   += delta;
        g_click16Break      += delta;
        g_click16SceneVar   += delta;
        g_click16GameVar    += delta;

        BYTE *site1 = (BYTE *)(0x00A5B3C7u + delta);
        *(LONG *)(kHudIcon16To + 1) = (LONG)((BYTE *)&HudIcon16Thunk - (site1 + 5));

        BYTE *site2 = (BYTE *)(0x00A5E0E0u + delta);
        *(LONG *)kHudTip16To = (LONG)((BYTE *)&Tooltip16Thunk - (site2 + 6));

        *(DWORD *)kHudClick16To = (DWORD)(DWORD_PTR)&Click16Thunk;

        A5_Log("[patch] hudicon16 蹦床：显示 %p / 提示 %p / 点击 %p",
               &HudIcon16Thunk, &Tooltip16Thunk, &Click16Thunk);

        int missing = 0;
        if (!GroupSignaturesOk(GRP_HUDICON16, delta, &missing)) {
            A5_Log("[patch] hudicon16 三处里有 %d 处签名对不上（换客户端了？），整组跳过"
                   " —— 只打一半会让第 16 项图标出现却点不动、或者悬停没有提示", missing);
            wantIcon16 = 0;
        }
    }

#if A5_PATCH_PKGWIZARD
    if (wantPkgWiz) {
        g_pkgWizardOrig    += delta;
        g_pkgWizCashShopVt += delta;

        BYTE *sitePkg = (BYTE *)(0x00A947FCu + delta);
        *(LONG *)(kPkgWizardTo + 1) = (LONG)((BYTE *)&PkgWizardThunk - (sitePkg + 5));

        A5_Log("[patch] pkgwizard 蹦床：%p（没步骤时回落到 %p，transaction vtable 认 %p）",
               &PkgWizardThunk, g_pkgWizardOrig, g_pkgWizCashShopVt);
    }
#endif

#if A5_PATCH_ANTIEVIL
    if (wantAntiEvil) {
        g_aeGetter   += delta;
        g_aeGetStr   += delta;
        g_aeFormat   += delta;
        g_aeRowStyle += delta;
        g_aeResume   += delta;

        BYTE *siteAE = (BYTE *)(0x009CC2B1u + delta);
        *(LONG *)(kAntiEvilTo + 1) = (LONG)((BYTE *)&AntiEvilThunk - (siteAE + 5));

        A5_Log("[patch] antievil ① 装备说明 蹦床：%p（画完回 %p，取值 %p，取文案 %p）",
               &AntiEvilThunk, g_aeResume, g_aeGetter, g_aeGetStr);

        g_seTextObj += delta;
        g_seGetStr  += delta;
        g_seTotal   += delta;
        g_seFormat  += delta;
        g_seNumFmt  += delta;
        g_seDrawEnd += delta;
        g_seNoMatch += delta;

        BYTE *siteSE = (BYTE *)(0x00A119D8u + delta);
        *(LONG *)(kStatTailTo + 2) = (LONG)((BYTE *)&StatAntiEvilThunk - (siteSE + 6));

        A5_Log("[patch] antievil ② 属性面板第30格 蹦床：%p（画完跳尾段 %p，不匹配回 %p，总抗魔值 %p）",
               &StatAntiEvilThunk, g_seDrawEnd, g_seNoMatch, g_seTotal);

        g_icoResume += delta;
        g_icoBail   += delta;
        g_icoSkip   += delta;

        BYTE *siteIco = (BYTE *)(0x00A0EB69u + delta);
        *(LONG *)(kStatIconTo + 1) = (LONG)((BYTE *)&StatIconThunk - (siteIco + 5));

        A5_Log("[patch] antievil ③ 第30格图标 蹦床：%p（帧 %d，回 %p / break %p / 跳过 %p）",
               &StatIconThunk, STAT_ICON_FRAME, g_icoResume, g_icoBail, g_icoSkip);

        int missing = 0;
        if (!GroupSignaturesOk(GRP_ANTIEVILSTAT, delta, &missing)) {
            A5_Log("[patch] antievil ② 三处里有 %d 处签名对不上（换客户端了？），整组跳过"
                   " —— 只打一半会让属性面板最后一格要么还是空的、要么每帧白跑一次", missing);
            wantAntiEvilStat = 0;
        }
    }
#endif

    int installed = 0;
    for (SIZE_T k = 0; k < PATCH_COUNT; k++) {
        Patch *p = &g_patches[k];

        int want = (p->group == GRP_UNWRAP)       ? wantUnwrap
                 : (p->group == GRP_NOTRACE)      ? wantNoTrace
                 : (p->group == GRP_FEMALEGUNNER) ? wantFemGun
                 : (p->group == GRP_FEMGUNSHOP)   ? wantFemShop
                 : (p->group == GRP_NOTN)         ? wantNoTn
                 : (p->group == GRP_GORE18)       ? wantGore18
                 : (p->group == GRP_OBJANIM)      ? wantObjAnim
                 : (p->group == GRP_NONOTICE)     ? wantNoNotice
                 : (p->group == GRP_HUDICON16)    ? wantIcon16
                 : (p->group == GRP_PKGWIZARD)    ? wantPkgWiz
                 : (p->group == GRP_ANTIEVIL)     ? wantAntiEvil
                 : (p->group == GRP_ANTIEVILSTAT) ? wantAntiEvilStat
                 : (p->group == GRP_PETICON)      ? wantPetIcon
                 : (p->group == GRP_NOMTUPROBE)   ? wantNoMtu
                 : (p->group == GRP_NORELAY)      ? wantNoRelay
                 : wantTrace;
        if (!want)
            continue;

        BYTE *sigAddr = (BYTE *)(p->sigVa + delta);

        BYTE now[PATCH_BUF];
        if (p->sigLen > sizeof(now) || !SafeRead(sigAddr, now, p->sigLen)) {
            A5_Log("[%s] %p 读不出来，跳过", p->name, sigAddr);
            continue;
        }

        if (memcmp(now, p->sig, p->sigLen) != 0) {
            BYTE expect[PATCH_BUF];
            memcpy(expect, p->sig, p->sigLen);
            memcpy(expect + p->patchOff, p->to, p->len);
            if (memcmp(now, expect, p->sigLen) == 0) {
                p->addr = sigAddr + p->patchOff;
                A5_Log("[%s] %p 已经是补丁后的样子，直接接管看护", p->name, p->addr);
                installed++;
                continue;
            }
            char got[PATCH_BUF * 3 + 8], want2[PATCH_BUF * 3 + 8];
            HexLine(now, (int)p->sigLen, got, sizeof(got));
            HexLine(p->sig, (int)p->sigLen, want2, sizeof(want2));
            A5_Log("[%s] %p 签名对不上，跳过这一处 —— 换客户端了？", p->name, sigAddr);
            A5_Log("        期望 %s", want2);
            A5_Log("        实际 %s", got);
            continue;
        }

        BYTE *target = sigAddr + p->patchOff;
        if (!WriteBytes(target, p->to, p->len)) {
            A5_Log("[%s] %p VirtualProtect/写入失败 (err=%lu)，跳过",
                   p->name, target, GetLastError());
            continue;
        }

        p->addr = target;
        installed++;

        char from[64], to[64];
        HexLine(p->from, (int)p->len, from, sizeof(from));
        HexLine(p->to, (int)p->len, to, sizeof(to));
        A5_Log("[%s] %p  %s -> %s  已打上", p->name, target, from, to);
    }

    A5_Log("[patch] 共 %d 处生效", installed);

    RosterProbe_Install(delta);

    if (installed == 0)
        return;

    if (!A5_WATCH_ENABLED)
        return;

    g_watchSeconds = A5_WATCH_SECONDS;
    if (g_watchSeconds < 1)
        g_watchSeconds = 1;
    if (g_watchSeconds > 3600)
        g_watchSeconds = 3600;

    HANDLE th = CreateThread(NULL, 0, WatchThread, NULL, 0, NULL);
    if (th)
        CloseHandle(th);
    else
        A5_Log("[patch] 看护线程起不来 (err=%lu)，补丁本身不受影响", GetLastError());
}
