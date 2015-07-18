
  int hu_usb_recv  (byte * buf, int len, int tmo);                     // Used by hu_aap:hu_aap_usb_recv ()
  int hu_usb_send  (byte * buf, int len, int tmo);                     // Used by hu_aap:hu_aap_usb_send ()
  int hu_usb_stop  ();                                                 // Used by hu_aap:hu_aap_stop     ()
  int hu_usb_start (byte ep_in_addr, byte ep_out_addr);                // Used by hu_aap:hu_aap_start    ()

