
  int hu_aap_mic_get ();
  int hu_aap_out_get (int chan);

  // Channels ( or Service IDs)
  #define AA_CH_CTR 0                                                                                  // Sync with hu_tra.java, hu_aap.h and hu_aap.c:aa_type_array[]
  // #define AA_CH_SEN 1
  // #define AA_CH_VID 2
  // #define AA_CH_TOU 3  
  #define AA_CH_SEN 2
  #define AA_CH_VID 3
  #define AA_CH_TOU 1
  // #define AA_CH_AUD 4
  // #define AA_CH_AU1 5
  // #define AA_CH_AU2 6  
  #define AA_CH_AUD 6
  #define AA_CH_AU1 4
  #define AA_CH_AU2 5
  #define AA_CH_MIC 7
  #define AA_CH_MED 9
  #define AA_CH_NAV 10
  #define AA_CH_MAX 14
  #define videobuffsize 5*1048576
 #define rxbuffsize 1048576
 #define monoaudiobuffsize 524288
 #define audiobuffsize 2*1048576
 #define metadatabuffsize 2*65536
   

  int hu_aap_tra_recv (byte * buf, int len, int tmo);                      // Used by intern,                      hu_ssl
  int hu_aap_tra_set  (int chan, int flags, int type, byte * buf, int len);// Used by intern                       hu_ssl
  int hu_aap_tra_send (byte * buf, int len, int tmo);                      // Used by intern,                      hu_ssl
  int hu_aap_enc_send (int chan, byte * buf, int len);                     // Used by intern,            hu_jni     // Encrypted Send
  int hu_aap_stop     ();                                                  // Used by          hu_mai,  hu_jni     // NEED: Send Byebye, Stops USB/ACC/OAP
  int hu_aap_start    (int cmd_len, byte * cmd_buf, long myip_string, int transport_audio, int hr);                 // Used by          hu_mai,  hu_jni     // Starts USB/ACC/OAP, then AA protocol w/ VersReq(1), SSL handshake, Auth Complete
  int hu_aap_recv_process ();                                              // Used by          hu_mai,  hu_jni     // Process 1 encrypted receive message set:
                                                                                                                          // Read encrypted message from USB
                                                                                                                          // Respond to decrypted message
                                                                                                                          // If video data, put on queue


