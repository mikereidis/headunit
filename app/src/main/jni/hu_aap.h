
  int hu_aap_usb_recv (byte * buf, int len, int tmo);                      // Used by intern,                      hu_ssl
  int hu_aap_usb_set  (int chan, int flags, int type, byte * buf, int len);// Used by intern                       hu_ssl
  int hu_aap_usb_send (byte * buf, int len, int tmo);                      // Used by intern,                      hu_ssl
  int hu_aap_enc_send (int chan, byte * buf, int len);                     // Used by intern,            hu_jni     // Encrypted Send
  int hu_aap_stop     ();                                                  // Used by          hu_mai,  hu_jni     // NEED: Send Byebye, Stops USB/ACC/OAP
  int hu_aap_start    (byte ep_in_addr, byte ep_out_addr);                 // Used by          hu_mai,  hu_jni     // Starts USB/ACC/OAP, then AA protocol w/ VersReq(1), SSL handshake, Auth Complete
  int hu_aap_recv_process ();                                              // Used by          hu_mai,  hu_jni     // Process 1 encrypted receive message set:
                                                                                                                          // Read encrypted message from USB
                                                                                                                          // Respond to decrypted message
                                                                                                                          // If video data, put on queue


