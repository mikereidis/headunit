
//#ifndef UTILS_INCLUDED

//  #define UTILS_INCLUDED

#undef NDEBUG // Ensure debug stuff

  #define hu_STATE_INITIAL   0
  #define hu_STATE_STARTIN   1
  #define hu_STATE_STARTED   2
  #define hu_STATE_STOPPIN   3
  #define hu_STATE_STOPPED   4
  //#define hu_STATE_   5

  #include <stdio.h>
  #include <stdlib.h>
  #include <stdarg.h>
  #include <stdint.h>

  #include <string.h>
  #include <signal.h>

  #include <pthread.h>

  #include <errno.h>

  #include <unistd.h>
  #include <fcntl.h>
  #include <sys/stat.h>

  #include <dirent.h>                                                   // For opendir (), readdir (), closedir (), DIR, struct dirent.

  extern int ena_log_extra;
  extern int ena_log_verbo;
  extern int ena_log_send;

  #define byte unsigned char
  #define DEFBUF  65536     //16384                                                 // Default buffer size is maximum for USB

  #define DEF_BUF 512                                                   // For Ascii strings and such


  #ifdef __ANDROID_API__
    #include <android/log.h>
  #else
      // UNKNOWN    0
    #define ANDROID_LOG_DEFAULT 1
    #define ANDROID_LOG_VERBOSE 2
    #define ANDROID_LOG_DEBUG   3
      // INFO       4
    #define ANDROID_LOG_WARN    5
    #define ANDROID_LOG_ERROR   6
      // FATAL      7
      // SILENT     8
  #endif

  #define hu_LOG_EXT   ANDROID_LOG_DEFAULT
  #define hu_LOG_VER   ANDROID_LOG_VERBOSE
  #define hu_LOG_DEB   ANDROID_LOG_DEBUG
  #define hu_LOG_WAR   ANDROID_LOG_WARN
  #define hu_LOG_ERR   ANDROID_LOG_ERROR

#ifdef NDEBUG

  #define  logx(...)
  #define  logv(...)
  #define  logd(...)
  #define  logw(...)
  #define  loge(...)

#else

  #define  logx(...)  hu_log(hu_LOG_EXT,LOGTAG,__func__,__VA_ARGS__)
  #define  logv(...)  hu_log(hu_LOG_VER,LOGTAG,__func__,__VA_ARGS__)
  #define  logd(...)  hu_log(hu_LOG_DEB,LOGTAG,__func__,__VA_ARGS__)
  #define  logw(...)  hu_log(hu_LOG_WAR,LOGTAG,__func__,__VA_ARGS__)
  #define  loge(...)  hu_log(hu_LOG_ERR,LOGTAG,__func__,__VA_ARGS__)

#endif

  int hu_log (int prio, const char * tag, const char * func, const char * fmt, ...);


  unsigned long ms_get          ();
  unsigned long ms_sleep        (unsigned long ms);
  void hex_dump                 (char * prefix, int width, unsigned char * buf, int len);
  char * write_tail_buffer_get  (int len);
  char * read_head_buffer_get   (int * len);

  #ifndef __ANDROID_API__
    #define strlcpy   strncpy
    #define strlcat   strncat
  #endif

  //#ifndef __ANDROID_API__
  #ifdef  DONT_USE
#ifndef HAVE_STRLCAT
/*
 * '_cups_strlcat()' - Safely concatenate two strings.
 */

size_t                  /* O - Length of string */
strlcat(char       *dst,        /* O - Destination string */
              const char *src,      /* I - Source string */
          size_t     size)      /* I - Size of destination string buffer */
{
  size_t    srclen;         /* Length of source string */
  size_t    dstlen;         /* Length of destination string */


 /*
  * Figure out how much room is left...
  */

  dstlen = strlen(dst);
  size   -= dstlen + 1;

  if (!size)
    return (dstlen);        /* No room, return immediately... */

 /*
  * Figure out how much room is needed...
  */

  srclen = strlen(src);

 /*
  * Copy the appropriate amount...
  */

  if (srclen > size)
    srclen = size;

  memcpy(dst + dstlen, src, srclen);
  dst[dstlen + srclen] = '\0';

  return (dstlen + srclen);
}
#endif /* !HAVE_STRLCAT */

#ifndef HAVE_STRLCPY
/*
 * '_cups_strlcpy()' - Safely copy two strings.
 */

size_t                  /* O - Length of string */
strlcpy(char       *dst,        /* O - Destination string */
              const char *src,      /* I - Source string */
          size_t      size)     /* I - Size of destination string buffer */
{
  size_t    srclen;         /* Length of source string */


 /*
  * Figure out how much room is needed...
  */

  size --;

  srclen = strlen(src);

 /*
  * Copy the appropriate amount...
  */

  if (srclen > size)
    srclen = size;

  memcpy(dst, src, srclen);
  dst[srclen] = '\0';

  return (srclen);
}
#endif /* !HAVE_STRLCPY */

  #endif


  // Android USB device priority:


//1d6b  Linux Foundation  PIDs:	0001  1.1 root hub  0002  2.0 root hub  -0003  3.0 root hub
//05c6  Qualcomm, Inc.

#define USB_VID_GOO 0x18D1    // The vendor ID should match Google's ID ( 0x18D1 ) and the product ID should be 0x2D00 or 0x2D01 if the device is already in accessory mode (case A).

#define USB_VID_HTC 0x0bb4
#define USB_VID_MOT 0x22b8

#define USB_VID_SAM 0x04e8
#define USB_VID_O1A 0xfff6  // Samsung ?

#define USB_VID_SON 0x0fce
#define USB_VID_LGE 0xfff5

#define USB_VID_LIN 0x1d6b
#define USB_VID_QUA 0x05c6
#define USB_VID_COM 0x1519  // Comneon

#define USB_VID_ASE 0x0835  // Action Star Enterprise

//#define USB_PID_OM8 0x0f87
//#define USB_PID_MOG 0x2e76


#define USB_PID_ACC_MIN       0x2D00
#define USB_PID_ACC_MAX       0x2D05

//#define USB_PID_ACC         0x2D00      // Accessory                  100
//#define USB_PID_ACC_ADB     0x2D01      // Accessory + ADB            110
//#define USB_PID_AUD         0x2D02      //                   Audio    001
//#define USB_PID_AUD_ADB     0x2D03      //             ADB + Audio    011
//#define USB_PID_ACC_AUD     0x2D04      // Accessory       + Audio    101
//#define USB_PID_ACC_AUD_ADB 0x2D05      // Accessory + ADB + Audio    111
// No 000 (Nothing) or 010 (adb only, which is default)


//#endif  //#ifndef UTILS_INCLUDED

