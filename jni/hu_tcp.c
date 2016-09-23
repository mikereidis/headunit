
  //

  #define LOGTAG "hu_tcp"
  #include "hu_uti.h"                                                  // Utilities

  int itcp_state = 0; // 0: Initial    1: Startin    2: Started    3: Stoppin    4: Stopped
  long myip_string=2130706433; //Default case is loopback we might receive other address.

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
  #include <pthread.h>

  int   itcp_ep_in          = -1;
  int   itcp_ep_out         = -1;
  byte my_buffer [1048576] = {0};

  int total_buffer = 0;
  int last_used=0;
  pthread_mutex_t lock;
  pthread_t tid;
  
  void *myThreadFun(void *sock)
{
	int data_length=0;
	int fd = *(int *)sock;
	
	//logd("in the Thread");


    data_length=recv(fd,&my_buffer[total_buffer],131080,MSG_EOR);
	if (data_length>0)
		total_buffer += data_length;

	//logd("Successfull read of %d bytes, current counter: %d",data_length,total_buffer);

}
  
  
  char * itcp_error_get (int error) {
    switch (error) {
      case 0: //LIBtcp_SUCCESS:                                              // 0
        return ("LIBtcp_SUCCESS");
      case -1: //LIBtcp_ERROR_IO:                                             // -1
        return ("LIBtcp_ERROR_IO");

    }
    return ("LIBtcp_ERROR Unknown error");//: %d", error);
  }


  
  int tcp_so_fd = -1;  // Socket FD
  int tcp_io_fd = -1;  // IO FD returned by accept()

  int hu_tcp_recv (byte * buf, int len, int tmo) {
    //int ret = itcp_bulk_transfer (itcp_ep_in, buf, len, tmo);         // milli-second timeout
    if (tcp_io_fd < 0)                                                // If TCP IO not ready...
    return (-1);
	
	////logd("we need len %d, we got total_buffer: %d ",len,total_buffer);
	if (tmo==-4) {
		////logd("We only read the header 6 bytes nothing more");
	 //ret = recv (tcp_io_fd, buf, len,MSG_EOR);
	 // if (total_buffer>len+last_used) {
	 // memcpy(buf,my_buffer+last_used,len);
	 // last_used=last_used+6;
	 // // memmove(my_buffer,my_buffer+6,total_buffer-6);
	 // // total_buffer=total_buffer-6;
     // return(len); 
	 // }
	 // else {
	int	ret = recv (tcp_io_fd, buf,6,MSG_WAITALL);
	return(ret);
	 // }
		
	}
	
	else {
		
    long ms_tmo = ms_get ();
    if (tmo > 0)                                                        // If tmo valid...
     ms_tmo += tmo;                                                    // Timeout time = now + tmo
	else
      ms_tmo += 1000;                                                   // Else default 1 second for special case

    int avail = 0;
    while (tmo == -1 && avail < len ) {            // If tmo == -1 for special "get exactly this many bytes" function
      avail = recv (tcp_io_fd, buf, len, MSG_PEEK);                     // Peek to see how many bytes are available
      //logd ("hu_tcp_recv avail: %d  len: %d", avail, len);
      if (avail < len)                                                  // If we don't have the needed bytes yet...
        ms_sleep (1);//3);
    }
	if (tmo == -2)
	{
	avail = recv (tcp_io_fd, buf, len, MSG_WAITALL );   
	logv ("hu_tcp_recv avail: %d  len: %d", avail, len);
	return (avail);
	}

    if (tmo > 0) {
      sock_tmo_set (tcp_io_fd, tmo);
    }

    errno = 0;
    int ret = 0;

	ret = recv (tcp_io_fd, buf, len,MSG_EOR);
	
	//ret = recv (tcp_io_fd, buf, len, MSG_WAITALL );   
    return (ret);
	}
  }

  int hu_tcp_send (byte * buf, int len, int tmo) {
    //int ret = itcp_bulk_transfer (itcp_ep_out, buf, len, tmo);      // milli-second timeout
    if (tcp_io_fd < 0)
      return (-1);

	
      errno = 0;
      int ret = send (tcp_io_fd, buf, len, MSG_DONTWAIT);
	  //logd("Trying to write...   %d",ret);
      if (ret != len) {             // Write, if can't write full buffer...
        loge ("Error write  errno: %d (%s)", errno, strerror (errno));
       // ms_sleep (101);                                                 // Sleep 0.1 second to try to clear errors
		// int ret = write (tcp_io_fd, buf, len);
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

    //logd ("Done");

    return (0);
  }


  #define CS_FAM   AF_INET

  #define CS_SOCK_TYPE    SOCK_STREAM     
  #define   RES_DATA_MAX  65536
  //#define   RES_DATA_MAX  131072



 // struct sockaddr_in  cli_addr = {0}; - changed on 10.08.2016, this was used in version 1.02
  struct sockaddr_in  cli_addr;
  socklen_t cli_len = 0;

  int itcp_accept (int tmo) {

  
  

    if (tcp_so_fd < 0)
      return (-1);

    if (tcp_io_fd < 0)                                                  // If we don't have an IO socket yet...
      sock_tmo_set (tcp_so_fd, tmo);//3000);//tmo);                     // Set socket timeout for polling every tmo milliseconds

   // memset ((char *) & cli_addr, sizeof (cli_addr), 0);                - changed on 10.08.2016, this was used in version 1.02 // ?? Don't need this ?
    //cli_addr.sun_family = CS_FAM;                                     // ""
    cli_len = sizeof (cli_addr);

    errno = 0;
    int ret = 0;
	  	  
			cli_addr.sin_addr.s_addr  = myip_string;
	//}
      cli_addr.sin_family = AF_INET;
      cli_addr.sin_port = htons (5277);
      ////logd ("cli_len: %d  fam: %d  addr: 0x%x  port: %d",cli_len,cli_addr.sin_family, ntohl (cli_addr.sin_addr.s_addr), ntohs (cli_addr.sin_port));

      ret = connect (tcp_so_fd, (const struct sockaddr *) & cli_addr, cli_len);
      if (ret != 0) {
        loge ("Error connect errno: %d (%s) - ipaddress: %ul ", errno, strerror (errno), myip_string);
        return (-1);
      }
	  
		  
      tcp_io_fd = tcp_so_fd;
  //  }


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
    int ret = setsockopt (tcp_so_fd, SOL_TCP, TCP_NODELAY , & flag, sizeof (flag));  // Only need this for IO socket from accept() ??
    if (ret != 0)
      loge ("setsockopt TCP_NODELAY errno: %d (%s)", errno, strerror (errno));
    else
      logd ("setsockopt TCP_NODELAY Success");
  
//*/
    sock_reuse_set (tcp_so_fd);

    //if (tmo != 0)
    //  sock_tmo_set (tcp_so_fd, tmo);                                 // Might need consider using pooling again



    while (tcp_io_fd < 0) {                                             // While we don't have an IO socket file descriptor...
      itcp_accept (100);                                                // Try to get one with 100 ms timeout
    }
    logd ("itcp_accept done");

    return (0);
  }
 int hu_tcp_start_my_thread() {
		
	
    //logd("Before Thread\n");
    pthread_create(&tid, NULL, myThreadFun, (void *)&tcp_so_fd);
	//logd("After Thread\n");
	sleep(3);
		
	}
  int hu_tcp_stop () {
    itcp_state = hu_STATE_STOPPIN;
    //logd ("  SET: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));
    int ret = itcp_deinit ();
    itcp_state = hu_STATE_STOPPED;
    //logd ("  SET: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));
    return (ret);
  }


  int hu_tcp_start (byte ep_in_addr, byte ep_out_addr, long myip) {
	////logd ("Recived IP is: %s", myip);
	myip_string=myip;
	
    int ret = 0;

    if (itcp_state == hu_STATE_STARTED) {
      //logd ("CHECK: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));
      return (0);
    }

 

    itcp_state = hu_STATE_STARTIN;
    //logd ("  SET: itcp_state: %d (%s)", itcp_state, state_get (itcp_state));

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

