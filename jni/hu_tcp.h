
  int hu_tcp_recv  (byte * buf, int len, int tmo);                     // Used by hu_aap:hu_aap_tcp_recv ()
  int hu_tcp_send  (byte * buf, int len, int tmo);                     // Used by hu_aap:hu_aap_tcp_send ()
  int hu_tcp_stop  ();                                                 // Used by hu_aap:hu_aap_stop     ()
  int hu_tcp_start (byte ep_in_addr, byte ep_out_addr);                // Used by hu_aap:hu_aap_start    ()

