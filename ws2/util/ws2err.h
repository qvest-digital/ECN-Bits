/* © mirabilos Ⓕ CC0 */

#ifndef UTIL_WS2ERR_H
#define UTIL_WS2ERR_H

#if defined(_WIN32) || defined(WIN32)
#include "rpl_err.h"
#else
#include <err.h>
#define ws2err	err
#define ws2warn	warn
#endif

#endif
