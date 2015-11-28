
  //

  #define LOGTAG "hu_tcp"
  #include "hu_uti.h"                                                  // Utilities

  int itcp_state = 0; // 0: Initial    1: Startin    2: Started    3: Stoppin    4: Stopped


  #ifdef __ANDROID_API__
  //#include <libtcp.h>
  #else
  //#include <libtcp.h>
  #endif

  #include <sys/types.h>
  #include <sys/socket.h>
  #include <netinet/in.h>
  #include <netinet/ip.h>
  #include <netinet/tcp.h>

  //#include <netdb.h> 


    // Data: 

  //struct libtcp_device_handle * itcp_dev_hndl      = NULL;
  //libtcp_device *               itcp_best_device  = NULL;
  int   itcp_ep_in          = -1;
  int   itcp_ep_out         = -1;

  char * itcp_error_get (int error) {
    switch (error) {
      case 0: //LIBtcp_SUCCESS:                                              // 0
        return ("LIBtcp_SUCCESS");
      case -1: //LIBtcp_ERROR_IO:                                             // -1
        return ("LIBtcp_ERROR_IO");
/*
      case LIBtcp_ERROR_INVALID_PARAM:                                  // -2
        return ("LIBtcp_ERROR_INVALID_PARAM");
      case LIBtcp_ERROR_ACCESS:                                         // -3
        return ("LIBtcp_ERROR_ACCESS");
      case LIBtcp_ERROR_NO_DEVICE:                                      // -4
        return ("LIBtcp_ERROR_NO_DEVICE");
      case LIBtcp_ERROR_NOT_FOUND:                                      // -5
        return ("LIBtcp_ERROR_NOT_FOUND");
      case LIBtcp_ERROR_BUSY:                                           // -6
        return ("LIBtcp_ERROR_BUSY");
      case LIBtcp_ERROR_TIMEOUT:                                        // -7
        return ("LIBtcp_ERROR_TIMEOUT");
      case LIBtcp_ERROR_OVERFLOW:                                       // -8
        return ("LIBtcp_ERROR_OVERFLOW");
      case LIBtcp_ERROR_PIPE:                                           // -9
        return ("LIBtcp_ERROR_PIPE");
      case LIBtcp_ERROR_INTERRUPTED:                                    // -10
        return ("Error:LIBtcp_ERROR_INTERRUPTED");
      case LIBtcp_ERROR_NO_MEM:                                         // -11
        return ("LIBtcp_ERROR_NO_MEM");
      case LIBtcp_ERROR_NOT_SUPPORTED:                                  // -12
        return ("LIBtcp_ERROR_NOT_SUPPORTED");
      case LIBtcp_ERROR_OTHER:                                          // -99
        return ("LIBtcp_ERROR_OTHER");
*/
    }
    return ("LIBtcp_ERROR Unknown error");//: %d", error);
  }


  int itcp_bulk_transfer (int ep, byte * buf, int len, int tmo) { // 0 = unlimited timeout

    char * dir = "recv";
    if (ep == itcp_ep_out)
      dir = "send";

    if (itcp_state != hu_STATE_STARTED) {
      logd ("CHECK: itcp_bulk_transfer start itcp_state: %d (%s) dir: %s  ep: 0x%02x  buf: %p  len: %d  tmo: %d", itcp_state, state_get (itcp_state), dir, ep, buf, len, tmo);
      return (-1);
    }

    if (ena_log_extra) {
      logd ("Start dir: %s  ep: 0x%02x  buf: %p  len: %d  tmo: %d", dir, ep, buf, len, tmo);
    }
//#ifndef NDEBUG
    if (ena_hd_tra_send && ep == itcp_ep_out)
      hex_dump ("US: ", 16, buf, len);
//#endif

    #define LIBtcp_ERROR_TIMEOUT -2
    int tcp_err = LIBtcp_ERROR_TIMEOUT;
    int total_bytes_xfrd = 0;
    int bytes_xfrd = 0;
      // You should also check the transferred parameter for bulk writes. Not all of the data may have been written.
      // Also check transferred when dealing with a timeout error code. libtcp may have to split your transfer into a number of chunks to satisfy underlying O/S requirements,
      // meaning that the timeout may expire after the first few chunks have completed. libtcp is careful not to lose any data that may have been transferred;
      // do not assume that timeout conditions indicate a complete lack of I/O.

    errno = 0;
    int continue_transfer = 1;
    while (continue_transfer) {
//      tcp_err = libtcp_bulk_transfer (itcp_dev_hndl, ep, buf, len, & bytes_xfrd, tmo);

      continue_transfer = 0;
      if (bytes_xfrd > 0)
        total_bytes_xfrd += bytes_xfrd;

      if (bytes_xfrd > 0 && tcp_err == LIBtcp_ERROR_TIMEOUT) {
        continue_transfer = 1;
        loge ("CONTINUE dir: %s  len: %d  bytes_xfrd: %d  tcp_err: %d (%s)  errno: %d (%s)", dir, len, bytes_xfrd, tcp_err, itcp_error_get (tcp_err), errno, strerror (errno));
        buf += bytes_xfrd;
        len -= bytes_xfrd;
      }
      else if (tcp_err < 0 && tcp_err != LIBtcp_ERROR_TIMEOUT)
        loge ("Done dir: %s  len: %d  bytes_xfrd: %d  tcp_err: %d (%s)  errno: %d (%s)", dir, len, bytes_xfrd, tcp_err, itcp_error_get (tcp_err), errno, strerror (errno));
      else if (ena_log_verbo && tcp_err != LIBtcp_ERROR_TIMEOUT)// && (ena_hd_tra_send || ep == itcp_ep_in))
        logd ("Done dir: %s  len: %d  bytes_xfrd: %d  tcp_err: %d (%s)  errno: %d (%s)", dir, len, bytes_xfrd, tcp_err, itcp_error_get (tcp_err), errno, strerror (errno));
      else if (ena_log_extra)
        logd ("Done dir: %s  len: %d  bytes_xfrd: %d  tcp_err: %d (%s)  errno: %d (%s)", dir, len, bytes_xfrd, tcp_err, itcp_error_get (tcp_err), errno, strerror (errno));

      bytes_xfrd = 0;
    }

    if (total_bytes_xfrd > 16384) {                                     // Previously caused problems that seem fixed by raising tcp Rx buffer from 16K to 64K
                                                                        // Streaming mode by first reading packet length may be better but may also create sync or other issues
      //loge ("total_bytes_xfrd: %d     ???????????????? !!!!!!!!!!!!!!!!!!!!!!!!!!!!! !!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ?????????????????????????", total_bytes_xfrd);
    }

    if (total_bytes_xfrd <= 0 && tcp_err < 0) {
      if (errno == EAGAIN || errno == ETIMEDOUT || tcp_err == LIBtcp_ERROR_TIMEOUT)
        return (0);

      hu_tcp_stop ();  // Other errors here are fatal, so stop tcp
      return (-1);
    }

    //if (ena_log_verbo)
    //  logd ("Done dir: %s  len: %d  total_bytes_xfrd: %d", dir, len, total_bytes_xfrd);

//#ifndef NDEBUG
    if (ena_hd_tra_recv && ep == itcp_ep_in)
      hex_dump ("UR: ", 16, buf, total_bytes_xfrd);
//#endif


    return (total_bytes_xfrd);
  }

  int tcp_so_fd = -1;  // Socket FD
  int tcp_io_fd = -1;  // IO FD returned by accept()

  int hu_tcp_recv (byte * buf, int len, int tmo) {
    //int ret = itcp_bulk_transfer (itcp_ep_in, buf, len, tmo);         // milli-second timeout
      if (tcp_io_fd < 0)                                                // If TCP IO not ready...
        return (-1);

    long ms_tmo = ms_get ();
    if (tmo > 0)                                                        // If tmo valid...
      ms_tmo += tmo;                                                    // Timeout time = now + tmo
    else
      ms_tmo += 1000;                                                   // Else default 1 second for special case

    int avail = 0;
    while (tmo == -1 && avail < len && ms_get () < ms_tmo) {            // If tmo == -1 for special "get exactly this many bytes" function
      avail = recv (tcp_io_fd, buf, len, MSG_PEEK);                     // Peek to see how many bytes are available
      logv ("hu_tcp_recv avail: %d  len: %d", avail, len);
      if (avail < len)                                                  // If we don't have the needed bytes yet...
        ms_sleep (1);//3);
    }

    if (tmo > 0) {
      sock_tmo_set (tcp_io_fd, tmo);
    }

    errno = 0;
    int ret = 0;

    while (ms_get () < ms_tmo) {
      //loge ("7777 BEFORE READ");
      //loge ("7777 BEFORE READ 2");
      ret = read (tcp_io_fd, buf, len);
      //loge ("7777 AFTER READ");
      //loge ("7777 AFTER READ 2 ret: %d", ret);
      //loge ("7777 AFTER READ 3");
      if (ret <= 0) {
        if (ret == 0 || errno == EAGAIN || errno == ETIMEDOUT)
          return (0);
        loge ("Error read  errno: %d (%s)", errno, strerror (errno));
        //ms_sleep (101);                                                 // Sleep 0.1 second to try to clear errors
        //close (tcp_io_fd);
        //ms_sleep (101);                                                 // Sleep 0.1 second to try to clear errors
        //continue;
        return (ret);
      }
      else {
        //logd ("cli_len: %d  fam: %d  addr: 0x%x  port: %d",cli_len,cli_addr.sin_family, ntohl (cli_addr.sin_addr.s_addr), ntohs (cli_addr.sin_port));
        //hex_dump ("", 32, cmd_buf, n);
        return (ret);
      }
    }
    return (ret);
  }

  int hu_tcp_send (byte * buf, int len, int tmo) {
    //int ret = itcp_bulk_transfer (itcp_ep_out, buf, len, tmo);      // milli-second timeout
    if (tcp_io_fd < 0)
      return (-1);

      errno = 0;
      int ret = write (tcp_io_fd, buf, len);
      if (ret != len) {             // Write, if can't write full buffer...
        loge ("Error write  errno: %d (%s)", errno, strerror (errno));
        //ms_sleep (101);                                                 // Sleep 0.1 second to try to clear errors
      }
      //close (tcp_io_fd);
    //}
    //close (tcp_so_fd);

    return (ret);
  }


  int itcp_deinit () {                                              // !!!! Need to better reset and wait a while to kill transfers in progress and auto-restart properly

    if (tcp_io_fd >= 0)
      close (tcp_io_fd);
    tcp_io_fd = -1;

    if (tcp_so_fd >= 0)
      close (tcp_so_fd);
    tcp_so_fd = -1;

    logd ("Done");

    return (0);
  }


  #define CS_FAM   AF_INET

  #define CS_SOCK_TYPE    SOCK_STREAM
  #define   RES_DATA_MAX  65536

  //int gen_server_exiting = 0;

  //int tmo = 100;

  struct sockaddr_in  cli_addr = {0};
  socklen_t cli_len = 0;

  int wifi_direct = 1;//0;

  int itcp_accept (int tmo) {

    if (tcp_so_fd < 0)
      return (-1);

    if (tcp_io_fd < 0)                                                  // If we don't have an IO socket yet...
      sock_tmo_set (tcp_so_fd, tmo);//3000);//tmo);                     // Set socket timeout for polling every tmo milliseconds

    memset ((char *) & cli_addr, sizeof (cli_addr), 0);                 // ?? Don't need this ?
    //cli_addr.sun_family = CS_FAM;                                     // ""
    cli_len = sizeof (cli_addr);

    errno = 0;
    int ret = 0;
    if (wifi_direct) {
      tcp_io_fd = accept (tcp_so_fd, (struct sockaddr *) & cli_addr, & cli_len);
      if (tcp_io_fd < 0) {
        loge ("Error accept errno: %d (%s)", errno, strerror (errno));
        return (-1);
      }
    }
    else {
      cli_addr.sin_addr.s_addr = htonl (INADDR_LOOPBACK);
      cli_addr.sin_family = AF_INET;
      cli_addr.sin_port = htons (5277);
      //logd ("cli_len: %d  fam: %d  addr: 0x%x  port: %d",cli_len,cli_addr.sin_family, ntohl (cli_addr.sin_addr.s_addr), ntohs (cli_addr.sin_port));

      ret = connect (tcp_so_fd, (const struct sockaddr *) & cli_addr, cli_len);
      if (ret != 0) {
        loge ("Error connect errno: %d (%s)", errno, strerror (errno));
        return (-1);
      }
      tcp_io_fd = tcp_so_fd;
    }

/*
    int flag = 1;
    int ret = setsockopt (tcp_io_fd, SOL_TCP, TCP_NODELAY, & flag, sizeof (flag));  // Only need this for IO socket from accept() ??
    if (ret != 0)
      loge ("setsockopt TCP_NODELAY errno: %d (%s)", errno, strerror (errno));
    else
      logd ("setsockopt TCP_NODELAY Success");
*/
    if ( cli_addr.sin_addr.s_addr == htonl (INADDR_LOOPBACK) )
      logd ("Success accept() from local host loopback");
    else
      logd ("Success accept() from host: 0x%x", inet_ntoa (cli_addr.sin_addr.s_addr));  //  host: 0xa038 50f0  = 160.56.80.240  ??

    return (tcp_so_fd);
  }

  struct sockaddr_in  srv_addr = {0};
  socklen_t srv_len = 0;
  int itcp_init (byte ep_in_addr, byte ep_out_addr) {
    logd ("ep_in_addr: %d  ep_out_addr: %d", ep_in_addr, ep_out_addr);

    itcp_ep_in  = -1;
    itcp_ep_out = -1;
    int net_port = 30515;

    int cmd_len = 0, ctr = 0;
    //struct hostent *hp;
    unsigned char cmd_buf [DEF_BUF] ={0};

    errno = 0;
    if ((tcp_so_fd = socket (CS_FAM, CS_SOCK_TYPE, 0)) < 0) {              // Create socket
      loge ("gen_server_loop socket  errno: %d (%s)", errno, strerror (errno));
      return (-1);
    }
//*
    int flag = 1;
    int ret = setsockopt (tcp_so_fd, SOL_TCP, TCP_NODELAY, & flag, sizeof (flag));  // Only need this for IO socket from accept() ??
    if (ret != 0)
      loge ("setsockopt TCP_NODELAY errno: %d (%s)", errno, strerror (errno));
    else
      logd ("setsockopt TCP_NODELAY Success");
//*/
    sock_reuse_set (tcp_so_fd);

    //if (tmo != 0)
    //  sock_tmo_set (tcp_so_fd, tmo);                                   // If polling mode, set socket timeout for polling every tmo milliseconds

if (wifi_direct) {
    memset ((char *) & srv_addr, sizeof (srv_addr), 0);

    srv_addr.sin_family = AF_INET;
    srv_addr.sin_addr.s_addr = htonl (INADDR_ANY);                      // Will bind to any/all Interfaces/IPs

    //errno = 0;
    //hp = gethostbyname ("localhost");
    //if (hp == 0) {
    //  loge ("Error gethostbyname  errno: %d (%s)", errno, strerror (errno));
    //  return (-2);
    //}
    //bcopy ((char *) hp->h_addr, (char *) & srv_addr.sin_addr, hp->h_length);
    srv_addr.sin_port = htons (net_port);
    srv_len = sizeof (struct sockaddr_in);

    logd ("srv_len: %d  fam: %d  addr: 0x%x  port: %d", srv_len, srv_addr.sin_family, ntohl (srv_addr.sin_addr.s_addr), ntohs (srv_addr.sin_port));
    errno = 0;
    if (bind (tcp_so_fd, (struct sockaddr *) & srv_addr, srv_len) < 0) {   // Bind socket to server address
      loge ("Error bind  errno: %d (%s)", errno, strerror (errno));
      loge ("Inet stream continuing despite bind error");               // OK to continue w/ Internet Stream
    }

    // Done after socket() and before bind() so don't repeat it here ?
    //if (tmo != 0)
    //  sock_tmo_set (tcp_so_fd, tmo);                                   // If polling mode, set socket timeout for polling every tmo milliseconds

  // Get command from client
    errno = 0;
    if (listen (tcp_so_fd, 5)) {                                           // Backlog= 5; likely don't need this
      loge ("Error listen  errno: %d (%s)", errno, strerror (errno));
      return (-4);
    }
  
    logd ("itcp_init Ready");
    if (file_get ("/data/data/ca.yyx.hu/files/nfc_wifi")) {
      int ret = system ("su -c \"am start -a android.nfc.action.NDEF_DISCOVERED -t application/com.google.android.gms.car -n com.google.android.gms/.car.FirstActivity -f 32768 1>/dev/null 2>/dev/null\""); // !! Binaries like ssd that write to stdout cause C system() to crash !
      logd ("itcp_init am start ret: %d", ret);
    }
}

    //tcp_io_fd = -1;
    while (tcp_io_fd < 0) {                                             // While we don't have an IO socket file descriptor...
      itcp_accept (100);                                                // Try to get one with 100 ms timeout
    }
    logd ("itcp_accept done");

    return (0);
  }

  int hu_tcp_stop () {
    itcp_state = hu_STATE_STOPPIN;
    logd ("  SET: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));
    int ret = itcp_deinit ();
    itcp_state = hu_STATE_STOPPED;
    logd ("  SET: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));
    return (ret);
  }


  int hu_tcp_start (byte ep_in_addr, byte ep_out_addr) {
    int ret = 0;

    if (itcp_state == hu_STATE_STARTED) {
      logd ("CHECK: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));
      return (0);
    }

    //#include <sys/prctl.h>
    //ret = prctl (PR_GET_DUMPABLE, 1, 0, 0, 0);  // 1 = SUID_DUMP_USER
    //logd ("prctl ret: %d", ret);

    itcp_state = hu_STATE_STARTIN;
    logd ("  SET: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));

    ret = itcp_init (ep_in_addr, ep_out_addr);
    if (ret < 0) {
      loge ("Error itcp_init");
      itcp_deinit ();
      itcp_state = hu_STATE_STOPPED;
      logd ("  SET: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));
      return (-1);
    }
    logd ("OK itcp_init");

    itcp_state = hu_STATE_STARTED;
    logd ("  SET: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));
    return (0);
  }

