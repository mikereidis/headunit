
  // AA Dump API:


  #define LOGTAG "hu_aad"
  #include "hu_uti.h"

#ifndef NDEBUG

  // AA Dump Internal:

  char * iaad_msg_type_str_get (int msg_type, int type, int len) {  // type 1 = rx from HU > AA     type 2 = tx from AA > HU
    #define k3 32768+
    switch (msg_type) {
      case 0:   return ("Media Data");
      case 1:   if (type == 1) return ("Version req");  else if (type == 2) return ("Codec Config req"); else return ("1 !");    // rx/tx
      case 2:   return ("Version rsp");              // short:major  short:minor   short:status
      case 3:   return ("SSL Handshake req-rsp");
      case 4:   return ("SSL Auth Complete not");
      case 5:   return ("Service Disc req");
      case 6:   return ("Service Disc rsp");
      case 7:   return ("Channel Open req");
      case 8:   return ("Channel Open rsp");         // byte:status
      case 9:   return ("9 ??");
      case 10:  return ("10 ??");
      case 11:  return ("Ping req");
      case 12:  return ("Ping rsp");
      case 13:  return ("Nav Focus req");
      case 14:  return ("Nav Focus not");      // NavFocusType
      case 15:  return ("Byebye req");
      case 16:  return ("Byebye rsp");
      case 17:  return ("Voice Session not");
      case 18:  return ("Audio Focus req");
      case 19:  return ("Audio Focus not");    // AudioFocusType   (AudioStreamType ?)

      case 5123:return ("SSL Change Cipher Spec");  // 0x14, 0x03
      case 5379:return ("SSL Alert");               // 0x15, 0x03
      case 5635:return ("SSL Handshake");           // 0x16, 0x03
      case 5891:return ("SSL App Data");            // 0x17, 0x03

      case k3 0: return ("Media Setup req");
      case k3 1: if (type == 2) return ("Sensor/Media Start req");  else if (type == 1) return ("Touch not");       else return ("32769 !");        // type 2 AA->HU also Media Start req ????
      case k3 2: if (type == 1) return ("Sensor Start rsp");  else if (type == 2) return ("Input Start Stop req");  else return ("32770 !");  // type 2 AA->HU also Media Stop req ?
      case k3 3: if (len  == 8) return ("Media Config rsp");  else if (len  == 4) return ("Key Binding rsp"); else return ("Sensor Batch not");
      case k3 4: return ("Video Ack rsp");

      case k3 8: return ("VideoFocus change not");

      case 65535:return ("Framing Error not");

    }
    return ("Unknown");
  }

  void iaad_adj (unsigned int * rmv, unsigned char ** buf, unsigned int * lft, unsigned int adj) {
    * buf += adj;
    * rmv += adj;
    * lft -= adj;
  }

  unsigned int iaad_vint_dec (int log_dmp, unsigned char * buf, unsigned int len, unsigned long * vintp) {  // Varint decode
    unsigned int rmv = 0, adj = 0;
    unsigned int lft = len;
    unsigned long vint = 0;

    adj = 1;
    while (rmv < 10 && lft > 0) {  // Assumed unsigned, 9 bytes = 63 bits
      unsigned char new_byte = buf [0];

      vint |= ((new_byte & 0x7f) << (7 * rmv));
      iaad_adj (& rmv, & buf, & lft, adj);

      if ((new_byte & 0x80) == 0)
        break;
    }
    if (rmv > 9) {
      if (log_dmp)
        loge ("rmv > 9");
      rmv = 0xffffffff;
    }

    if (vintp)
      * vintp = vint;
    return (rmv);
  }

  unsigned int iaad_dmp_vint (int log_dmp, unsigned int n, unsigned char * buf, unsigned int len) {
    unsigned int rmv = 0, adj = 0;
    unsigned int lft = len;
    unsigned long vint = 0;

    unsigned int num = buf [0] >> 3;
    adj = 1;
    iaad_adj (& rmv, & buf, & lft, adj);

    adj = iaad_vint_dec (log_dmp, buf, lft, & vint);
    iaad_adj (& rmv, & buf, & lft, adj);

    if (log_dmp) {
      //logd ("n: %1.1d  num: %1.1d  vint: %20ld", n, num, vint);
      //logd ("%1.1d  %1.1d  %6ld", n, num, vint);

      unsigned char str_buf [256] = {0};
      //snprintf (str_buf, sizeof (str_buf), "%1.1d", num);
      int ctr = 0;
      for (ctr = 0; ctr < n - 1; ctr ++)
        strncat (str_buf, "                    ", sizeof (str_buf));


      //unsigned char str_buf2 [256] = {0};
      //snprintf (str_buf2, sizeof (str_buf), "%s%1.1d", str_buf, num);
      logd ("%s%1.1d %4x %5ld", str_buf, num, vint, vint);
    }
    return (rmv);
  }

  unsigned int iaad_dmp_n (int log_dmp, unsigned int n, unsigned char * buf, unsigned int len);

  unsigned int iaad_dmp_arry (int log_dmp, unsigned int n, unsigned char * buf, unsigned int len) {
    if (n > 8) {
      //if (log_dmp)
        loge ("iaad_dmp_arry n: %d", n);
    }
    unsigned int rmv = 0, adj = 0;
    unsigned int lft = len;
    unsigned int num = buf [0] >> 3;
    adj = 1;
    iaad_adj (& rmv, & buf, & lft, adj);

    unsigned long alen = 0;
    adj = iaad_vint_dec (log_dmp, buf, lft, & alen);                    // Get length of array
    if (adj > 8192 || alen > 8192) {
      if (log_dmp)
        loge ("iaad_dmp_arry iaad_vint_dec adj: %d  alen: %ld", adj, alen);
      return (0xffffffff);
    }
    //logd ("iaad_dmp_arry iaad_vint_dec adj: %d  alen: %ld", adj, alen);
    iaad_adj (& rmv, & buf, & lft, adj);                                  // Point to array

    if (alen == 0)
      return (rmv);
    if (alen < 0) {
      if (log_dmp)
        loge ("iaad_dmp_arry alen: %ld", alen);
      return (rmv);
    }

    if (log_dmp) {
      //logd ("iaad_dmp_arry n: %d  num: %d  alen: %ld", n, num, alen);      // Dump raw array
      unsigned char str_buf [256] = {0};
      int ctr = 0;
      for (ctr = 0; ctr < n - 1; ctr ++)
        strncat (str_buf, "                    ", sizeof (str_buf));

      unsigned char str_buf2 [256] = {0};
      snprintf (str_buf2, sizeof (str_buf), "%s%1.1d", str_buf, num);      // Dump raw array
      hex_dump (str_buf2, 16, buf, alen);
    }

    adj = iaad_dmp_n (0, n + 1, buf, alen);                              // Try first without error logging to see if this is a valid array
    if (adj != alen || adj > 8192) {                                            //  "|| rmv + adj != len) {" only works if array is last item in packet
      //logd ("Array not embedded data adj: %d  alen: %ld  rmv: %d  len: %d", adj, alen, rmv, len);
      return (rmv + alen);
    }

    adj = iaad_dmp_n (log_dmp, n + 1, buf, alen);
    /*if (adj > 8192) {
      if (log_dmp)
        loge ("iaad_dmp_arry iaad_dmp_arry adj: %d  alen: %ld", adj, alen);
      return (0xffffffff);
    }*/
    iaad_adj (& rmv, & buf, & lft, adj);

    return (rmv);
  }

  unsigned int iaad_dmp_n (int log_dmp, unsigned int n, unsigned char * buf, unsigned int len) {
    unsigned int rmv = 0, adj = 0;
    unsigned int lft = len;

    while (lft > 0) {                                                   // While bytes left to process...
      //loge ("!!!! 1  n: %d  adj: %d  len: %d  lft: %d  rmv: %d  buf: %p", n, adj, len, lft, rmv, buf);
      if ((((unsigned int) buf [0]) & 0x07) == 0) {                     // If varint...
        adj = iaad_dmp_vint (log_dmp, n, buf, lft);                      // Adjust past the varint
      }

      else if ((((unsigned int) buf [0]) & 0x07) == 2) {                // If array...
        adj = iaad_dmp_arry (log_dmp, n, buf, lft);                      // Adjust past the array
      }

      else {                                                            // Else if unknown...
        if (log_dmp)
          loge ("iaad_dmp_n buf[0]: 0x%x", buf [0]);
        return (rmv);
      }

      iaad_adj (& rmv, & buf, & lft, adj);                                // Apply adjustment
      //loge ("!!!! 2  n: %d  adj: %d  len: %d  lft: %d  rmv: %d  buf: %p", n, adj, len, lft, rmv, buf);
    }

    if (lft != 0 || rmv != len || rmv < 0)
      if (log_dmp)
        loge ("iaad_dmp_n after content len: %d  lft: %d  rmv: %d  buf: %p", len, lft, rmv, buf);
    return (rmv);
  }

  char * msg_type_enc_get (int type) {
    switch (type) {
      case 5123:return ("SSL Change Cipher Spec");  // 0x14, 0x03
      case 5379:return ("SSL Alert");               // 0x15, 0x03
      case 5635:return ("SSL Handshake");           // 0x16, 0x03
      case 5891:return ("SSL App Data");            // 0x17, 0x03
    }
    return (NULL);
  }

  unsigned int hu_aad_dmp (unsigned char * prefix, int type, unsigned char * buf, int len) {   // type 1 = rx from HU > AA     type 2 = tx from AA > HU
    unsigned int rmv = 0, adj = 0;
    unsigned int lft = len;

    if (len < 2) {                                                      // Need at least 2 bytes for msg_type
      loge ("hu_aad_dmp len: %d", len);
      return (rmv);
    }

    hex_dump (prefix, 16, buf, len);

    int checksum = 0;
    int ctr = 0;
    for (ctr = 16; ctr < len; ctr ++)
      checksum += (int) buf [ctr];

    unsigned int msg_type = (((unsigned int) buf [0]) << 8) + ((unsigned int) buf [1]);
    //msg_type = buf [0] << 8;
    //msg_type += buf [1];

    adj = 2;
    iaad_adj (& rmv, & buf, & lft, adj);                                // Adjust = past the msg_type to content
    char * msg_type_str = iaad_msg_type_str_get (msg_type, type, len);  // Get msg_type string
    logd ("len: %5d  checksum: %12d  msg_type: %6d (%s)", len, checksum, msg_type, msg_type_str);

    if (msg_type_enc_get (type) == NULL) {                              // Ignore encrypted data
      return (len);
    }

    if (lft == 0)                                                       // If no content
      return (rmv);
    if (lft < 2) {                                                      // Need at least 2 bytes for content
      loge ("hu_aad_dmp len: %d  lft: %d", len, lft);
      return (rmv);
    }

    //hex_dump ("0  ", 16, buf, len - 2);

    adj = iaad_dmp_n (1, 1, buf, lft);                                   // Dump the content w/ n=1
    iaad_adj (& rmv, & buf, & lft, adj);                                  // Adjust past the content (to nothing, presumably)
    if (lft != 0 || rmv != len || rmv < 0)
      loge ("hu_aad_dmp after content len: %d  lft: %d  rmv: %d  buf: %p", len, lft, rmv, buf);

    logd ("");  // Empty line

    return (rmv);
  }
#endif

