
  #define LOGTAG "hu_jni"
  #include "hu_uti.h"
  #include "hu_aap.h"


  #include <stdio.h>
  #include <errno.h>
  #include <fcntl.h>
  #include <sys/stat.h>
  #include <sys/resource.h>

  #include <jni.h>


  char * copyright = "Copyright (c) 2011-2015 Michael A. Reid. All rights reserved.";

  int aap_state_start_error = 0;
  int aap_state_starts  = 0;
  int mic_running=0;
  JNIEnv * myenv = NULL;
  jobject jobj;
  jclass clazz;
  jmethodID sendmethod;
  jmethodID recvmethod;
  
   int hu_usb_recv (byte * buf, int len, int tmo) {
    //int ret = iusb_bulk_transfer (iusb_ep_in, buf, len, 250);       // milli-second timeout
	 jint jlen=len;
	 jint jtmo=tmo;
	
	  jbyte * rawDataRead = (*myenv)->NewByteArray(myenv, len);
	  int ret = (*myenv)->CallIntMethod(myenv, jobj,recvmethod, rawDataRead,jlen,jtmo);
	  (*myenv)->GetByteArrayRegion(myenv, rawDataRead, 0,len,buf);
	  
  //memcpy(buffer, carr, bytes);
  //(*env)->DeleteLocalRef(env, rawDataRead);
	
	//jbyteArray arr = (jbyteArray) (*myenv)->CallObjectMethod(myenv, jobj, recvmethod, jlen);
	/*
	if(arr != NULL)
    {
        ret = (*myenv)->GetArrayLength(myenv, arr);
        buf = (*myenv)->GetByteArrayElements(myenv, arr, NULL);
    }

    (*myenv)->ReleaseByteArrayElements(myenv, arr, ret, 0);
	
	*/
   return(ret);
  }

  int hu_usb_send (byte * buf, int len, int tmo) {
    //int ret = iusb_bulk_transfer (iusb_ep_out, buf, len, 250);      // milli-second timeout
	  jint jtmo=tmo;
	 jint jlen=len;
     jbyteArray jb;
	 jb=(*myenv)->NewByteArray(myenv, len);
	 (*myenv)->SetByteArrayRegion(myenv, jb, 0, len, buf);

    int ret=(*myenv)->CallIntMethod(myenv, jobj, sendmethod, jb, jlen, jtmo);
    //(*myenv)->ReleaseByteArrayElements(myenv, buf, jbuf, 0);

   
	//int ret=0;
    return (ret);
  }

  int jni_aa_cmd (int cmd_len, char * cmd_buf, int res_max, char * res_buf, long myip_string, int transport_audio, int hires) {
    // if (ena_log_extra || cmd_len >= 1)
       //logd ("trans audop: %d, cmd_len: %d  cmd_buf %p  res_max: %d  res_buf: %p, ip_long: %lu", transport_audio, cmd_len, cmd_buf, res_max, res_buf, myip_string);
    int res_len = 0;
    int ret = 0;
	
	

    int vid_bufs = vid_buf_buf_tail - vid_buf_buf_head;
    int aud_bufs = aud_buf_buf_tail - aud_buf_buf_head;
    int med_bufs = med_buf_buf_tail - med_buf_buf_head;
   // int med_bufs = 0;


	if (cmd_buf != NULL && cmd_buf [0] == 121) {        // If onCreate() hu_tra:transport_start()
      //byte ep_in_addr  = cmd_buf [1];
      //byte ep_out_addr = cmd_buf [2];                                   // Get endpoints passed
	  
		vid_bufs=0;
		//aud_bufs=0;
		reset_vid_buf();
		
	
     // ret = hu_aap_start (ep_in_addr, ep_out_addr, myip_string, transport_audio, hires);                     // Start USB/ACC/OAP, AA Protocol
	 hex_dump ("CMD_BUF: ", 128, cmd_buf, cmd_len);
      ret = hu_aap_start (cmd_len, cmd_buf, myip_string, transport_audio, hires);                     // Start USB/ACC/OAP, AA Protocol
	//hex_dump (" W/   BluetoothMac: ", 256, & cmd_buf[3], 17);
      aap_state_starts ++;                                              // Count error starts too
      if (ret == 0) {
        logd ("hu_aap_start() success aap_state_starts: %d", aap_state_starts);
      }
      else {
        loge ("hu_aap_start() error aap_state_starts: %d", aap_state_starts);
        aap_state_start_error = 1;
        return (-1);
      }
    }

/* Functions                code                            Params:
    Transport Start         hu_aap_start()                  USB EPs
    Poll/Handle 1 Rcv Msg   hu_aap_recv_process()           -
    Send:
      Send Mic              hu_aap_enc_send()               mic data
      Send Touch            hu_aap_enc_send()               touch data
      Send Ctrl             hu_aap_enc_send()/hu_aap_stop() ctrl data

  Returns:
    Audio Mic Start/Stop
    Audio Out Start/Stop
    Video
    Audio
*/
    else if (cmd_buf != NULL && cmd_len >= 4) {                         // If encrypted command to send...
      int chan = 0;//cmd_buf [0];
	int dont_send=0;
	 if ( cmd_buf[0] == 3 || cmd_buf[0] == 8 || cmd_buf[0] == 9 || cmd_buf[0] == 2  || cmd_buf[0] == 14  || cmd_buf[0] == 13  || cmd_buf[0] == 12 || cmd_buf[0] == 11 || cmd_buf[0] == 10) {
         hu_aap_enc_send (cmd_buf[0],& cmd_buf [1], cmd_len - 1);
         dont_send=1;
		 //hex_dump ("HEX_DUMP: ", 640,cmd_buf , cmd_len);
		 return(0);
	 }
	 else if (cmd_buf[0]==20) {
		 dont_send=1;
		 hu_aap_tra_send(& cmd_buf [1], cmd_len - 1,250);
		 return(0);
	 }
	 
	 else if (cmd_buf[0]==7)                                               // If Microphone audio, but can also be GPS....
	 {
		  chan = AA_CH_MIC;	
		  ret = hu_aap_mic_get ();
		if (ret == 1) 
		{
			mic_running=0;
			return (ret);                                                   // If mic function was stopped on AA, DON'T SEND ANYTHING
			
		}
		
	 }	 
      else if (cmd_len > 6 && cmd_buf [4] == 0x80 && cmd_buf [5] == 1)  // If Touch event...
        chan = AA_CH_TOU;
		
		
 

	 
	  else                                                              // If Byebye or other control packet...
        chan = AA_CH_CTR;
      if (chan != cmd_buf [0]) {
        loge ("chan: %d != cmd_buf[0]: %d", chan, cmd_buf [0]);
        chan = cmd_buf [0];
      }

      //hex_dump ("JNITX: ", 16, cmd_buf, cmd_len);
	if (dont_send==0)
	{ 	
		ret = hu_aap_enc_send (chan, & cmd_buf [4], cmd_len - 4);         // Send
		
	}

      if (cmd_buf != NULL && cmd_len  >= 8 && cmd_buf [5] == 15) {      // If byebye...
        logd ("Byebye");
        ms_sleep (100);
        ret = hu_aap_stop ();
		return(-9);
      }
	return(ret);
    }
   // else  {    //If it's the audio thread poll new data
	
		
		
   
	
	ret = hu_aap_recv_process ();
	

   if (vid_bufs <= 0 && aud_bufs <= 0 && med_bufs <=0) {                               // If no queued audio or video...
   
     										//Only get data if we have nothing queued
		
		if (ret < 0) {
		return (ret);                                                     // If error then done w/ error
		}
		
	if (mic_running==0) {
      ret = hu_aap_mic_get ();
      if (ret == 2) {// && ret <= 2) {                                  // If microphone start (2) or stop (1)...
	   mic_running=1;
	   return (ret);                                                   // Done w/ mic notification: start (2) or stop (1)
       }
	  }
                                                                        // Else if no microphone state change...
      
      if (hu_aap_out_get (AA_CH_AUD) >= 0)                              // If audio out stop...
        return (3);                                                     // Done w/ audio out notification 0
      if (hu_aap_out_get (AA_CH_AU1) >= 0)                              // If audio out stop...
        return (4);                                                     // Done w/ audio out notification 1
      if (hu_aap_out_get (AA_CH_AU2) >= 0)                              // If audio out stop...
        return (5);                                                     // Done w/ audio out notification 2
    }

    
	
	byte * dq_buf = NULL; 
	dq_buf = aud_read_head_buf_get (& res_len);
	
	if (dq_buf == NULL)
	{
		dq_buf = vid_read_head_buf_get (& res_len);    
			if (dq_buf == NULL)    
					{
						dq_buf = med_read_head_buf_get (& res_len); 
						  if (dq_buf!=NULL)
						  {
							
							memcpy (res_buf, dq_buf, res_len);
							return(res_len);
						  }
							else
							{
								return(0);
							}
						
					}
					else
					{
						res_buf[0]=3;
						
						memcpy (&res_buf[1], dq_buf, res_len);
						return(res_len+1);
					}
	}
	else
	{
		memcpy (res_buf, dq_buf, res_len);
		return(res_len);
	}


  }


  JNIEXPORT jint Java_gb_xxy_hr_new_1hu_1tra_native_1aa_1cmd (JNIEnv * env, jobject thiz, jint cmd_len, jbyteArray cmd_buf, jint res_len, jbyteArray res_buf, jlong myip_string, jint transport_audio, jint hires) {
	  
//	char *nativeString = (*env)->GetStringUTFChars(env, myip_string, 0);

   // use your string

 //  (*env)->ReleaseStringUTFChars(env, myip_string, nativeString);
jobj=thiz;
if (myenv!=env)
{
	 myenv=env;
	 
	 clazz = (*env)->GetObjectClass(env, thiz);
    //  MyJavaClass method:  private void addData(byte[] data)
    sendmethod = (*env)->GetMethodID(env, clazz, "usbsend", "([BII)I");
    recvmethod = (*env)->GetMethodID(env, clazz, "usbrecv", "([BII)I");
}
    if (ena_log_extra)
      logd ("cmd_buf: %p  cmd_len: %d  res_buf: %p  res_len: %d", cmd_buf, cmd_len,  res_buf, res_len);

    jbyte * aa_cmd_buf          = NULL;
    jbyte * aa_res_buf          = NULL;

    if (cmd_buf == NULL) {
      loge ("cmd_buf == NULL");
      return (-1);
    }

    // if (res_buf == NULL) {
      // loge ("res_buf == NULL");
      // return (-1);
    // }

    aa_cmd_buf = (*env)->GetByteArrayElements (env, cmd_buf, NULL);
    aa_res_buf = (*env)->GetByteArrayElements (env, res_buf, NULL);

    int len = jni_aa_cmd (cmd_len, aa_cmd_buf, res_len, aa_res_buf, myip_string, transport_audio, hires);

    if (cmd_buf != NULL)
      (*env)->ReleaseByteArrayElements (env, cmd_buf, aa_cmd_buf, 0);

    if (res_buf != NULL)
      (*env)->ReleaseByteArrayElements (env, res_buf, aa_res_buf, 0);

    return (len);
  }

