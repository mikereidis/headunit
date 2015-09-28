
  // Android Auto Protocol Handler

  #define LOGTAG "hu_aap"
  #include "hu_usb.h"
  #include "hu_uti.h"
  #include "hu_ssl.h"
  #include "hu_aap.h"
#ifndef NDEBUG
  #include "hu_aad.h"
#endif

  int iaap_state = 0; // 0: Initial    1: Startin    2: Started    3: Stoppin    4: Stopped

/*
  byte iaap_ch_ctr = 0;
  byte iaap_ch_vid = 1;
  byte iaap_ch_tou = 2;
  byte iaap_ch_sen = 3;
*/
  #define AA_CH_CTR 0
  #define AA_CH_VID 1
  #define AA_CH_TOU 2
  #define AA_CH_SEN 3


  int iaap_recv_tmo = 100;//25; // 10 doesn't work ? 100 does
  int iaap_send_tmo = 250;


  byte enc_buf [DEFBUF] = {0};                                          // Global encrypted transmit data buffer

  byte assy [65536 * 16] = {0};                                         // Global assembly buffer for video fragments: Up to 1 megabyte   ; 128K is fine for now at 800*640
  int assy_size = 0;                                                    // Current size
  int max_assy_size = 0;                                                // Max observed size needed:  151,000

  int record_enable = 0;                                                // Video recording to file
  int record_fd = -1;

  byte vid_ack [] = {0x80, 0x04, 0x08, 0, 0x10,  1};                    // Global Ack: 0, 1

  byte  rx_buf [DEFBUF] = {0};                                          // Global USB Rx buf
  //byte dec_buf [DEFBUF] = {0};                                          // Global decrypted receive buffer
  #define dec_buf rx_buf                          // Use same buffer !!!



  int hu_aap_usb_set (int chan, int flags, int type, byte * buf, int len) {  // Convenience function sets up 6 byte USB header: chan, flags, len, type

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

  int hu_aap_usb_recv (byte * buf, int len, int tmo) {
    int ret = 0;
    if (iaap_state != hu_STATE_STARTED && iaap_state != hu_STATE_STARTIN) {   // Need to recv when starting
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (-1);
    }
    ret = hu_usb_recv (buf, len, tmo);
    if (ret < 0) {
      loge ("hu_usb_recv() error so stop USB & AAP  ret: %d", ret);
      hu_aap_stop (); 
    }
    return (ret);
  }

  int hu_aap_usb_send (byte * buf, int len, int tmo) {                 // Send USB data: chan,flags,len,type,...
    if (iaap_state != hu_STATE_STARTED && iaap_state != hu_STATE_STARTIN) {   // Need to send when starting
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (-1);
    }
    if (ena_log_verbo && ena_log_send) {
#ifndef NDEBUG
      if (buf [1] | 0x08 == 0)                                          // If not encrypted (which was dumped in hu_aap_enc_send()
        hu_aad_dmp ("US: ", 1, & buf [4], len - 4);
#endif
    }
    int ret = hu_usb_send (buf, len, tmo);
    if (ret < 0 || ret != len) {
      loge ("Error hu_usb_send() error so stop USB & AAP  ret: %d  len: %d", ret, len);
      hu_aap_stop (); 
      return (-1);
    }  
    if (ena_log_verbo && ena_log_send)
      logd ("OK hu_usb_send() ret: %d  len: %d", ret, len);
    return (ret);
  }



  int hu_aap_enc_send (int chan, byte * buf, int len) {                // Send encrypted data: type,...
    if (iaap_state != hu_STATE_STARTED) {
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (-1);
    }
    if (ena_log_verbo && ena_log_send) {
#ifndef NDEBUG
      hu_aad_dmp ("ES: ", 1, buf, len);
#endif
    }
    int bytes_written = SSL_write (hu_ssl_ssl, buf, len);              // Write plaintext to SSL
    if (bytes_written <= 0) {
      loge ("SSL_write() bytes_written: %d", bytes_written);
      hu_ssl_ret_log (bytes_written);
      hu_ssl_inf_log ();
      hu_aap_stop ();
      return (-1);
    }
    if (bytes_written != len)
      loge ("SSL_write() len: %d  bytes_written: %d  chan: %d", len, bytes_written, chan);
    else if (ena_log_verbo && ena_log_send)
      logd ("SSL_write() len: %d  bytes_written: %d  chan: %d", len, bytes_written, chan);

    int bytes_read = BIO_read (hu_ssl_wm_bio, & enc_buf [4], sizeof (enc_buf) - 4); // Read encrypted from SSL BIO to enc_buf
    if (bytes_read <= 0) {
      loge ("BIO_read() bytes_read: %d", bytes_read);
      hu_aap_stop ();
      return (-1);
    }
    if (ena_log_verbo && ena_log_send)
      logd ("BIO_read() bytes_read: %d", bytes_read);

    int flags = 0x0b;                                                   // Flags = First + Last + Encrypted
    if (chan != 0 && buf [0] == 0) {                                    // If msg_type = 0 - 255
      flags = 0x0f;                                                     // Control
      //logd ("Setting control");
    }

    hu_aap_usb_set (chan, flags, -1, enc_buf, bytes_read + 4);          // -1 for type so encrypted type position is not overwritten !!
    hu_aap_usb_send (enc_buf, bytes_read + 4, iaap_send_tmo);           // Send encrypted data to AA Server

    return (0);
  }

    byte sd_buf [] = {0, 6,        //8, 0};                                            // Svc Disc Rsp = 6
/*
cq  (co[  (str  (str  (str  (str  (int  (str  (str  (str  (str  (boo  (boo    MsgServiceDiscoveryResponse

  co  int (cm (bd (ak (bi (m  (ce (bq (bb (cb (av (cy (ad       co[] a()      MsgAllServices

    cm  (cn[                                                                  MsgSensors
      cn  int                                                   cn[] a()      MsgSensorSourceService  Fix name to MsgSensor

    bd  (int  (int  (f[ (cz[  (boo                                            MsgMediaSinkService
       f  int   int   int                                        f[] a()      MsgAudCfg   See same below
      cz  (int  (int  (int  (int  (int  (int                    cz[] a()      MsgVidCfg

    ak  (int[ (am[  (al[                                                      MsgInputSourceService   int[] = keycodes    Graphics Points ?
      am  int   int                                             am[] a()      TouchScreen width, height
      al  int   int                                             al[] a()      TouchPad    width, height

Audio Config:
  sampleRate
  channelConfig
  audioFormat

public final class MsgMediaSinkService extends k                        // bd/MsgMediaSinkService extends k/com.google.protobuf.nano.MessageNano
{
  public int      a                 = 0;                                // a
  public int      mCodecType        = 1;                                // b
  public int      mAudioStreamType  = 1;                                // c
  public f[]      mAudioStreams     = f.a();                            // f[]:d    a:samplingRate    b:numBits     c:channels
  public cz[]     mCodecs           = cz.a();                           // cz[]:e   b:codecResolution 1=800x480 2=1280x720 3=1920x1080
                                                                                //  c:0/1 for 30/60 fps   d:widthMargin e:heightMargin f:density/fps g: ?
  private boolean f                 = false;                            // f

*/
// D/CAR.GAL ( 3804): Service id=1 type=MediaSinkService { codec type=1 { codecResolution=1 widthMargin=0 heightMargin=0 density=30}}

///*                      //cq/co[]
            // CH 3 SENSORS:
                        0x0A, 4 + 4*1,//co: int, cm/cn[]
                                      0x08, AA_CH_SEN,  0x12, 4*1,
                                                          0x0A, 2,
                                                                    0x08, 11, // SENSOR_TYPE_DRIVING_STATUS 12  */
/*  Requested Sensors: 10, 9, 2, 7, 6:
                        0x0A, 4 + 4*6,     //co: int, cm/cn[]
                                      0x08, AA_CH_SEN,  0x12, 4*6,
                                                          0x0A, 2,
                                                                    0x08, 11, // SENSOR_TYPE_DRIVING_STATUS 12
                                                          0x0A, 2,
                                                                    0x08,  3, // SENSOR_TYPE_RPM            2
                                                          0x0A, 2,
                                                                    0x08,  8, // SENSOR_TYPE_DIAGNOSTICS    7
                                                          0x0A, 2,
                                                                    0x08,  7, // SENSOR_TYPE_GEAR           6
                                                          0x0A, 2,
                                                                    0x08,  1, // SENSOR_TYPE_COMPASS       10
                                                          0x0A, 2,
                                                                    0x08, 10, // SENSOR_TYPE_LOCATION       9   */
            // CH 1 Video Sink:
///*                        
                        0x0A, 4+4+11, 0x08, AA_CH_VID,    // Channel 1

                                      0x1A, 4+11, // Sink: Video
                                                  0x08, 3,    // int (codec type)
                                                  //0x10, 1,    // int (audio stream type)
//                                                  0x1a, 8,    // f        //I44100 = 0xAC44 = 10    10 1  100 0   100 0100  :  -60, -40, 2
                                                                            // 48000 = 0xBB80 = 10    111 0111   000 0000     :  -128, -9, 2
                                                                            // 16000 = 0x3E80 = 11 1110 1   000 0000          :  -128, -6
                                                  0x22, 11,   // cz
                                                              0x08, 1, 0x10, 1, 0x18, 0, 0x20, 0, 0x28, -96, 1,   //0x30, 0,      // res800x480, 30 fps, 0, 0, 160 dpi    0xa0
                                                              //0x08, 1, 0x10, 2, 0x18, 0, 0x20, 0, 0x28, -96, 1,   //0x30, 0,      // res800x480, 60 fps, 0, 0, 160 dpi    0xa0
                                                              //0x08, 2, 0x10, 1, 0x18, 0, 0x20, 0, 0x28, -96, 1,   //0x30, 0,      // res1280x720, 30 fps, 0, 0, 160 dpi    0xa0
                                                              //0x08, 3, 0x10, 1, 0x18, 0, 0x20, 0, 0x28, -96, 1,   //0x30, 0,      // res1920x1080, 30 fps, 0, 0, 160 dpi    0xa0        

///*
            // CH 2 TouchScreen:
                        0x0A, 4+2+6, 0x08, AA_CH_TOU,  // Channel 2

//                                                              0x08, -128, -9, 2,    0x10, 16,   0x18, 2,
                                                  //0x28, 0, //1,   boolean


                                      0x22, 2+6,//+2+16, // ak  Input
                                                  //0x0a, 16,   0x03, 0x54, 0x55, 0x56, 0x57, 0x58, 0x7e, 0x7f,   -47, 1,   -127, -128, 4,    -124, -128, 4,
                                                  0x12,  6,        // no int[], am      // 800 = 0x0320 = 11 0    010 0000 : 32+128(-96), 6
                                                                      // 480 = 0x01e0 = 1 1     110 0000 =  96+128 (-32), 3
                                                              0x08, -96, 6,    0x10, -32, 3,        // 800x480
                                                              //0x08, -128, 10,    0x10, -48, 5,        // 1280x720     0x80, 0x0a   0xd0, 5
                                                              //0x08, -128, 15,      0x10,  -72, 8, // 1920x1080      0x80, 0x0f      0xb8, 8      

//*/                                                                                        
                        0x12, 1, 'A', // Car Manuf
                        0x1A, 1, 'B', // Car Model
                        0x22, 1, 'C', // Car Year
                        0x2A, 1, 'D', // Car Serial
                        0x30, 0,      // driverPosition
                        0x3A, 1, 'E', // HU  Make / Manuf
                        0x42, 1, 'F', // HU  Model
                        0x4A, 1, 'G', // HU  SoftwareBuild
                        0x52, 1, 'H', // HU  SoftwareVersion
                        0x58, 1,//0,//1,       // ? bool (or int )    canPlayNativeMediaDuringVr
                        0x60, 1,//0,//1        // mHideProjectedClock

// 04-22 03:43:38.049 D/CAR.SERVICE( 4306): onCarInfo com.google.android.gms.car.CarInfoInternal[dbId=0,manufacturer=A,model=B,headUnitProtocolVersion=1.1,modelYear=C,vehicleId=null,
// bluetoothAllowed=false,hideProjectedClock=false,driverPosition=0,headUnitMake=E,headUnitModel=F,headUnitSoftwareBuild=G,headUnitSoftwareVersion=H,canPlayNativeMediaDuringVr=false]
    };


  int aa_pro_ctr_a00 (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_ctr_a01 (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_ctr_a02 (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_ctr_a03 (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_ctr_a04 (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_ctr_a05 (int chan, byte * buf, int len) {
    logd ("Service Disc req");
    return (hu_aap_enc_send (chan, sd_buf, sizeof (sd_buf)));
  }
  int aa_pro_ctr_a06 (int chan, byte * buf, int len) {
    return (-1);
  }
  /*int aa_pro_ctr_a07 (int chan, byte * buf, int len) {
    return (-1);
  }*/
  int aa_pro_ctr_a08 (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_ctr_a09 (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_ctr_a0a (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_all_a0b (int chan, byte * buf, int len) {
    logd ("Ping all chan: %d", chan);
    buf [1] = 12;                                                       // Use request buffer for response
    int ret = hu_aap_enc_send (chan, buf, len);                        // Respond with Channel Open OK
    return (ret);
  }
  int aa_pro_ctr_a0c (int chan, byte * buf, int len) {
    return (-1);
  }
  int aa_pro_ctr_a0d (int chan, byte * buf, int len) {                  // Navigation Focus Request
    loge ("Navigation Focus Request: %d", buf [3]);
    buf [1] = 14;                                                       // Use request buffer for response
    buf [3] = 2;  // Focus granted ?
    int ret = hu_aap_enc_send (chan, buf, len);                        // Respond
    return (0);
  }
  int aa_pro_ctr_a0e (int chan, byte * buf, int len) {
    return (-1);
  }
  byte bye_rsp [] = {0, 16, 8, 0};                                      // Byebye rsp: Status 0 = OK
  int aa_pro_all_a0f (int chan, byte * buf, int len) {
    logd ("Byebye all chan: %d", chan);
    int ret = hu_aap_enc_send (chan, bye_rsp, sizeof (bye_rsp));       // Respond with Byebye rsp

    ms_sleep (100);                                                     // Wait for response
    //terminate = 1;

    hu_aap_stop ();

    return (-1);
  }
  int aa_pro_ctr_a10 (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a11 (int chan, byte * buf, int len) {                  // sr:  00000000 00 11 08 01      Microphone voice search usage     sr:  00000000 00 11 08 02
    loge ("Voice Session notification: %d", buf [3]);
    return (0);
  }
  int aa_pro_ctr_a12 (int chan, byte * buf, int len) {                  // Audio Focus Request
    loge ("Audio Focus Request: %d", buf [3]);
    buf [1] = 19;                                                       // Use request buffer for response
    buf [3] = 2;  // Focus granted ?
    int ret = hu_aap_enc_send (chan, buf, len);                        // Respond
    return (0);
  }
  int aa_pro_ctr_a13 (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a14 (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a15 (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a16 (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a17 (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a18 (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a19 (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a1a (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a1b (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a1c (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a1d (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a1e (int chan, byte * buf, int len) {
  }
  int aa_pro_ctr_a1f (int chan, byte * buf, int len) {
  }

  int aa_pro_all_a07 (int chan, byte * buf, int len) {
    logd ("Channel Open all chan: %d", chan);
    byte rsp [] = {0, 8, 8, 0};                                         // Status 0 = OK
    int ret = hu_aap_enc_send (chan, rsp, sizeof (rsp));               // Respond with Channel Open OK
    if (ret || chan != AA_CH_SEN)
      return (ret);
    ms_sleep (20);                                                      // Else if success and channel = sensor...
    byte rspds [] = {0x80, 0x03, 0x6a, 2, 8, 0};                        // Driving Status = 0 = Parked (1 = Moving)
    return (hu_aap_enc_send (chan, rspds, sizeof (rspds)));            // Respond with response
  }
  /*int aa_pro_vid_a07 (int chan, byte * buf, int len) {
    logd ("Channel Open vid");
    byte rsp [] = {0, 8, 8, 0};                                         // Status 0 = OK
    int ret = hu_aap_enc_send (AA_CH_VID, rsp, sizeof (rsp));          // Respond with Channel Open OK
    return (ret);
  }
  int aa_pro_tou_a07 (int chan, byte * buf, int len) {
    logd ("Channel Open tou");
    byte rsp [] = {0, 8, 8, 0};                                         // Status 0 = OK
    int ret = hu_aap_enc_send (AA_CH_TOU, rsp, sizeof (rsp));          // Respond with Channel Open OK
    return (ret);
  }*/

  int aa_pro_all_b00 (int chan, byte * buf, int len) {
    logd ("Video/Media Setup Request all chan: %d", chan);
    byte rsp [] = {0x80, 0x03, 0x08, 2, 0x10, 1, 0x18, 0};//0x1a, 4, 0x08, 1, 0x10, 2};      // 1/2, MaxUnack, int[] 1        2, 0x08, 1};//
    int ret = hu_aap_enc_send (chan, rsp, sizeof (rsp));               // Respond with Config Response
    if (ret || chan != AA_CH_VID)
      return (ret);
    ms_sleep (20);                                                      // Else if success and channel = video...
    byte rsp2 [] = {0x80, 0x08, 0x08, 1, 0x10, 1};                      // 1, 1     VideoFocus gained focusState=0 unsolicited=true
    return (hu_aap_enc_send (chan, rsp2, sizeof (rsp2)));              // Respond with VideoFocus gained/notif focusState=0 unsolicited=true
  }

  int aa_pro_tou_b02 (int chan, byte * buf, int len) {                  // TouchScreen/Input Start Request...    Or "send setup, ch:X" for channel X
    logd ("TouchScreen/Input Start Request tou chan: %d", chan);
    byte rsp [] = {0x80, 0x03, 0x08, 0};
    int ret = hu_aap_enc_send (chan, rsp, sizeof (rsp));               // Respond with Key Binding Response = OK
    return (ret);
  }

  int aa_pro_vid_b01 (int chan, byte * buf, int len) {                  // Sensor Start Request...
    logd ("Ignore Media Start Request chan: %d", chan);
  }
  int aa_pro_sen_b01 (int chan, byte * buf, int len) {                  // Sensor Start Request...
    logd ("Sensor Start Request chan: %d", chan);
    byte rsp [] = {0x80, 0x02, 0x08, 0};
    int ret = hu_aap_enc_send (chan, rsp, sizeof (rsp));               // Respond with Sensor Response
    if (ret)
      return (ret);
    ms_sleep (20);                                                      // Else if success and channel = video...
    byte rsp2 [] = {0x80, 0x03, 106, /*4, 0x0a,*/ 2, 8, 1}; // Driving Status = Stopped
    return (hu_aap_enc_send (chan, rsp2, sizeof (rsp2)));              // Respond with Sensor Batch
  }


  typedef int (* aa_type_ptr_t) (int chan, byte * buf, int len);

  aa_type_ptr_t aa_type_array [4] [3] [32] = {   // 0 - 31, 32768-32799, 65504-65535

// Channel 0 Ctr Control:
    aa_pro_ctr_a00, aa_pro_ctr_a01, aa_pro_ctr_a02, aa_pro_ctr_a03, aa_pro_ctr_a04, aa_pro_ctr_a05, aa_pro_ctr_a06, aa_pro_all_a07, aa_pro_ctr_a08, aa_pro_ctr_a09, aa_pro_ctr_a0a, aa_pro_all_a0b, aa_pro_ctr_a0c, aa_pro_ctr_a0d, aa_pro_ctr_a0e, aa_pro_all_a0f,
    NULL, aa_pro_ctr_a11, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,   //    aa_pro_ctr_a10, aa_pro_ctr_a11, aa_pro_ctr_a12, aa_pro_ctr_a13, aa_pro_ctr_a14, aa_pro_ctr_a15, aa_pro_ctr_a16, aa_pro_ctr_a17, aa_pro_ctr_a18, aa_pro_ctr_a19, aa_pro_ctr_a1a, aa_pro_ctr_a1b, aa_pro_ctr_a1c, aa_pro_ctr_a1d, aa_pro_ctr_a1e, aa_pro_ctr_a1f,
    aa_pro_all_b00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

// Channel 1 Vid Video:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, aa_pro_all_a0b, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    aa_pro_all_b00, aa_pro_vid_b01, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

// Channel 2 Tou TouchScreen:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, aa_pro_all_a0b, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    aa_pro_all_b00, NULL, aa_pro_tou_b02, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,

// Channel 3 Sen Sensor:
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, aa_pro_all_a07, NULL, NULL, NULL, aa_pro_all_a0b, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    aa_pro_all_b00, aa_pro_sen_b01, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
  };

  void iaap_video_decode (byte * buf, int len) {

    byte * q_buf = write_tail_buffer_get (len);                         // Get queue buffer tail to write to     !!! Need to lock until buffer written to !!!!
    if (ena_log_verbo)
      logd ("q_buf: %p  buf: %p  len: %d", q_buf, buf, len);
    if (q_buf == NULL) {
      loge ("Error no q_buf: %p  buf: %p  len: %d", q_buf, buf, len);
      //return;                                                         // Continue in order to write to record file
    }
    else
      memcpy (q_buf, buf, len);                                         // Copy video to queue buffer

    if (record_enable == 0)
      return;

#ifndef NDEBUG
    char * rec_file = "/home/m/dev/hu/aa.h264";
  #ifdef __ANDROID_API__
    rec_file = "/sdcard/aa.h264";
  #endif

    if (record_fd < 0)
      record_fd = open (rec_file, O_RDWR | O_CREAT, S_IRWXU | S_IRWXG | S_IRWXO);
    int written = -77;
    if (record_fd >= 0)
      written = write (record_fd, buf, len);
    logd ("Video written: %d", written);
#endif
  }


  int ack_ctr = 0;
  int iaap_video_process (int type, int flag, byte * buf, int len) {    // Process video packet
// MaxUnack
//loge ("????????????????????? !!!!!!!!!!!!!!!!!!!!!!!!!   !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!   ack_ctr: %d  len: %d", ack_ctr ++, len);
int ret = hu_aap_enc_send (AA_CH_VID, vid_ack, sizeof (vid_ack));      // Respond with ACK (for all fragments ?)
/*
    int ret = 0;
    //if (ack_ctr ++ % 17 == 16)
    if (ack_ctr ++ % 2 == 1)
      loge ("Drop ack to test !!!!!!!!!!!!!!!!!!!!!!!!!   !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!   ack_ctr: %d  len: %d", ack_ctr, len);
    else
      ret = hu_aap_enc_send (AA_CH_VID, vid_ack, sizeof (vid_ack));      // Respond with ACK (for all fragments ?)
//*/
    if (0) {
    }
    else if (flag == 11 && (type == 0 || type == 1) && (buf [10] == 0 && buf [11] == 0 && buf [12] == 0 && buf [13] == 1)) {  // If Not fragmented Video
      iaap_video_decode (& buf [10], len - 10);                                                                               // Decode H264 video
    }
    else if (flag == 9 && (type == 0 || type == 1) && (buf [10] == 0 && buf [11] == 0 && buf [12] == 0 && buf [13] == 1)) {   // If First fragment Video
      memcpy (assy, & buf [10], len - 10);                                                                                    // Len in bytes 2,3 doesn't include total len 4 bytes at 4,5,6,7
      assy_size = len - 10;                                                                                                   // Add to re-assembly in progress
    }
    else if (flag == 11 && type == 1 && (buf [2] == 0 && buf [3] == 0 && buf [4] == 0 && buf [5] == 1)) {                     // If Not fragmented First video config packet
      iaap_video_decode (& buf [2], len - 2);                                                                                 // Decode H264 video
    }
    else if (flag == 8) {                                                                                                     // If Middle fragment Video
      memcpy (& assy [assy_size], buf, len);
      assy_size += len;                                                                                                       // Add to re-assembly in progress
    }
    else if (flag == 10) {                                                                                                    // If Last fragment Video
      memcpy (& assy [assy_size], buf, len);
      assy_size += len;                                                                                                       // Add to re-assembly in progress
      iaap_video_decode (assy, assy_size);                                                                                    // Decode H264 video fully re-assembled
    }
    else
      loge ("Video error type: %d  flag: %d  buf: %p  len: %d", type, flag, buf, len);

    return (0);
  }

  int iaap_msg_process (int chan, int flag, byte * buf, int len) {

    int type = (int) buf [1];
    type += ((int) buf [0] * 256);

    if (ena_log_verbo)
      logd ("iaap_msg_process type: %d  len: %d  buf: %p", type, len, buf);

    int run = 0;
    if (chan == AA_CH_VID && type == 0 || type == 1 || flag == 8 || flag == 9 || flag == 10 ) {      // If Video...
      return (iaap_video_process (type, flag, buf, len));
    }
    else if (type >= 0 && type <= 31)
      run = 0;
    else if (type >= 32768 && type <= 32799)
      run = 1;
    else if (type >= 65504 && type <= 65535)
      run = 2;
    else {
      loge ("Unknown type: %d", type);
      return (0);
    }

    int prot_func_ret = -1;
    int num = type & 0x1f;
    aa_type_ptr_t func = aa_type_array [chan] [run] [num];
    if (func)
      prot_func_ret = (* func) (chan, buf, len);
    else
      loge ("No func chan: %d  run: %d  num: %d", chan, run, num);

    return (prot_func_ret);
  }

  int hu_aap_stop () {                                                  // Sends Byebye, then stops USB/ACC/OAP
    // Send Byebye
    iaap_state = hu_STATE_STOPPIN;
    logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));

    int ret = hu_usb_stop ();                                           // Stop USB/ACC/OAP
    iaap_state = hu_STATE_STOPPED;
    logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));

    return (ret);
  }

  int hu_aap_start (byte ep_in_addr, byte ep_out_addr) {                // Starts USB/ACC/OAP, then AA protocol w/ VersReq(1), SSL handshake, Auth Complete

    if (iaap_state == hu_STATE_STARTED) {
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (0);
    }

    iaap_state = hu_STATE_STARTIN;
    logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));

    int ret = hu_usb_start (ep_in_addr, ep_out_addr);                   // Start USB/ACC/OAP
    if (ret) {
      iaap_state = hu_STATE_STOPPED;
      logd ("  SET: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (ret);                                                     // Done if error
    }

    byte vr_buf [] = {0, 3, 0, 6, 0, 1, 0, 1, 0, 1};                    // Version Request
    ret = hu_aap_usb_set (0, 3, 1, vr_buf, sizeof (vr_buf));
    ret = hu_aap_usb_send (vr_buf, sizeof (vr_buf), 1000);              // Send Version Request
    if (ret < 0) {
      loge ("Version request send ret: %d", ret);
      hu_aap_stop ();
      return (-1);
    }  

    byte buf [DEFBUF] = {0};
    errno = 0;
    ret = hu_aap_usb_recv (buf, sizeof (buf), 1000);                    // Get Rx packet from USB:    Wait for Version Response
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
    ret = hu_aap_usb_set (0, 3, 4, ac_buf, sizeof (ac_buf));
    ret = hu_aap_usb_send (ac_buf, sizeof (ac_buf), 1000);              // Auth Complete, must be sent in plaintext
    if (ret < 0) {
      loge ("hu_aap_usb_send() ret: %d", ret);
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
http://stackoverflow.com/questions/22753221/openssl-read-write-handshake-data-with-memory-bio
http://www.roxlu.com/2014/042/using-openssl-with-memory-bios
https://www.openssl.org/docs/ssl/SSL_read.html
http://blog.davidwolinsky.com/2009/10/memory-bios-and-openssl.html
http://www.cisco.com/c/en/us/support/docs/security-vpn/secure-socket-layer-ssl/116181-technote-product-00.html
*/

  int iaap_recv_dec_process (int chan, int flags, byte * buf, int len) {// Decrypt & Process 1 received encrypted message

    int bytes_written = BIO_write (hu_ssl_rm_bio, buf, len);           // Write encrypted to SSL input BIO
    if (bytes_written <= 0) {
      loge ("BIO_write() bytes_written: %d", bytes_written);
      return (-1);
    }
    if (bytes_written != len)
      loge ("BIO_write() len: %d  bytes_written: %d  chan: %d", len, bytes_written, chan);
    else if (ena_log_verbo)
      logd ("BIO_write() len: %d  bytes_written: %d  chan: %d", len, bytes_written, chan);

    errno = 0;
    int ctr = 0;
    int max_tries = 1;  // Higher never works
    int bytes_read = -1;
    while (bytes_read <= 0 && ctr ++ < max_tries) {
      bytes_read = SSL_read (hu_ssl_ssl, dec_buf, sizeof (dec_buf));   // Read decrypted to decrypted rx buf
      if (bytes_read <= 0) {
        loge ("ctr: %d  SSL_read() bytes_read: %d  errno: %d", ctr, bytes_read, errno);
        hu_ssl_ret_log (bytes_read);
        ms_sleep (1);
      }
      //logd ("ctr: %d  SSL_read() bytes_read: %d  errno: %d", ctr, bytes_read, errno);
    }

    if (bytes_read <= 0) {
      loge ("ctr: %d  SSL_read() bytes_read: %d  errno: %d", ctr, bytes_read, errno);
      hu_ssl_ret_log (bytes_read);
      return (-1);                                                      // Fatal so return error and de-initialize; Should we be able to recover, if USB data got corrupted ??
    }
    if (ena_log_verbo)
      logd ("ctr: %d  SSL_read() bytes_read: %d", ctr, bytes_read);

    int rmv = 0;
#ifndef NDEBUG
//    if (chan != AA_CH_VID)                                            // If not video...
      rmv = hu_aad_dmp ("DR: ", 2, dec_buf, bytes_read);                // Dump decrypted AA
#endif

    int prot_func_ret = iaap_msg_process (chan, flags, dec_buf, bytes_read);   // Process decrypted AA protocol message
    return (0);//prot_func_ret);
  }

  int hu_aap_recv_process () {                                          // Process 1 encrypted receive message set, from reading encrypted message from USB to reacting to decrypted message
    if (iaap_state != hu_STATE_STARTED && iaap_state != hu_STATE_STARTIN) {   // Need to recv when starting) {
      loge ("CHECK: iaap_state: %d (%s)", iaap_state, state_get (iaap_state));
      return (-1);
    }
    byte * buf = rx_buf;
    int ret = 0;
    errno = 0;
    int len_remain = hu_aap_usb_recv (rx_buf, sizeof (rx_buf), iaap_recv_tmo); // Get Rx packet from USB
                                                                        // Length remaining for all sub-packets plus 4/8 byte headers
    if (len_remain == 0) {                                              // If no data, then done w/ no data
      return (0);
    }
    if (len_remain < 0) {
      loge ("Recv len_remain: %d", len_remain);
      hu_aap_stop ();
      return (-1);
    }  

    int ctr = 0;     
    while (len_remain > 0) {                                            // While length remaining,... Process Rx packet:

      //if (ctr ++ > 0)   // Multiple subpackets work OK now
      //  loge ("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

      if (ena_log_verbo) {
        logd ("Recv while (len_remain > 0): %d", len_remain);
#ifndef NDEBUG
        hex_dump ("LR: ", 16, buf, len_remain);
#endif
      }

      /*if (len_remain % 16384 == 0) {    // Incomplete 16K packets don't happen now with 64K USB Rx buffers
        loge ("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        loge ("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        loge ("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      }*/

      int chan = (int) buf [0];                                         // Channel
      int flags = buf [1];                                              // Flags

      int len = (int) buf [3];                                          // Encoded length of bytes to be decrypted (minus 4/8 byte headers)
      len += ((int) buf [2] * 256);

      int type = (int) buf [5];                                         // Message Type (or post handshake, mostly indicator of SSL encrypted data)
      type += ((int) buf [4] * 256);

      len_remain -= 4;                                                  // Length starting at byte 4: Unencrypted Message Type or Encrypted data start
      buf += 4;                                                         // buf points to data to be decrypted
      if (chan == AA_CH_VID && flags == 9) {                            // If First fragment Video... Packet is encrypted so we can't get the real msg_type or check for 0, 0, 0, 1
        int total_size = (int) buf [3];
        total_size += ((int) buf [2] * 256);
        total_size += ((int) buf [1] * 256 * 256);
        total_size += ((int) buf [0] * 256 * 256 * 256);

        if (max_assy_size < total_size)                                // Seen as big as 151 Kbytes so far
          max_assy_size = total_size;                                  // See: jni/hu_aap.c:  byte assy [65536 * 16] = {0}; // up to 1 megabyte
        loge ("First fragment total_size: %d  max_assy_size: %d", total_size, max_assy_size); // & jni/hu_uti.c:  #define gen_buf_BUFS_SIZE    65536 * 4      // Up to 256 Kbytes
                                                                        // & src/ca/yyx/hu/hu_tro.java:    byte [] assy = new byte [65536 * 16];
                                                                        // & src/ca/yyx/hu/hu_tra.java:      res_buf = new byte [65536 * 4];
        len_remain -= 4;                                                // Remove 4 length bytes inserted into first video fragment
        buf += 4;
      }
      if (flags & 0x08 != 0x08) {
        loge ("NOT ENCRYPTED !!!!!!!!! len_remain: %d  len: %d  buf: %p  chan: %d  flags: 0x%x  type: %d", len_remain, len, buf, chan, flags, type);
        hu_aap_stop ();
        return (-1);
      }
      if (len_remain < len) {
        int need = len - len_remain;
        logd ("len_remain: %d < len: %d  need: %d  !!!!!!!!!", len_remain, len, need);
        //ms_sleep (50);  // ?? Wait a bit for data to come ??
        int need_ret = hu_aap_usb_recv (& buf [len_remain], need, iaap_recv_tmo); // Get Rx packet from USB
                                                                        // Length remaining for all sub-packets plus 4/8 byte headers
        if (need_ret != need) {
          loge ("Recv need_ret: %d", need_ret);
          hu_aap_stop ();
          return (-1);
        }
        len_remain = len;
      }


      ret = iaap_recv_dec_process (chan, flags, buf, len);              // Decrypt & Process 1 received encrypted message

      if (ret < 0) {
        loge ("Error iaap_recv_dec_process() ret: %d  len_remain: %d  len: %d  buf: %p  chan: %d  flags: 0x%x  type: %d", ret, len_remain, len, buf, chan, flags, type);
        hu_aap_stop ();
        return (ret);  
      }
      logd ("OK iaap_recv_dec_process() ret: %d  len_remain: %d  len: %d  buf: %p  chan: %d  flags: 0x%x  type: %d", ret, len_remain, len, buf, chan, flags, type);

      len_remain -= len;                                                // Consume processed sub-packet and advance to next, if any
      buf += len;
    }

    return (ret);   // Return from the last; should be 0
  }
/*
*/
