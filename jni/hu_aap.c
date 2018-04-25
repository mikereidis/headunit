
  // Android Auto Protocol Handler

  #define LOGTAG "hu_aap"
  #include "hu_uti.h"
  #include "hu_ssl.h"
  #include "hu_aap.h"

  int iaap_state = 0; // 0: Initial    1: Startin    2: Started    3: Stoppin    4: Stopped
  int use_audio=1;
  int hires=0;
  int first_run=1;
  char * new_sdbuf;
  int new_buf_length=0;
  
  byte bluetooth_mac [17] = {0};      //This needs to be re-enabled for built in devices.
 
  
  char * chan_get (int chan) {
    switch (chan) {
      case AA_CH_CTR: return ("CTR");
      case AA_CH_VID: return ("VID");
      case AA_CH_TOU: return ("TOU");
      case AA_CH_SEN: return ("SEN");
      case AA_CH_MIC: return ("MIC");
      case AA_CH_AUD: return ("AUD");
      case AA_CH_AU1: return ("AU1");
      case AA_CH_AU2: return ("AU2");
      case AA_CH_MED: return ("MED");
      case AA_CH_NAV: return ("NAV");
    }
    return ("UNK");
  }
#include "hu_tcp.h"
  int transport_type = 1; // 1=USB 2=WiFi 3=OLD USB
  int ihu_tra_recv  (byte * buf, int len, int tmo) {
    if (transport_type == 1)
	{
      if (tmo == -2)
		  tmo = 2000;
	  else if (tmo == -4)
		  tmo = 150;
	  return (hu_usb_recv  (buf, len, tmo));
	}
    else if (transport_type == 2)
      return (hu_tcp_recv  (buf, len, tmo));
    else if (transport_type == 3)
      return (old_hu_usb_recv(buf,len,tmo));
    else
      return (-1);
  }
  int ihu_tra_send  (byte * buf, int len, int tmo) {
    if (transport_type == 1)
      return (hu_usb_send  (buf, len, tmo));
    else if (transport_type == 2)
      return (hu_tcp_send  (buf, len, tmo));
    else if (transport_type == 3)
      return (old_hu_usb_send(buf,len,tmo));
    else
      return (-1);
  }
  int ihu_tra_stop  () {
    if (transport_type == 1 || transport_type==3)
      return (-1);
    else if (transport_type == 2)
      return (hu_tcp_stop  ());
    else
      return (-1);
  }


  int iaap_tra_recv_tmo = 150;//100;//1;//10;//100;//250;//100;//250;//100;//25; // 10 doesn't work ? 100 does
  int iaap_tra_send_tmo = 250;//2;//25;//250;//500;//100;//500;//250;

  int ihu_tra_start (byte ep_in_addr, byte ep_out_addr, byte con_mod_use, long myip_string, int transport_audio,int hr) {
	  logd("The audio transport should be now %d",transport_audio);
	  logd("This is the bew version with buffer / then decode...");
	  use_audio=transport_audio;
	  hires=hr;


    if (ep_in_addr == 255 && ep_out_addr == 255) {
      logd ("AA Wifi Direct");
      transport_type = 2;       // WiFi
      iaap_tra_recv_tmo = 150;
      iaap_tra_send_tmo = 250;
    }
 
    else if (con_mod_use == 255) {
      transport_type = 1;       // USB
      logd ("AA over USB");
      iaap_tra_recv_tmo = 150;//100;
      iaap_tra_send_tmo = 250;
    }
    else
     {
      transport_type = 3;       // USB
      logd ("AA over USB using C library.");
      iaap_tra_recv_tmo = 150;//100;
      iaap_tra_send_tmo = 250;
     }
    if (transport_type == 1)
      return (0);
    else if (transport_type == 2)
      return (hu_tcp_start  (ep_in_addr, ep_out_addr, myip_string));
    else if (transport_type == 3)
      return (hu_usb_start  (ep_in_addr, ep_out_addr));
	 else
      return (-1);
  }




  byte enc_buf [DEFBUF] = {0};                                          // Global encrypted transmit data buffer

  byte assy [65536 * 32] = {0};                                         // Global assembly buffer for video fragments: Up to 1 megabyte   ; 128K is fine for now at 800*640
  int assy_size = 0;                                                    // Current size
  int max_assy_size = 0;                                                // Max observed size needed:  151,000

  byte media_assy [65536] = {0};                                         // Global assembly buffer for video fragments: Up to 1 megabyte   ; 128K is fine for now at 800*640
  int media_assy_size = 0;                                                    // Current size
 

  
  int vid_rec_ena = 0;                                                // Video recording to file
  int vid_rec_fd  = -1;

  byte vid_ack [] = {0x80, 0x04, 0x08, -1, 0x10,  1};                    // Global Ack: 0, 1

 
  byte  rx_buf [DEFBUF] = {0};                                          // Global Transport Rx buf
  byte global_data[rxbuffsize] = {0};									//1Mb Rx Read Buffer
  int global_position = 0;											//Pointer of the Rx Buffer
  
  
  
                                           // Global decrypted receive buffer
 // #define dec_buf rx_buf                          // Use same buffer !!!



  int hu_aap_tra_set (int chan, int flags, int type, byte * buf, int len) {  // Convenience function sets up 6 byte Transport header: chan, flags, len, type

    buf [0] = (byte) chan;                                              // Encode channel and flags
    buf [1] = (byte) flags;
    buf [2] = (len -4) / 256;                                            // Encode length of following data:
    buf [3] = (len -4) % 256;
    if (type >= 0) {                                                    // If type not negative, which indicates encrypted type should not be touched...
      buf [4] = type / 256;
      buf [5] = type % 256;                                             // Encode msg_type
    }

    return (len);
  }

  int hu_aap_tra_recv (byte * buf, int len, int tmo) {
    int ret = 0;
    if (iaap_state != hu_STATE_STARTED && iaap_state != hu_STATE_STARTIN) {   // Need to recv when starting
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (-1);
    }
    ret = ihu_tra_recv (buf, len, tmo);
	 if (transport_type == 1 && ret<0)
	 { 
		hu_aap_stop (); 
	    return (ret);
	 }
  }

  int log_packet_info = 1;

  int hu_aap_tra_send (byte * buf, int len, int tmo) {                  // Send Transport data: chan,flags,len,type,...
       
	//hex_dump (" W/    hu_aap_enc_send: ", 256, buf, len);
	   // Need to send when starting
    if (iaap_state != hu_STATE_STARTED && iaap_state != hu_STATE_STARTIN) {
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (-1);
    }

    int ret = ihu_tra_send (buf, len, tmo);
    if (ret < 0 || ret != len) {
      loge ("Error ihu_tra_send() error so stop Transport & AAP  ret: %d  len: %d", ret, len);
      hu_aap_stop (); 
      return (-1);
    }  

    if (ena_log_verbo && ena_log_aap_send)
      logd ("OK ihu_tra_send() ret: %d  len: %d", ret, len);
    return (ret);
  }



  int hu_aap_enc_send (int chan, byte * buf, int len) {                 // Encrypt data and send: type,...
  
  // printing the byte buffer

  
    if (iaap_state != hu_STATE_STARTED) {
      logw ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      //logw ("chan: %d  len: %d  buf: %p", chan, len, buf);
      //hex_dump (" W/    hu_aap_enc_send: ", 16, buf, len);    // Byebye: hu_aap_enc_send:  00000000 00 0f 08 00
      return (-1);
    }
    int flags = 0x0b;                                                   // Flags = First + Last + Encrypted
    if (chan != AA_CH_CTR && buf [0] == 0) {                            // If not control channel and msg_type = 0 - 255 = control type message
      flags = 0x0f;                                                     // Set Control Flag (On non-control channels, indicates generic/"control type" messages
      //logd ("Setting control");
    }
    if (chan == AA_CH_MIC && buf [0] == 0 && buf [1] == 0) {            // If Mic PCM Data
      flags = 0x0b;                                                     // Flags = First + Last + Encrypted
    }
// hex_dump (" W/    unenc_send: ", 16, buf, len);
/*#ifndef NDEBUG
//    if (ena_log_verbo && ena_log_aap_send) {
   // if (log_packet_info) { // && ena_log_aap_send)
      char prefix [DEFBUF] = {0};
      snprintf (prefix, sizeof (prefix), "S %d %s %1.1x", chan, chan_get (chan), flags);  // "S 1 VID B"
      int rmv = hu_aad_dmp (prefix, "HU", chan, flags, buf, len);
   // }
#endif*/
    int bytes_written = SSL_write (hu_ssl_ssl, buf, len);               // Write plaintext to SSL
    if (bytes_written <= 0) {
      loge ("SSL_write() bytes_written: %d", bytes_written);
      hu_ssl_ret_log (bytes_written);
      hu_ssl_inf_log ();
      hu_aap_stop ();
      return (-1);
    }
    if (bytes_written != len)
      loge ("SSL_write() len: %d  bytes_written: %d  chan: %d %s", len, bytes_written, chan, chan_get (chan));
    else if (ena_log_verbo && ena_log_aap_send)
      logd ("SSL_write() len: %d  bytes_written: %d  chan: %d %s", len, bytes_written, chan, chan_get (chan));

    int bytes_read = BIO_read (hu_ssl_wm_bio, & enc_buf [4], sizeof (enc_buf) - 4); // Read encrypted from SSL BIO to enc_buf + 
    if (bytes_read <= 0) {
      loge ("BIO_read() bytes_read: %d", bytes_read);
      hu_aap_stop ();
      return (-1);
    }
    if (ena_log_verbo && ena_log_aap_send)
      logd ("BIO_read() bytes_read: %d", bytes_read);

    hu_aap_tra_set (chan, flags, -1, enc_buf, bytes_read + 4);          // -1 for type so encrypted type position is not overwritten !!
    hu_aap_tra_send (enc_buf, bytes_read + 4, iaap_tra_send_tmo);           // Send encrypted data to AA Server
    //hex_dump ("SSL DATA: ", 256, enc_buf, bytes_read+4);
    return (0);
  }



byte sd_buf2 []={0x00, 0x06, 
//Touch+input CHANNEL
0x0A, 34,   0x08, 0x01, 0x22, 30, 0x0A, 20, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x12, 0x06, 0x08, 0x80, 0x0f, 0x10, 0xb8, 0x08,
//0x0A, 0x0F, 0x08, 0x01, 0x22, 0x0B, 0x0A, 0x01, 0x54, 0x12, 0x06, 0x08, 0xA0, 0x06, 0x10, 0xE0, 0x03, 
//0x0a, 0x12, 0x08, 0x01, 0x22, 0x0e, 0x0a, 0x0c, 0x01, 0x02, 0x04, 0x13, 0x14, 0x15, 0x16, 0x17, 0x54, 0x80, 0x80, 0x04,

// SENSOR CHANNEL
0x0A, 16, 0x08, 0x02, 0x12, 12, 0x0A, 0x02, 0x08, 0x01, 0x0A, 0x02, 0x08, 0x0A, 0x0A, 0x02, 0x08, 13, 
//                       18,  12,     10,    2,    8,    1,  10,   2,     8,    10, 10,    2,    8,    13
//General Notification channel currently not working I might be doing something wrong...
//0x0a, 4,     0x08, 13,0x6A,0,


// Video CHANNEL
0x0A, 0x15, 0x08, 0x03, 0x1A, 0x11, 0x08, 0x03, 0x22, 0x0D, 0x08, 0x01, 0x10, 0x02, 0x18, 0x00, 0x20, 0x00, 0x28, -96, 0x01, 0x30, 0x00, 
// MIC CHANNEL
0x0A, 4+4+7,0x08, AA_CH_MIC, 0x2A, 4+7, 0x08, 1,    0x12, 7,    0x08, -128, 0x7d, 0x10, 0x10, 0x18, 1,
//0x0A, 29,   0x08,     8,      0x32, 25, 0x0A, 17, '0','0',':','0','0',':','0','0',':','0','0',':','0','0',':','0','0',0x12,1,4,0x1A,1,1,
//MediaPlaybackService?????
0x0a, 6, 0x08, 9,0x4A,2,0x08,01,
//Navigation Status Service (below code looks to be working, we are receiveing 2 messags, 8004 and 8005, 8005 is probably next turn distance/time and 8004 is the graphical part, inc street name...
0x0a, 17, 0x08,10,0x42,0x0d,0x08,-84,0x02,0x10,0x01,0x1a,0x06,0x08,0x40,0x10,0x40,0x18,0x10,
//0x0a, 6, 0x08,10,0x42,2,0x08,01,
//0x0a, 6, 0x08, 9,0x7A,2,0x08,01,


// CAR DEFINITION
						
                        0x12, 0, //'H','e','a','d','u','n','i','t',//1, 'A', // Car Manuf          Part of "remembered car"
                        0x1A, 8, 'R','e','l','o','a','d','e','d',//1, 'B', // Car Model
                        0x22, 4, '2', '0', '1', '7',//1, 'C', // Car Year           Part of "remembered car"
                        0x2A, 4, '0', '0', '0', '1',//1, 'D', // Car Serial     Not Part of "remembered car" ??     (vehicleId=null)
                        0x30, 0,//0,      // driverPosition
						
                        0x3A, 8, 'H','e','a','d','u','n','i','t',//1, 'E', // HU  Make / Manuf
                        0x42, 4, 'H', 'U', '1', '5',//1, 'F', // HU  Model
                        0x4A, 4, 'S', 'W', 'B', '1',//1, 'G', // HU  SoftwareBuild
                        0x52, 4, 'S', 'W', 'V', '1',//1, 'H', // HU  SoftwareVersion
                        0x58, 1,//1,//1,//0,//1,       // ? bool (or int )    canPlayNativeMediaDuringVr
                        0x60, 0,//1,//0,//0,//1        // mHideProjectedClock     1 = True = Hide	*/	
						
//THE LAST 3 CHANNEL KEEP THEM HERE SO WE CAN EXCLUDE THEM IF NEED BE.
//WifiProjectionService (Phone BSSID????)
//0x0a, 4+16,     0x08, 10,0x72,16,0x0A,'H','e','a','d','u','n','i','t',' ','R','e','l','o','a','d',

0x0A, 4+6+7, 0x08, AA_CH_AU2, 0x1A, 6+7, 0x08, 1,  0x10, 2, 0x1A, 7, 0x08, -128, 0x7d,         0x10, 0x10,   0x18, 1,
0x0A, 4+6+7, 0x08, AA_CH_AU1, 0x1A, 6+7, 0x08, 1,  0x10, 1, 0x1A, 7, 0x08, -128, 0x7d,         0x10, 0x10,   0x18, 1,
0x0A, 4+6+8, 0x08, AA_CH_AUD, 0x1A, 6+8, 0x08, 1,  0x10, 3, 0x1A, 8, 0x08, -128,   -9, 0x02,   0x10, 0x10,   0x18, 02,

};

													
  int aa_pro_ctr_a00 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a01 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a02 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a03 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a04 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }


  int aa_pro_ctr_a05 (int chan, byte * buf, int len) {                  // Service Discovery Request
    if (len < 4 || buf [2] != 0x0a)
      loge ("Service Discovery Request: %x", buf [2]);
    else
      logd ("Service Discovery Request");                               // S 0 CTR b src: HU  lft:   113  msg_type:     6 Service Discovery Response    S 0 CTR b 00000000 0a 08 08 01 12 04 0a 02 08 0b 0a 13 08 02 1a 0f

	logd("The new buf size should be: %d",new_buf_length);
    // int sd_buf_len = sizeof (sd_buf);
    //int sd_buf_len = sizeof (sd_buf2);
    int sd_buf_len = new_buf_length;
	byte updated_sdbuf [new_buf_length];
	int idx=0;
	for (idx=0;idx<new_buf_length;idx++)
	{
		updated_sdbuf[idx]=new_sdbuf[idx];
	}
	//strncpy(updated_sdbuf,new_sdbuf,new_buf_length);
	hex_dump ("New SD BUF: ", new_buf_length, updated_sdbuf, new_buf_length);
	hex_dump ("SD BUF2: ",  sizeof(sd_buf2), sd_buf2, sizeof(sd_buf2));
   logd("hu_app_start, use_audio value is reported as %d!",use_audio);
   if (use_audio<1)
   {
	   logd("hu_app_start, removing audio headers!");
     sd_buf_len -= 39;   //  Last 2 channels (Audio) counting 39 bytes. Remove audio outputs from service discovery response buf
   }
   if (hires==1)
   {  
   updated_sdbuf[67]=2; //Change  resolution mode to 1280x720p
   updated_sdbuf[75]=-16;  //Need to increase DPI to 240
   }
  // memcpy(& sd_buf2[97],bluetooth_mac,17);
//	hex_dump ("BluetoothMac: ", 32, bluetooth_mac, 17);
   //hex_dump (" W/    hu_aap_enc_send: ", 256, sd_buf2, sd_buf_len);
   
    return (hu_aap_enc_send (chan, updated_sdbuf, sd_buf_len));                // Send Service Discovery Response from sd_buf
    //return (hu_aap_enc_send (chan, sd_buf2, sd_buf_len));                // Send Service Discovery Response from sd_buf
  }
  int aa_pro_ctr_a06 (int chan, byte * buf, int len) {                  // Service Discovery Response
    loge ("!!!!!!!!");
    return (-1);
  }
  /*int aa_pro_ctr_a07 (int chan, byte * buf, int len) {                // Channel Open Request (never for control channel)
    loge ("!!!!!!!!");
    return (-1);
  }*/
  int aa_pro_ctr_a08 (int chan, byte * buf, int len) {                  // Channel Open Response (never from AA)
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a09 (int chan, byte * buf, int len) {                  // ?
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a0a (int chan, byte * buf, int len) {                  // ?
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a0b (int chan, byte * buf, int len) {                  // Ping Request
    if (len != 4 || buf [2] != 0x08)
      loge ("Ping Request");
    else
      logd ("Ping Request: %d", buf[3]);
    buf [0] = 0;                                                        // Use request buffer for response
    buf [1] = 12;                                                       // Channel Open Response
    int ret = hu_aap_enc_send (chan, buf, len);                         // Send Channel Open Response
    return (ret);
  }
  int aa_pro_ctr_a0c (int chan, byte * buf, int len) {                  // Ping Response (never unless we send Ping Request)
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a0d (int chan, byte * buf, int len) {                  // Navigation Focus Request
    if (len != 4 || buf [2] != 0x08)
      loge ("Navigation Focus Request");
    else
      logd ("Navigation Focus Request: %d", buf [3]);
    buf [0] = 0;                                                        // Use request buffer for response
    buf [1] = 14;                                                       // Navigation Focus Notification
    buf [2] = 0x08;
    buf [3] = 2;                                                        // Gained / Gained Transient ?
    int ret = hu_aap_enc_send (chan, buf, 4);//len);                         // Send Navigation Focus Notification
    return (0);
  }
  int aa_pro_ctr_a0e (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a0f (int chan, byte * buf, int len) {                  // Byebye Request
    if (len != 4 || buf [2] != 0x08)
      loge ("Byebye Request");
    else if (buf [3] == 1)
      logd ("Byebye Request reason: 1 AA Exit Car Mode");
    else if (buf [3] == 2)
      loge ("Byebye Request reason: 2 ?");
    else
      loge ("Byebye Request reason: %d", buf [3]);
    //byte bye_rsp [] = {0, 16, 8, 0};                                  // Byebye rsp: Status 0 = OK
    //int ret = hu_aap_enc_send (chan, bye_rsp, sizeof (bye_rsp));      // Send Byebye Response
    buf [0] = 0;                                                        // Use request buffer for response
    buf [1] = 16;                                                       // Byebye Response
    buf [2] = 0x08;
    buf [3] = 0;                                                        // Status 0 = OK
    int ret = hu_aap_enc_send (chan, buf, 4);                           // Send Byebye Response
    ms_sleep (100);                                                     // Wait a bit for response
    //terminate = 1;

    hu_aap_stop ();

    return (-1);
  }
  int aa_pro_ctr_a10 (int chan, byte * buf, int len) {                  // Byebye Response
/*    if (len != 4 || buf [2] != 0x08)
      loge ("Byebye Response");
    else if (buf [3] == 1)
      loge ("Byebye Response: 1 ?");
    else if (buf [3] == 2)
      loge ("Byebye Response: 2 ?");
    else
      loge ("Byebye Response: %d", buf [3]);*/
    if (len != 2)
      loge ("Byebye Response");
    else
      logd ("Byebye Response");                                         // R 0 CTR b src: AA  lft:     0  msg_type:    16 Byebye Response
    return (-1);
  }

  int aa_pro_ctr_a11 (int chan, byte * buf, int len) {                  // sr:  00000000 00 11 08 01      Microphone voice search usage     sr:  00000000 00 11 08 02
    if (len != 4 || buf [2] != 0x08)
      loge ("Voice Session Notification");
    else if (buf [3] == 1)
      logd ("Voice Session Notification: 1 START");
    else if (buf [3] == 2)
      logd ("Voice Session Notification: 2 STOP");
    else
      loge ("Voice Session Notification: %d", buf [3]);
    return (0);
  }
  int aa_pro_ctr_a12 (int chan, byte * buf, int len) {                  // Audio Focus Request
    if (len != 4 || buf [2] != 0x08)
      loge ("Audio Focus Request");
    else if (buf [3] == 1)
      logd ("Audio Focus Request: 1 AUDIO_FOCUS_GAIN ?");
    else if (buf [3] == 2)
      logd ("Audio Focus Request: 2 AUDIO_FOCUS_GAIN_TRANSIENT");
    else if (buf [3] == 3)
      logd ("Audio Focus Request: 3 gain/release ?");
    else if (buf [3] == 4)
      logd ("Audio Focus Request: 4 AUDIO_FOCUS_RELEASE");
    else
      loge ("Audio Focus Request: %d", buf [3]);
    buf [0] = 0;                                                        // Use request buffer for response
    buf [1] = 19;                                                       // Audio Focus Response
    buf [2] = 0x08;
      // buf[3]: See senderprotocol/q.java:
    // 1: AUDIO_FOCUS_STATE_GAIN
    // 2: AUDIO_FOCUS_STATE_GAIN_TRANSIENT
    // 3: AUDIO_FOCUS_STATE_LOSS
    // 4: AUDIO_FOCUS_STATE_LOSS_TRANSIENT_CAN_DUCK
    // 5: AUDIO_FOCUS_STATE_LOSS_TRANSIENT
    // 6: AUDIO_FOCUS_STATE_GAIN_MEDIA_ONLY
    // 7: AUDIO_FOCUS_STATE_GAIN_TRANSIENT_GUIDANCE_ONLY
    if (buf [3] == 4)                                                   // If AUDIO_FOCUS_RELEASE...
      buf [3] = 3;                                                      // Send AUDIO_FOCUS_STATE_LOSS
    else if (buf [3] == 2)                                              // If AUDIO_FOCUS_GAIN_TRANSIENT...
      buf [3] = 1;//2;                                                      // Send AUDIO_FOCUS_STATE_GAIN_TRANSIENT
    else
      buf [3] = 1;                                                      // Send AUDIO_FOCUS_STATE_GAIN
    buf [4] = 0x10;
    buf [5] = 0;                                                      // unsolicited:   0 = false   1 = true
    int ret = hu_aap_enc_send (chan, buf, 4);//6);                      // Send Audio Focus Response
    return (0);
  }
  int aa_pro_ctr_a13 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a14 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a15 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a16 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a17 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a18 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a19 (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a1a (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a1b (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a1c (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a1d (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a1e (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }
  int aa_pro_ctr_a1f (int chan, byte * buf, int len) {
    loge ("!!!!!!!!");
    return (-1);
  }

  int aa_pro_all_a07 (int chan, byte * buf, int len) {                  // Channel Open Request
    if (len != 6 || buf [2] != 0x08 || buf [4] != 0x10)
      loge ("Channel Open Request");
    else
      logd ("Channel Open Request: %d  chan: %d", buf [3], buf [5]);    // R 1 SEN f 00000000 08 00 10 01   R 2 VID f 00000000 08 00 10 02   R 3 TOU f 00000000 08 00 10 03   R 4 AUD f 00000000 08 00 10 04   R 5 MIC f 00000000 08 00 10 05
    byte rsp [] = {0, 8, 8, 0};                                         // Status 0 = OK
    int ret = hu_aap_enc_send (chan, rsp, sizeof (rsp));                // Send Channel Open Response

    if (ret == 0 && chan == AA_CH_MIC) {
      //byte rspm [] = {0, 17, 0x08, 1, 0x10, 1};                         // 1, 1     Voice Session not focusState=1=AUDIO_FOCUS_STATE_GAIN unsolicited=true    050b0000001108011001
      //ret = hu_aap_enc_send (chan, rspm, sizeof (rspm));                // Send AudioFocus Notification
      //ms_sleep (200);
      //logd ("Channel Open Request AFTER ms_sleep (500)");
    }

    if (ret)                                                            // If error, done with error
      return (ret);

    if (chan == AA_CH_SEN) {                                            // If Sensor channel...
      ms_sleep (100);//20);
	  //00012 00 0b 0b 4b ea 6  
	 // byte rspds [] = {0x80, 0x03, 0x72, 2, 8, 0};                      // Driving Status = 0 = Parked (1 = Moving)
	
     // hu_aap_enc_send (chan, rspds, sizeof (rspds)); 
      // byte rspds3 [] = {0x80, 0x03, 0x6a, 2, 8, 31};                      // Driving Status = 0 = Parked (1 = Moving)
      // hu_aap_enc_send (chan, rspds3, sizeof (rspds3));           // Send Sensor Notification
	  // ms_sleep (2);//20);
	  // byte rspds2 [] = {0x80, 0x03, 0x3a, 2, 8, 1};     This sensor looks to be the passanger sensor (1 has passanger, 0 no passanger) or am I wrong?
	  byte rspds2 [] = {0x80, 0x03, 0x6a, 2, 8, 0};     
      return (hu_aap_enc_send (chan, rspds2, sizeof (rspds2)));           // Send Sensor Notification
	   
	 /* byte rspds [] = {0x80, 0x03, 0x0a, 0x0a, 0x80, 0x80, 0xbc, 0x8a, 0xc9, 0xd2, 0x13, 0x00, 0x30, 0x01};    
	   return (hu_aap_enc_send (chan, rspds, sizeof (rspds)));    
	
	   ms_sleep (2);
	  byte rspds3 [] = {0x80, 0x03, 0x08, 0x00};    
	   return (hu_aap_enc_send (chan, rspds3, sizeof (rspds3)));    
	 
  	 ms_sleep (2);
	   return (hu_aap_enc_send (chan, rspds, sizeof (rspds)));   
  	 ms_sleep (2);
	   return (hu_aap_enc_send (chan, rspds, sizeof (rspds)));   
  	 ms_sleep (2);
	 byte rspds4 [] = {0x80, 0x03, 0x08, 2, 10, 2, 0x18, 0x00}; 
	   return (hu_aap_enc_send (chan, rspds4, sizeof (rspds4)));  */
    }
    return (ret);
  }

//aa_start 01
  int aa_pro_snk_b00 (int chan, byte * buf, int len) {                  // Media Sink Setup Request
    if (len != 4 || buf [2] != 0x08)
      loge ("Media Sink Setup Request");
    else
      logd ("Media Sink Setup Request: %d", buf [3]);                   // R 2 VID b 00000000 08 03       R 4 AUD b 00000000 08 01

    #define MAX_UNACK 1 //8 //3 //8 //1
    byte rsp [] = {0x80, 0x03, 0x08, 2, 0x10, MAX_UNACK, 0x18, 0};//0x1a, 4, 0x08, 1, 0x10, 2};      // 1/2, MaxUnack, int[] 1        2, 0x08, 1};//

    int ret = hu_aap_enc_send (chan, rsp, sizeof (rsp));               // Respond with Config Response
    if (ret)
      return (ret);

    if (chan == AA_CH_AUD || chan == AA_CH_AU1 || chan == AA_CH_AU2) {
      return (ret); // Rely on solicited focus request
      //ms_sleep (20);                                                      // Else if success and channel = audio...
      //byte rspa [] = {0, 19, 0x08, 1, 0x10, 1};                      // 1, 1     AudioFocus gained focusState=1=AUDIO_FOCUS_STATE_GAIN unsolicited=true
      //return (hu_aap_enc_send (chan, rspa, sizeof (rspa)));              // Respond with AudioFocus gained
    }
//*
    if (chan == AA_CH_VID) {
//      ms_sleep (200);//(2);//200);//20);                                                      // Else if success and channel = video...
      byte rsp2 [] = {0x80, 0x08, 0x08, 1, 0x10, 1};                      // 1, 1     VideoFocus gained focusState=1 unsolicited=true     010b0000800808011001
      return (hu_aap_enc_send (chan, rsp2, sizeof (rsp2)));              // Respond with VideoFocus gained
    }
//*/
    return (ret);
  }

  byte ack_val_aud = 0;
  byte ack_val_au1 = 0;
  byte ack_val_au2 = 0;
  int out_state_aud = -1;
  int out_state_au1 = -1;
  int out_state_au2 = -1;
  int aa_pro_aud_b01 (int chan, byte * buf, int len) {                  // Audio Sink Start Request...     First/Second R 4 AUD b 00000000 08 00/01 10 00
    if (len != 6 || buf [2] != 0x08 || buf [4] != 0x10)
      loge ("Audio Sink Start Request");
    else
      logd ("Audio Sink Start Request: %d %d", buf [3], buf[5]);
    if (chan == AA_CH_AUD)
      ack_val_aud = buf [3];                                            // Save value for audio acks
    else if (chan == AA_CH_AU1)
      ack_val_au1 = buf [3];                                            // Save value for audio1 acks
    else if (chan == AA_CH_AU2)
      ack_val_au2 = buf [3];                                            // Save value for audio2 acks
    return (0);
  }
  int hu_aap_out_get (int chan) {
    int state = 0;
    if (chan == AA_CH_AUD) {
      state = out_state_aud;                                            // Get current audio output state change
      out_state_aud = -1;                                               // Reset audio output state change indication
    }
    else if (chan == AA_CH_AU1) {
      state = out_state_au1;                                            // Get current audio output state change
      out_state_au1 = -1;                                               // Reset audio output state change indication
    }
    else if (chan == AA_CH_AU2) {
      state = out_state_au2;                                            // Get current audio output state change
      out_state_au2 = -1;                                               // Reset audio output state change indication
    }
    return (state);                                                     // Return what the new state was before reset
  }

  int aa_pro_aud_b02 (int chan, byte * buf, int len) {                  // 08-22 20:03:09.075 D/ .... hex_dump(30767): S 4 AUD b 00000000 08 00 10 01   Only at stop ??
    if (len != 2)//4 || buf [2] != 0x08)
      loge ("Audio Sink Stop Request");
    else
      logd ("Audio Sink Stop Request");//: %d", buf [3]);
    if (chan == AA_CH_AUD)
      out_state_aud = 1;                                                      // Signal Audio stop
    else if (chan == AA_CH_AU1)
      out_state_au1 = 1;                                                      // Signal Audio1 stop
    else if (chan == AA_CH_AU2)
      out_state_au2 = 1;                                                      // Signal Audio2 stop
    //hex_dump ("AOSSR: ", 16, buf, len);
    return (0);
  }

 int aa_pro_vid_b02 (int chan, byte * buf, int len) { 
 //toggle_get_video(0);  //Stop fetching the video
 return(0);
 }
//aa_start 03
  int aa_pro_vid_b01 (int chan, byte * buf, int len) {                  // Media Video Start Request...
  hex_dump("Video Start Req: ",len, buf, len);
    if (len != 6 || buf [2] != 0x08 || buf [4] != 0x10)
      loge ("Media Video Start Request");
    else
      logd ("Media Video Start Request: %d %d", buf [3], buf [5]);
    int ret = 0;
	vid_ack[3]=vid_ack[3]+1;
	
	//toggle_get_video(1);
	  first_run=0;
	  
      return (ret);
  }
  int aa_pro_vid_b07 (int chan, byte * buf, int len) {                  // Media Video ? Request...
    if (len != 4 || buf [2] != 0x10)
      loge ("Media Video ? Request");
    else
      logd ("Media Video ? Request: %d", buf [3]);
    int ret = 0;
    return (ret);
  }

  int aa_pro_sen_b01 (int chan, byte * buf, int len) {                  // Sensor Start Request...
    if (len != 6 || buf [2] != 0x08 || buf [4] != 0x10)
	{loge ("Sensor Start Request");
	//hex_dump ("HEX_DUMP: ", 64,buf , len);
	}
    else
      logd ("Sensor Start Request sensor: %d   period: %d", buf [3], buf [5]);  // R 1 SEN b 00000000 08 01 10 00     Sen: 1, 10, 3, 8, 7
     // hex_dump("Sensor",64,buf,len);                                                                          // Yes: SENSOR_TYPE_COMPASS/LOCATION/RPM/DIAGNOSTICS/GEAR      No: SENSOR_TYPE_DRIVING_STATUS
    byte rsp [] = {0x80, 0x02, 0x08, 0};
    int ret = hu_aap_enc_send (chan, rsp, sizeof (rsp));                  // Send Sensor Start Response
//    if (ret)                                                            // If error, done with error
      return (ret);

    //ms_sleep (20);                                                      // Else if success...
    //byte rspds [] = {0x80, 0x03, 0x6a, 2, 8, 0};                      // Driving Status = 0 = Parked (1 = Moving)
    //return (hu_aap_enc_send (chan, rspds, sizeof (rspds)));           // Send Sensor Notification
  }

//aa_start 02
  int aa_pro_tou_b02 (int chan, byte * buf, int len) {                  // TouchScreen/Input Start Request...    Or "send setup, ch:X" for channel X
    if (len < 2 || len > 256)
      loge ("Touch/Input/Audio Start/Stop Request");
    else
      logd ("Touch/Input/Audio Start/Stop Request");                    // R 3 TOU b src: AA  lft:     0  msg_type: 32770 Touch/Input/Audio Start/Stop Request
                                                                        // R 3 TOU b src: AA  lft:    18  msg_type: 32770 Touch/Input/Audio Start/Stop Request
                                                                        // R 3 TOU b 00000000 0a 10 03 54 55 56 57 58 7e 7f d1 01 81 80 04 84     R 3 TOU b     0010 80 04 (Echo Key Array discovered)
    byte rsp [] = {0x80, 0x03, 0x08, 0};
    int ret = hu_aap_enc_send (chan, rsp, sizeof (rsp));                // Respond with Key Binding/Audio Response = OK
    return (ret);
  }

  int mic_change_status = 0;
  int hu_aap_mic_get () {
	  return (mic_change_status);
	  /*
    int ret_status = mic_change_status;                                 // Get current mic change status
    if (mic_change_status == 2 || mic_change_status == 1) {             // If start or stop...
      mic_change_status = 0;                                            // Reset mic change status to "No Change"
    }
    return (ret_status);                                                // Return original mic change status
	*/
  }
  int aa_pro_mic_b01 (int chan, byte * buf, int len) {                  // Media Mic Start Request...
    if (len != 4 || buf [2] != 0x08)
      loge ("Media Mic Start Request ????");
    else
      loge ("Media Mic Start Request ????: %d", buf [3]);
    return (0);
  }
  int aa_pro_mic_b04 (int chan, byte * buf, int len) {
   // hex_dump ("MIC ACK: ", 16, buf, len);
    return (0);
  }
  int aa_pro_mic_b05 (int chan, byte * buf, int len) {
    if (len == 4 && buf [2] == 0x08 && buf [3] == 0) {
      logd ("Mic Start/Stop Request: 0 STOP");
      mic_change_status = 1;                                            // Stop Mic
    }
    else if (len != 10 || buf [2] != 0x08 || buf [3] != 1 || buf [4] != 0x10 || buf [6] != 0x18 || buf [8] != 0x20) {
      loge ("Mic Start/Stop Request");
    }
    else {
      logd ("Mic Start/Stop Request: 1 START %d %d %d", buf [5], buf [7], buf [9]);
      mic_change_status = 2;                                            // Start Mic
    }
    return (0);
  }

  int aa_pro_gen_not (int chan, byte * buf, int len) {                  //General message notification
  // hex_dump ("HEX_DUMP: ", 64,buf , len);
  // byte rspds [] = {0x80, 0x03, 0x0a, 0x02, 0x30, 0x31, 0x10, 0x01};                
  // hu_aap_enc_send (chan, rspds, sizeof (rspds));  
  return(0);
  }
  




  typedef int (* aa_type_ptr_t) (int chan, byte * buf, int len);

  aa_type_ptr_t aa_type_array [15] [3] [32] = {              // 0 - 31, 32768-32799, 65504-65535
                                                                        // Sync with hu_tra.java, hu_aap.h and hu_aap.c:aa_type_array[]
// Channel 0 Ctr Control:
    aa_pro_ctr_a00, aa_pro_ctr_a01, aa_pro_ctr_a02, aa_pro_ctr_a03, aa_pro_ctr_a04, aa_pro_ctr_a05, aa_pro_ctr_a06, aa_pro_all_a07, aa_pro_ctr_a08, aa_pro_ctr_a09, aa_pro_ctr_a0a, aa_pro_ctr_a0b, aa_pro_ctr_a0c, aa_pro_ctr_a0d, aa_pro_ctr_a0e, aa_pro_ctr_a0f,
    aa_pro_ctr_a10, aa_pro_ctr_a11, aa_pro_ctr_a12, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,   //    a10, a11, a12, aa_pro_ctr_a13, aa_pro_ctr_a14, aa_pro_ctr_a15, aa_pro_ctr_a16, aa_pro_ctr_a17, aa_pro_ctr_a18, aa_pro_ctr_a19, aa_pro_ctr_a1a, aa_pro_ctr_a1b, aa_pro_ctr_a1c, aa_pro_ctr_a1d, aa_pro_ctr_a1e, aa_pro_ctr_a1f,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

// Channel 3 Tou TouchScreen:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, aa_pro_tou_b02, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
	
// Channel 1 Sen Sensor:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, aa_pro_sen_b01, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

// Channel 2 Vid Video:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    aa_pro_snk_b00, aa_pro_vid_b01, aa_pro_vid_b02, NULL, NULL, NULL, NULL, aa_pro_vid_b07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,



// Channel 4 Output Audio:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    aa_pro_snk_b00, aa_pro_aud_b01, aa_pro_aud_b02, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

// Channel 5 Output Audio1:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    aa_pro_snk_b00, aa_pro_aud_b01, aa_pro_aud_b02, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

// Channel 6 Output Audio2:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    aa_pro_snk_b00, aa_pro_aud_b01, aa_pro_aud_b02, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

// Channel 7 Mic Audio:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, aa_pro_mic_b01, NULL, NULL, aa_pro_mic_b04, aa_pro_mic_b05, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

	
// Channel 8 BluetoothAdapter:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, aa_pro_sen_b01, NULL, NULL, aa_pro_mic_b04, aa_pro_mic_b05, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
	
// Channel 9 media_playback_service:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
	
// Channel 10 Navigation Status Service:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, aa_pro_ctr_a0d, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
	
// Channel 11 phone_status_service:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, aa_pro_sen_b01, NULL, NULL, aa_pro_mic_b04, aa_pro_mic_b05, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
	
// Channel 12 media_browser_service:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, aa_pro_sen_b01, NULL, NULL, aa_pro_mic_b04, aa_pro_mic_b05, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
	
// Channel 13 vendor_extension_service:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, aa_pro_gen_not, NULL, NULL, aa_pro_mic_b04, aa_pro_mic_b05, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,	
	
// Channel 14 generic_notification_service:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, aa_pro_sen_b01, NULL, NULL, aa_pro_mic_b04, aa_pro_mic_b05, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
	
  };

	
  
  void iaap_media_decode (int chan, byte * buf, int len) {


    byte * q_buf = med_write_tail_buf_get (len+1);                        // Get queue buffer tail to write to     !!! Need to lock until buffer written to !!!!
    if (ena_log_verbo)
      logd ("media q_buf: %p  buf: %p  len: %d", q_buf, buf, len);
    if (q_buf == NULL) {
      loge ("Error video no q_buf: %p  buf: %p  len: %d", q_buf, buf, len);
      //return;                                                         // Continue in order to write to record file
    }
    else
	{
		q_buf[0]=chan;
        memcpy (&q_buf[1], buf, len);     // Copy video to queue buffer
                                         
	  
	}

	return;
  }  
  
  void iaap_video_decode (byte * buf, int len) {

  
 
    byte * q_buf = vid_write_tail_buf_get (len);                         // Get queue buffer tail to write to     !!! Need to lock until buffer written to !!!!
    if (ena_log_verbo)
      logd ("video q_buf: %p  buf: %p  len: %d", q_buf, buf, len);
    if (q_buf == NULL) {
      loge ("Error video no q_buf: %p  buf: %p  len: %d", q_buf, buf, len);
	  loge ("Going to reset the video buffers and start fresh...");
	  reset_vid_buf();
		 byte data [] = {   0x80, 0x08, 0x08, 0x02, 0x10, 0x01};
         hu_aap_enc_send (3, data, 6);
		 ms_sleep(100);
		 data[3]=1;
		 hu_aap_enc_send (3, data, 6);
	    
      //return;                                                         // Continue in order to write to record file
    }
    else
	{
	  //q_buf[0]=AA_CH_VID;
      memcpy (q_buf, buf, len);                                         // Copy video to queue buffer
	}

   
      return;
	  

  }

/* 8,192 bytes per packet at stereo 48K 16 bit = 42.667 ms per packet                            Timestamp = uptime in microseconds:
ms: 337, 314                                                                                  0x71fd616538  0x71fd620560   0x71fd62a970 (489,582,406,000)
                                                                                           diff:  0xA028 (41000)    0xA410  (42000)
07-01 18:54:11.067 W/                        hex_dump(28628): AUDIO:  00000000 00 00 00 00 00 71 fd 61 65 38 00 00 00 00 00 00 
07-01 18:54:11.404 W/                        hex_dump(28628): AUDIO:  00000000 00 00 00 00 00 71 fd 62 05 60 00 00 00 00 00 00 
07-01 18:54:11.718 W/                        hex_dump(28628): AUDIO:  00000000 00 00 00 00 00 71 fd 62 a9 70 00 00 00 00 00 00 

*/

  int aud_rec_ena = 0;                                                // Audio recording to file
  int aud_rec_fd  = -1;

  void iaap_audio_decode (int chan, byte * buf, int len) {

    //hu_uti.c:  #define aud_buf_BUFS_SIZE    65536 * 4      // Up to 256 Kbytes
	#define aud_buf_BUFS_SIZE    65536 * 4      // Up to 256 Kbytes
    if (len > aud_buf_BUFS_SIZE) {
      loge ("Error audio len: %d  aud_buf_BUFS_SIZE: %d", len, aud_buf_BUFS_SIZE);
      len = aud_buf_BUFS_SIZE;
    }
	
    byte * q_buf = aud_write_tail_buf_get (len+1);                         // Get queue buffer tail to write to     !!! Need to lock until buffer written to !!!!
    if (ena_log_verbo)
      logd ("audio q_buf: %p  buf: %p  len: %d", q_buf, buf, len);
    if (q_buf == NULL) {
      loge ("Error audio no q_buf: %p  buf: %p  len: %d", q_buf, buf, len);
      //return;                                                         // Continue in order to write to record file
    }
    else {
		q_buf[0]=chan;
        memcpy (&q_buf[1], buf, len);                                         // Copy audio to queue buffer
	}
	return;
		//memcpy(&global_audio_data[global_audio_position],buf,len);
		//global_audio_position=global_audio_position+len;
		
      /*if (0){//chan == AA_CH_AU1 || chan == AA_CH_AU2) {
        len *= 6;                                                       // 48k stereo takes 6 times the space
        if (len > aud_buf_BUFS_SIZE) {
          loge ("Error * 6  audio len: %d  aud_buf_BUFS_SIZE: %d", len, aud_buf_BUFS_SIZE);
          len = aud_buf_BUFS_SIZE;
        }
        int idx = 0;
        int idxi = 0;
        for (idx = 0; idx < len ; idx += 12) {                          // Convert 16K mono to 48k stereo equivalent; interpolation would be better
          q_buf [idx + 0] = buf [idxi + 0];
          q_buf [idx + 1] = buf [idxi + 1];
          q_buf [idx + 2] = buf [idxi + 0];
          q_buf [idx + 3] = buf [idxi + 1];
          q_buf [idx + 4] = buf [idxi + 0];
          q_buf [idx + 5] = buf [idxi + 1];
          q_buf [idx + 6] = buf [idxi + 0];
          q_buf [idx + 7] = buf [idxi + 1];
          q_buf [idx + 8] = buf [idxi + 0];
          q_buf [idx + 9] = buf [idxi + 1];
          q_buf [idx +10] = buf [idxi + 0];
          q_buf [idx +11] = buf [idxi + 1];
          idxi += 2;
        }
      }*/
    }

//*/
   // if (aud_rec_ena == 0)                                               // Return if audio recording not enabled
      //return;

/*#ifndef NDEBUG
    char * aud_rec_file = "/home/m/dev/hu/aa.pcm";
  #ifdef __ANDROID_API__
    aud_rec_file = "/sdcard/hu.pcm";
  #endif

    if (aud_rec_fd < 0)
      aud_rec_fd = open (aud_rec_file, O_RDWR | O_CREAT, S_IRWXU | S_IRWXG | S_IRWXO);
    int written = -77;
    if (aud_rec_fd >= 0)
      written = write (aud_rec_fd, buf, len);
    logv ("Audio written: %d", written);
//#endif*/
  //}


int iaap_media_process (int chan, int msg_type, int flags, byte * buf, int len) {     // Process video packet

//new message starts with 32771 (8003)
//8001 is the state (shuffle,source,etc), we will need to concat all between.
	
			iaap_media_decode(chan,buf,len);
	

	

    return (0);
  }

  byte aud_ack [] = {0x80, 0x04, 0x08, 0, 0x10,  1};                    // Global Ack: 0, 1     Same as video ack ?

  //int aud_ack_ctr = 0;
  
  int iaap_audio_process (int chan, int msg_type, int flags, byte * buf, int len) { // 300 ms @ 48000/sec   samples = 14400     stereo 16 bit results in bytes = 57600
    //loge ("????????????????????? !!!!!!!!!!!!!!!!!!!!!!!!!   !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!   aud_ack_ctr: %d  len: %d", aud_ack_ctr ++, len);

    //logd ("iaap_audio_process chan: %d  msg_type: %d  flags: 0x%x  buf: %p  len: %d", chan, msg_type, flags, buf, len); // iaap_audio_process msg_type: 0  flags: 0xb  buf: 0xe08cbfb8  len: 8202

    if (chan == AA_CH_AU1)
      aud_ack [3] = ack_val_au1;
    else if (chan == AA_CH_AU2)
      aud_ack [3] = ack_val_au2;
    else
      aud_ack [3] = ack_val_aud;

    int ret = hu_aap_enc_send (chan, aud_ack, sizeof (aud_ack));      // Respond with ACK (for all fragments ?)


    //hex_dump ("AUDIO: ", 16, buf, len);
    if (len >= 10) {
   /*   int ctr = 0;
      unsigned long ts = 0, t2 = 0;
      for (ctr = 2; ctr <= 9; ctr ++) {
        ts = ts << 8;
        t2 = t2 << 8;
        ts += (unsigned long) buf [ctr];
        t2 += buf [ctr];
        if (ctr == 6)
          logv ("iaap_audio_process ts: %d 0x%x  t2: %d 0x%x", ts, ts, t2, t2);
      }
      logv ("iaap_audio_process ts: %d 0x%x  t2: %d 0x%x", ts, ts, t2, t2);*/

      iaap_audio_decode (chan, & buf [10], len - 10);//assy, assy_size);                                                                                    // Decode PCM audio fully re-assembled
    }

    return (0);
  }

  //int vid_ack_ctr = 0;
  int iaap_video_process (int msg_type, int flags, byte * buf, int len) {    // Process video packet
// MaxUnack
//loge ("????????????????????? !!!!!!!!!!!!!!!!!!!!!!!!!   !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!   vid_ack_ctr: %d  len: %d", vid_ack_ctr ++, len);
int ret = hu_aap_enc_send (AA_CH_VID, vid_ack, sizeof (vid_ack));      // Respond with ACK (for all fragments ?)
	//logd("Sending Video Ack");
    if (0) {
    }
    else if (flags == 11 && (msg_type == 0 || msg_type == 1) && (buf [10] == 0 && buf [11] == 0 && buf [12] == 0 && buf [13] == 1)) {  // If Not fragmented Video
      iaap_video_decode (& buf [10], len - 10);                                                                               // Decode H264 video
    }
    else if (flags == 9 && (msg_type == 0 || msg_type == 1) && (buf [10] == 0 && buf [11] == 0 && buf [12] == 0 && buf [13] == 1)) {   // If First fragment Video
      memcpy (assy, & buf [10], len - 10);                                                                                    // Len in bytes 2,3 doesn't include total len 4 bytes at 4,5,6,7
      assy_size = len - 10;                                                                                                   // Add to re-assembly in progress
    }
    else if (flags == 11 && msg_type == 1 && (buf [2] == 0 && buf [3] == 0 && buf [4] == 0 && buf [5] == 1)) {                     // If Not fragmented First video config packet
      iaap_video_decode (& buf [2], len - 2);                                                                                 // Decode H264 video
    }
    else if (flags == 8) {                                                                                                     // If Middle fragment Video
      memcpy (& assy [assy_size], buf, len);
      assy_size += len;                                                                                                       // Add to re-assembly in progress
    }
    else if (flags == 10) {                                                                                                    // If Last fragment Video
      memcpy (& assy [assy_size], buf, len);
      assy_size += len;                                                                                                       // Add to re-assembly in progress
      iaap_video_decode (assy, assy_size);                                                                                    // Decode H264 video fully re-assembled
    }
    else
      loge ("Video error msg_type: %d  flags: 0x%x  buf: %p  len: %d", msg_type, flags, buf, len);

    return (0);
  }

  int iaap_msg_process (int chan, int flags, byte * buf, int len) {

  //hex_dump ("HEX_DUMP: ", 256, buf, len);
  
    int msg_type = (int) buf [1];
    msg_type += ((int) buf [0] * 256);
	//logd("message type: %d",msg_type);

    if (ena_log_verbo)
      logd ("iaap_msg_process msg_type: %d  len: %d  buf: %p chan: %d", msg_type, len, buf, chan);

    int run = 0;
    if ((chan == AA_CH_AUD || chan == AA_CH_AU1 || chan == AA_CH_AU2) && (msg_type == 0 || msg_type == 1)) {// || flags == 8 || flags == 9 || flags == 10 ) {         // If Audio Output...
      return (iaap_audio_process (chan, msg_type, flags, buf, len)); // 300 ms @ 48000/sec   samples = 14400     stereo 16 bit results in bytes = 57600
    }
	
    else if ((chan == AA_CH_VID && msg_type == 0 || msg_type == 1 || flags == 8 || flags == 9 || flags == 10 ) && (chan != AA_CH_MED)) {    // If Video...
    //else if (chan == AA_CH_VID)  {    // If Video...
	  //logd("Channel is: %d",chan);
      return (iaap_video_process (msg_type, flags, buf, len));
    }
	else if (chan == AA_CH_MED && msg_type != 7) {
		//logd("Channel is: %d",chan);
		 return (iaap_media_process (chan, msg_type, flags, buf, len));
	 }
	 else if (chan == AA_CH_NAV && (msg_type == 32772 || msg_type == 32773))
		 return (iaap_media_process (chan, msg_type, flags, buf, len));
	 
    else if (msg_type >= 0 && msg_type <= 31)
      run = 0;
    else if (msg_type >= 32768 && msg_type <= 32799)
      run = 1;
    else if (msg_type >= 65504 && msg_type <= 65535)
      run = 2;
    else {
      loge ("Unknown msg_type: %d", msg_type);
      return (0);
    }

    int prot_func_ret = -1;
    int num = msg_type & 0x1f;
    aa_type_ptr_t func = NULL;
    if (chan >= 0 && chan <= AA_CH_MAX)
      func = aa_type_array [chan] [run] [num];
    else
      loge ("chan >= 0 && chan <= AA_CH_MAX chan: %d %s", chan, chan_get (chan));
    if (func)
      prot_func_ret = (* func) (chan, buf, len);
    else
		{
		loge ("No func chan: %d %s  run: %d  num: %d", chan, chan_get (chan), run, num);
		hex_dump ("HEX_DUMP: ", 256, buf, len);
		}

        logd ("Function to be run: %d %s  run: %d  num: %d", chan, chan_get (chan), run, num);
  
    if (log_packet_info) {
      if (chan == AA_CH_VID && (flags == 8 || flags == 0x0a || msg_type == 0)) // || msg_type ==1))
        ;
      //else if (chan == AA_CH_VID && msg_type == 32768 + 4)
      //  ;
      else {
        //logd ("        iaap_msg_process() len: %d  buf: %p  chan: %d %s  flags: 0x%x  msg_type: %d", len, buf, chan, chan_get (chan), flags, msg_type);
        logd ("--------------------------------------------------------");  // Empty line / 56 characters
      }
    }


    return (prot_func_ret);
  }

  int hu_aap_stop () {                                                  // Sends Byebye, then stops Transport/USBACC/OAP

                                                                        // Continue only if started or starting...
    if (iaap_state != hu_STATE_STARTED && iaap_state != hu_STATE_STARTIN)
      return (0);

    // Send Byebye
    iaap_state = hu_STATE_STOPPIN;
    logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));

    int ret = ihu_tra_stop ();                                           // Stop Transport/USBACC/OAP
    iaap_state = hu_STATE_STOPPED;
    logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));

    return (ret);
	exit(0);
  }

  int hu_aap_start (int cmd_len, byte * cmd_buf,long myip_string, int transport_audio, int hr) {                // Starts Transport/USBACC/OAP, then AA protocol w/ VersReq(1), SSL handshake, Auth Complete
 
vid_ack[3]=-1;
  logd("Starting hu_aap_start %d audio transport: %d",myip_string,transport_audio);
    /* Should not happen!
	if (iaap_state == hu_STATE_STARTED) {
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (0);
    }
	*/
	hex_dump ("CMD_BUF: ", 128, cmd_buf, cmd_len);
	
    iaap_state = hu_STATE_STARTIN;
    logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));

    int ret = ihu_tra_start (cmd_buf[1], cmd_buf[2],cmd_buf[3], myip_string, transport_audio, hr);                   // Start Transport/USBACC/OAP
	new_buf_length=(cmd_len-4+sizeof(sd_buf2));
	new_sdbuf=(char *) malloc(new_buf_length);
	int idx=0;
	int i=0;
	for (i = 0; i < 124 ; i ++) { new_sdbuf[i]=sd_buf2[i];	idx++;}
	new_sdbuf[idx++]=(cmd_len-4) & 0xFF;
    for (i = 4; i<(cmd_len);i++) { new_sdbuf[idx++]=cmd_buf[i];}
    for (i = 125; i<sizeof(sd_buf2);i++) { new_sdbuf[idx++]=sd_buf2[i];}
		

	
	hex_dump ("CMD_BUF: ", sizeof(sd_buf2), sd_buf2, sizeof(sd_buf2));
	hex_dump ("CMD_BUF: ", new_buf_length, new_sdbuf, new_buf_length);
	
	
	//memmove(sd_buf2,updated_sdbuf,cmd_len-3+sizeof(sd_buf2));
	
	
	// Also add a , byte * hf_mac to the fucntion when re-enabeling bluetooth.
	//hex_dump ("BluetoothMac: ", 32, hf_mac, 17);
	//memcpy(bluetooth_mac,hf_mac,17);
	
    if (ret) {
      iaap_state = hu_STATE_STOPPED;
      logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (ret);                                                     // Done if error
    }
	
    byte vr_buf [] = {0, 3, 0, 6, 0, 1, 0, 1, 0, 2};                    // Version Request
    ret = hu_aap_tra_set (0, 3, 1, vr_buf, sizeof (vr_buf));
	
    ret = hu_aap_tra_send (vr_buf, sizeof (vr_buf), 1000);              // Send Version Request
	
    if (ret < 0) {
      loge ("Version request send ret: %d", ret);
      hu_aap_stop ();
      return (-1);
    }  

    byte buf [DEFBUF] = {0};
    errno = 0;
	int ret_count=5;
	do {
    ret = hu_aap_tra_recv (buf,12, -4);                    // Get Rx packet from Transport:    Wait for Version Response
    ret_count--;
	logd ("Version response recv ret, attemp %d, ret value: %d", ret_count, ret);
	} while (ret!=12 && ret_count>=0);
    if (ret <= 0) {
      loge ("Version response recv ret: %d", ret);
      hu_aap_stop ();
      return (-1);
    }  
    logd ("Version response recv ret: %d", ret);

//*
    ret = hu_ssl_handshake ();                                          // Do SSL Client Handshake with AA SSL server
    if (ret) {
      hu_aap_stop ();
      return (ret);
    }

    byte ac_buf [] = {0, 3, 0, 4, 0, 4, 8, 0};                          // Status = OK
    ret = hu_aap_tra_set (0, 3, 4, ac_buf, sizeof (ac_buf));
    ret = hu_aap_tra_send (ac_buf, sizeof (ac_buf), 1000);              // Auth Complete, must be sent in plaintext
    if (ret < 0) {
      loge ("hu_aap_tra_send() ret: %d", ret);
      hu_aap_stop ();
      return (-1);
    }  
    hu_ssl_inf_log ();

    iaap_state = hu_STATE_STARTED;
    logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
//*/
	
	
    return (0);
  }



/*
http://stackoverflow.com/questions/11929773/compiling-the-latest-openssl-for-android/18577811#18577811
http://stackoverflow.com/questions/22753221/openssl-read-write-handshake-data-with-memory-bio
http://www.roxlu.com/2014/042/using-openssl-with-memory-bios
https://www.openssl.org/docs/ssl/SSL_read.html
http://blog.davidwolinsky.com/2009/10/memory-bios-and-openssl.html
http://www.cisco.com/c/en/us/support/docs/security-vpn/secure-socket-layer-ssl/116181-technote-product-00.html
*/


  int iaap_recv_dec_process (int chan, int flags, byte * buf, int len) {// Decrypt & Process 1 received encrypted message
  //logd("Decoding channel %d, with flag: %d, size of buffer: %d, reported lenght: %d",chan,flags,sizeof(buf),len);
	//hex_dump ("iaap_recv_dec_process: ", 1024, buf, len);
	 byte dec_buf [65536 * 4] = {0};
    int bytes_written = BIO_write (hu_ssl_rm_bio, buf, len);           // Write encrypted to SSL input BIO
    if (bytes_written <= 0) {
      loge ("BIO_write() bytes_written: %d", bytes_written);
      return (-1);
    }
    if (bytes_written != len)
      loge ("BIO_write() len: %d  bytes_written: %d  chan: %d %s", len, bytes_written, chan, chan_get (chan));
    else if (ena_log_verbo)
      logd ("BIO_write() len: %d  bytes_written: %d  chan: %d %s", len, bytes_written, chan, chan_get (chan));

    errno = 0;
    int ctr = 0;
    int max_tries = 1;  // Higher never works
    int bytes_read = -1;
    while (bytes_read <= 0 && ctr ++ < max_tries) {
      bytes_read = SSL_read (hu_ssl_ssl, dec_buf, sizeof (dec_buf));   // Read decrypted to decrypted rx buf
      if (bytes_read <= 0) {
        loge ("ctr: %d  SSL_read() bytes_read: %d  errno: %d", ctr, bytes_read, errno);
        hu_ssl_ret_log (bytes_read);
        //ms_sleep (1);
      }
      //logd ("ctr: %d  SSL_read() bytes_read: %d  errno: %d", ctr, bytes_read, errno);
    }

    if (bytes_read <= 0) {
      loge ("ctr: %d  SSL_read() bytes_read: %d  errno: %d", ctr, bytes_read, errno);
      hu_ssl_ret_log (bytes_read);
	return (-1);                                                      // Fatal so return error and de-initialize; Should we be able to recover, if Transport data got corrupted ??
    }
    if (ena_log_verbo)
      logd ("ctr: %d  SSL_read() bytes_read: %d", ctr, bytes_read);

/*#ifndef NDEBUG
////    if (chan != AA_CH_VID)                                          // If not video...
      if (log_packet_info) {
        char prefix [DEFBUF] = {0};
        snprintf (prefix, sizeof (prefix), "R %d %s %1.1x", chan, chan_get (chan), flags);  // "R 1 VID B"
        int rmv = hu_aad_dmp (prefix, "AA", chan, flags, dec_buf, bytes_read);           // Dump decrypted AA
      }
#endif*/

    int prot_func_ret = iaap_msg_process (chan, flags, dec_buf, bytes_read);      // Process decrypted AA protocol message
    return (0);//prot_func_ret);
  }


    //  Process 1 encrypted "receive message set":
      // - Read encrypted message from Transport
      // - Process/react to decrypted message by sending responses etc.
/*
        Tricky issues:

          - Read() may return less than a full packet.
              USB is somewhat "packet oriented" once I raised DEFBUF/sizeof(rx_buf) from 16K to 64K (Maximum video fragment size)
              But TCP is more stream oriented.
			  Looking at DHU I have increased the buffer even further to 128Kb (current value used in DHU) - Emil

          - Read() may contain multiple packets, returning all or the end of one packet, plus all or the beginning of the next packet.
              So far I have only seen 2 complete packets in one read().

          - Read() may return all or part of video stream data fragments. Multiple fragments need to be re-assembled before H.264 video processing.
            Fragments may be up to 64K - 256 in size. Maximum re-assembled video packet seen is around 150K; using 256K re-assembly buffer at present.
            

*/




 int hu_aap_recv_process () {                                          // 
                                                                    // Terminate unless started or starting (we need to process when starting)
    if (iaap_state != hu_STATE_STARTED && iaap_state != hu_STATE_STARTIN) {
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (-1);
    }

	// if (first_run==1 && transport_type == 2) {
			// hu_tcp_start_my_thread();
			// first_run=0;
	// }
	
    byte * buf = rx_buf;

    int ret = 0;
    errno = 0;
    int min_size_hdr = 6;
    int rx_len = sizeof (rx_buf);
   if (transport_type == 2)                                            // If wifi...
     rx_len = min_size_hdr;                                           // Just get the header

    int have_len = 0;                                                   // Length remaining to process for all sub-packets plus 4/8 byte headers

    have_len = hu_aap_tra_recv (rx_buf, rx_len, -4);     // Get Rx packet from Transport
    
    if (have_len == 0) {                                                // If no data, then done w/ no data
      return (0);
    }
	/*
    if (have_len < min_size_hdr) {                                      // If we don't have a full 6 byte header at least...
      loge ("Recv have_len: %d", have_len);
      hu_aap_stop ();
      return (-1);
    }  */

    while (have_len > 0) {                                              // While length remaining to process,... Process Rx packet:
      // if (ena_log_verbo) {
         // logd ("Recv while (have_len > 0): %d", have_len);
         //hex_dump ("LR: ", 16, buf, have_len);
      // }
      int chan = (int) buf [0];                                         // Channel
      int flags = buf [1];                                              // Flags
	
      int enc_len = (int) buf [3];                                      // Encoded length of bytes to be decrypted (minus 4/8 byte headers)
      enc_len += ((int) buf [2] * 256);

      int msg_type = (int) buf [5];                                     // Message Type (or post handshake, mostly indicator of SSL encrypted data)
      msg_type += ((int) buf [4] * 256);

      have_len -= 4;                                                    // Length starting at byte 4: Unencrypted Message Type or Encrypted data start
      buf += 4;                                                         // buf points to data to be decrypted
      if (flags & 0x08 != 0x08) {
        loge ("NOT ENCRYPTED !!!!!!!!! have_len: %d  enc_len: %d  buf: %p  chan: %d %s  flags: 0x%x  msg_type: %d", have_len, enc_len, buf, chan, chan_get (chan), flags, msg_type);
        hu_aap_stop ();
        return (-1);
      }
      if ((chan == AA_CH_VID || chan== 9 || chan == 10 )&& flags == 9)  {                            // If First fragment Video... (Packet is encrypted so we can't get the real msg_type or check for 0, 0, 0, 1)
        //logd("First fragment Video");
		int total_size = (int) buf [3];
        total_size += ((int) buf [2] * 256);
        total_size += ((int) buf [1] * 256 * 256);
        total_size += ((int) buf [0] * 256 * 256 * 256);

        if (total_size > max_assy_size)                                 // If new  max_assy_size... (total_size seen as big as 151 Kbytes)
          max_assy_size = total_size;                                   // Set new max_assy_size      See: jni/hu_aap.c:  byte assy [65536 * 16] = {0}; // up to 1 megabyte
                                                                         //                               & jni/hu_uti.c:  #define vid_buf_BUFS_SIZE    65536 * 4
                                                                        // Up to 256 Kbytes// & src/ca/yyx/hu/hu_tro.java:    byte [] assy = new byte [65536 * 16];
                                                                        // & src/ca/yyx/hu/hu_tra.java:      res_buf = new byte [65536 * 4];
        if (total_size > 320 * 1024)
          logw ("First fragment total_size: %d  max_assy_size: %d", total_size, max_assy_size);
        else
          logv ("First fragment total_size: %d  max_assy_size: %d", total_size, max_assy_size);
        have_len -= 4;                                                  // Remove 4 length bytes inserted into first video fragment
        buf += 4;
      }
	  
      if (have_len < enc_len) {                                         // If we need more data for the full packet...
        int need_len = enc_len - have_len;
	 if (transport_type != 2)   {
	    memmove(rx_buf, buf, have_len);
		buf = rx_buf;
		 }
		
			//logd("current Buffer size: %d, and we try to feed in: %d",sizeof(buf),need_len);
			int need_ret = hu_aap_tra_recv (& buf [have_len], need_len, -1);// Get Rx packet from Transport. Use -1 instead of iaap_tra_recv_tmo to indicate need to get need_len bytes
			//logd("need len: %d, received len: %d",need_len,need_ret);
			// Length remaining for all sub-packets plus 4/8 byte headers
        if (need_ret != need_len) {
			logd ("have_len: %d < enc_len: %d  need_len: %d", have_len, enc_len, need_len);
		    loge ("Recv bytes: %d but we expected: %d", need_ret, need_len);
						
           hu_aap_stop ();

          return (-1);
		 
        }
        have_len = enc_len;                                             // Length to process now = encoded length for 1 packet
      }

 
      ret = iaap_recv_dec_process (chan, flags, buf, enc_len);          // Decrypt & Process 1 received encrypted message
      if (ret < 0) {                                                    // If error...
        loge ("Error iaap_recv_dec_process() ret: %d  have_len: %d  enc_len: %d  buf: %p  chan: %d %s  flags: 0x%x  msg_type: %d", ret, have_len, enc_len, buf, chan, chan_get (chan), flags, msg_type);
        hu_aap_stop ();
        return (ret);  
      }

      have_len -= enc_len;                                              // Consume processed sub-packet and advance to next, if any
      buf += enc_len;
      if (have_len != 0)
        logd ("iaap_recv_dec_process() more than one message   have_len: %d  enc_len: %d", have_len, enc_len);
    }
	memset(rx_buf, 0, sizeof(rx_buf));
    return (ret);                                                       // Return value from the last iaap_recv_dec_process() call; should be 0
  }





















/*
  int hu_aap_recv_process () {                                          // 
                                                                    // Terminate unless started or starting (we need to process when starting)
    if (iaap_state != hu_STATE_STARTED && iaap_state != hu_STATE_STARTIN) {
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (-1);
    }

    int ret = 0;
    errno = 0;
    
    
	int rx_len=16384; //Use 256Kb max len if Wifi
   /* if (transport_type == 2)	
		rx_len= 4*16384;*
	int have_len = 0;                                                   
	
  
  if (transport_type == 2) 
   {
	byte * buf = rx_buf;
	rx_len = 6; 
    have_len = hu_aap_tra_recv (rx_buf, rx_len, -1); 			    // Get Rx packet from Transport
	if (have_len == 0)                                               // If no data, then done w/ no data
      return (0);
    
	while (have_len > 0) {                                              // While length remaining to process,... Process Rx packet:
     
      int chan = (int) buf [0];                                         // Channel
      int flags = buf [1];                                              // Flags
	
      int enc_len = (int) buf [3];                                      // Encoded length of bytes to be decrypted (minus 4/8 byte headers)
      enc_len += ((int) buf [2] * 256);

      int msg_type = (int) buf [5];                                     // Message Type (or post handshake, mostly indicator of SSL encrypted data)
      msg_type += ((int) buf [4] * 256);

      have_len -= 4;                                                    // Length starting at byte 4: Unencrypted Message Type or Encrypted data start
      buf += 4;                                                         // buf points to data to be decrypted
      if (flags & 0x08 != 0x08) {
        loge ("NOT ENCRYPTED !!!!!!!!! have_len: %d  enc_len: %d  buf: %p  chan: %d %s  flags: 0x%x  msg_type: %d", have_len, enc_len, buf, chan, chan_get (chan), flags, msg_type);
        hu_aap_stop ();
        return (-1);
      }
      if ((chan == AA_CH_VID || chan== 9 )&& flags == 9) {                             // If First fragment Video... (Packet is encrypted so we can't get the real msg_type or check for 0, 0, 0, 1)
        //logd("First fragment Video");
		int total_size = (int) buf [3];
        total_size += ((int) buf [2] * 256);
        total_size += ((int) buf [1] * 256 * 256);
        total_size += ((int) buf [0] * 256 * 256 * 256);

        if (total_size > max_assy_size)                                 // If new  max_assy_size... (total_size seen as big as 151 Kbytes)
          max_assy_size = total_size;                                   // Set new max_assy_size      See: jni/hu_aap.c:  byte assy [65536 * 16] = {0}; // up to 1 megabyte
                                                                         //                               & jni/hu_uti.c:  #define vid_buf_BUFS_SIZE    65536 * 4
                                                                        // Up to 256 Kbytes// & src/ca/yyx/hu/hu_tro.java:    byte [] assy = new byte [65536 * 16];
                                                                        // & src/ca/yyx/hu/hu_tra.java:      res_buf = new byte [65536 * 4];
        if (total_size > 320 * 1024)
          logw ("First fragment total_size: %d  max_assy_size: %d", total_size, max_assy_size);
        else
          logv ("First fragment total_size: %d  max_assy_size: %d", total_size, max_assy_size);
        have_len -= 4;                                                  // Remove 4 length bytes inserted into first video fragment
        buf += 4;
      }
	  
      if (have_len < enc_len) {                                         // If we need more data for the full packet...
        int need_len = enc_len - have_len;
	
		
			//logd("current Buffer size: %d, and we try to feed in: %d",sizeof(buf),need_len);
			int need_ret = hu_aap_tra_recv (& buf [have_len], need_len, -1);// Get Rx packet from Transport. Use -1 instead of iaap_tra_recv_tmo to indicate need to get need_len bytes
			//logd("need len: %d, received len: %d",need_len,need_ret);
			// Length remaining for all sub-packets plus 4/8 byte headers
					if (need_ret != need_len) {
						logd ("have_len: %d < enc_len: %d  need_len: %d", have_len, enc_len, need_len);
						loge ("Recv bytes: %d but we expected: %d", need_ret, need_len);
									
					   hu_aap_stop ();

					  return (-1);
					 
					}
        have_len = enc_len;                                             // Length to process now = encoded length for 1 packet
      }

      ret = iaap_recv_dec_process (chan, flags, buf, enc_len);          // Decrypt & Process 1 received encrypted message
      if (ret < 0) {                                                    // If error...
        loge ("Error iaap_recv_dec_process() ret: %d  have_len: %d  enc_len: %d  buf: %p  chan: %d %s  flags: 0x%x  msg_type: %d", ret, have_len, enc_len, buf, chan, chan_get (chan), flags, msg_type);
        hu_aap_stop ();
        return (ret);  
      }

      have_len -= enc_len;                                              // Consume processed sub-packet and advance to next, if any
      buf += enc_len;
      if (have_len != 0)
        logd ("iaap_recv_dec_process() more than one message   have_len: %d  enc_len: %d", have_len, enc_len);
    }
	return(0);
   }
   else
   {
	 //  if (transport_type==2)
	// have_len = hu_aap_tra_recv ( & global_data [global_position], rx_len, -6);     // Get Rx packet from Transport
	  // else
	 have_len = hu_aap_tra_recv ( & global_data [global_position], rx_len, 150);     // Get Rx packet from Transport
	 //logd("Respons size is: %d positions: %d",have_len,global_position);	 
	 if ((have_len <= 0 && transport_type == 2)  || (have_len==0 && transport_type == 1))                                               // If no data, then done w/ no data
	 {
		return (0);
	 }
	//
	global_position=global_position+have_len;
    ret=data_loop();	
   }
	
	
    return (ret);                                                       // Return value from the last iaap_recv_dec_process() call; should be 0
  //}
  }
 int data_loop () {
	 
	 
	 int enc_len = (int) global_data [3];                                      
     enc_len += ((int) global_data [2] * 256);
	 //byte * buf = rx_buf;
	 //logd("Encoded lenght is: %d, and we currently have: %d",enc_len,global_position);
	 if (global_position<enc_len)
		  return(0);
	 
	 while (global_position>=enc_len+4) {
     int data_possition=0;
	  int chan = (int) global_data [0];                                         // Channel
      int flags = global_data [1];                                              // Flags
	
      int msg_type = (int) global_data [5];                                     // Message Type (or post handshake, mostly indicator of SSL encrypted data)
      msg_type += ((int) global_data [4] * 256);
			//logd("Channel %d, flags: %d, encoded lenght: %d, msg type: %d",chan, flags, enc_len,msg_type);
       	data_possition += 4;                                                         // buf points to data to be decrypted
      if (flags & 0x08 != 0x08) {
            hu_aap_stop ();
          return (-1);
      }
      if ((chan == AA_CH_VID || chan== 9 )&& flags == 9) {                            // If First fragment Video... (Packet is encrypted so we can't get the real msg_type or check for 0, 0, 0, 1)
        //logd("First fragment Video");
		int total_size = (int) global_data [7];
        total_size += ((int) global_data [6] * 256);
        total_size += ((int) global_data [5] * 256 * 256);
        total_size += ((int) global_data [4] * 256 * 256 * 256);

        if (total_size > max_assy_size)                                 // If new  max_assy_size... (total_size seen as big as 151 Kbytes)
          max_assy_size = total_size;                                   // Set new max_assy_size      See: jni/hu_aap.c:  byte assy [65536 * 16] = {0}; // up to 1 megabyte
                                                                         //                               & jni/hu_uti.c:  #define vid_buf_BUFS_SIZE    65536 * 4
                                                                        // Up to 256 Kbytes// & src/ca/yyx/hu/hu_tro.java:    byte [] assy = new byte [65536 * 16];
                                                                        // & src/ca/yyx/hu/hu_tra.java:      res_buf = new byte [65536 * 4];
        if (total_size > 320 * 1024)
          logw ("First fragment total_size: %d  max_assy_size: %d", total_size, max_assy_size);
        else
          logv ("First fragment total_size: %d  max_assy_size: %d", total_size, max_assy_size);
       
        data_possition += 4;
      }



		iaap_recv_dec_process (chan, flags, &global_data[data_possition], enc_len);  // Decode one message only
		memmove(&global_data[0],&global_data[data_possition+enc_len],sizeof(global_data)-enc_len); // shift all content to the left
		global_position=global_position-enc_len-data_possition;
		if (global_position > 0)
		{ 
		enc_len = (int) global_data [3];
		enc_len += ((int) global_data [2] * 256); 
		//logd("We have more packets, is it a full packet? Global_pos: %d, enc_len: %d, data_pos: %d, size: %d", global_position,enc_len,data_possition,sizeof(global_data));
		
		}
		
	 }
	 return(1);
 }*/

