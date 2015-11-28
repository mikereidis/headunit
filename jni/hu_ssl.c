
  #define LOGTAG "hu_ssl"
  #include "hu_uti.h"
  #include "hu_aap.h"

  #include <openssl/bio.h>
  #include <openssl/ssl.h>
  #include <openssl/err.h>
  #include <openssl/pem.h>
  #include <openssl/x509.h>
  #include <openssl/x509_vfy.h>

  SSL_METHOD  * hu_ssl_method  = NULL;
  SSL_CTX     * hu_ssl_ctx     = NULL;
  SSL         * hu_ssl_ssl     = NULL;
  BIO         * hu_ssl_rm_bio  = NULL;
  BIO         * hu_ssl_wm_bio  = NULL;

  #define MR_SSL_INTERNAL   // For certificate and private key only
  #include "hu_ssl.h"

    // Code:

  void hu_ssl_inf_log () {
    //logd ("SSL_is_init_finished(): %d", SSL_is_init_finished (hu_ssl_ssl));

    const char * ssl_state_string_long = SSL_state_string_long (hu_ssl_ssl);   // "SSLv3 write client hello B"
    logd ("ssl_state_string_long: %s", ssl_state_string_long);

    const char * ssl_version = SSL_get_version (hu_ssl_ssl);                   // "TLSv1.2"
    logd ("ssl_version: %s", ssl_version);

    const SSL_CIPHER * ssl_cipher = SSL_get_current_cipher (hu_ssl_ssl);
    const char * ssl_cipher_name = SSL_CIPHER_get_name (ssl_cipher);    // "(NONE)"
    logd ("ssl_cipher_name: %s", ssl_cipher_name);
  }

  void hu_ssl_ret_log (int ret) {
    int ssl_err = SSL_get_error (hu_ssl_ssl, ret);
    char * err_str = "";

    switch (ssl_err) {
      case SSL_ERROR_NONE:              err_str = ("");                      break;
      case SSL_ERROR_ZERO_RETURN:       err_str = ("Error Zero Return");     break;
      case SSL_ERROR_WANT_READ:         err_str = ("Error Want Read");       break;
      case SSL_ERROR_WANT_WRITE:        err_str = ("Error Want Write");      break;
      case SSL_ERROR_WANT_CONNECT:      err_str = ("Error Want Connect");    break;
      case SSL_ERROR_WANT_ACCEPT:       err_str = ("Error Want Accept");     break;
      case SSL_ERROR_WANT_X509_LOOKUP:  err_str = ("Error Want X509 Lookup");break;
      case SSL_ERROR_SYSCALL:           err_str = ("Error Syscall");         break;
      case SSL_ERROR_SSL:               err_str = ("Error SSL");             break;
      default:                          err_str = ("Error Unknown");         break;
    }

    if (strlen (err_str) == 0)
      logd ("ret: %d  ssl_err: %d (Success)", ret, ssl_err);
    else
      loge ("ret: %d  ssl_err: %d (%s)", ret, ssl_err, err_str);
  }

#define HU_USB_ERROR
//#ifdef __i386__
//#ifdef __arm__

  int hu_ssl_handshake () {

    int                 ret;
    BIO               * cert_bio = NULL;
    BIO               * pkey_bio = NULL;
#ifdef HU_USB_ERROR
    SSL_load_error_strings ();                                          // Before or after init ?
    ERR_load_BIO_strings ();
    ERR_load_crypto_strings ();
  #ifdef __arm__
    ERR_load_SSL_strings ();
  #endif
#endif
    ret = SSL_library_init ();                                          // Init
    logd ("SSL_library_init ret: %d", ret);
    if (ret != 1) {                                                     // Always returns "1", so it is safe to discard the return value.
      loge ("SSL_library_init() error");
      return (-1);
    }
#ifdef HU_USB_ERROR
    SSL_load_error_strings ();                                          // Before or after init ?
    ERR_load_BIO_strings ();
    ERR_load_crypto_strings ();
  #ifdef __arm__
    ERR_load_SSL_strings ();
  #endif
#endif

  #ifdef __arm__
    OPENSSL_add_all_algorithms_noconf ();                               // Add all algorithms, without using config file
  #endif

    ret = RAND_status ();                                               // 1 if the PRNG has been seeded with enough data, 0 otherwise.
    logd ("RAND_status ret: %d", ret);
    if (ret != 1) {
      loge ("RAND_status() error");
      return (-1);
    }

    //CRYPTO_set_locking_callback (locking_function);

    cert_bio = BIO_new_mem_buf (cert_buf, sizeof (cert_buf));           // Read only memory BIO for certificate
    pem_password_cb * ppcb1 = NULL;
    void * u1 = NULL;
    X509 * x509 = NULL;
    X509 * x509_cert = PEM_read_bio_X509_AUX (cert_bio, & x509, ppcb1, u1);
    if (x509_cert == NULL) {
      loge ("read_bio_X509_AUX() error");
      return (-1);
    }
    logd ("PEM_read_bio_X509_AUX() x509_cert: %p", x509_cert);
    ret = BIO_free (cert_bio);
    if (ret != 1)
      loge ("BIO_free(cert_bio) ret: %d", ret);
    else
      logd ("BIO_free(cert_bio) ret: %d", ret);

    pkey_bio = BIO_new_mem_buf (pkey_buf, sizeof (pkey_buf));           // Read only memory BIO for private key
    pem_password_cb * ppcb2 = NULL;
    void * u2 = NULL;
      // Read a private key from a BIO using a pass phrase callback:    key = PEM_read_bio_PrivateKey(bp, NULL, pass_cb,  "My Private Key");
      // Read a private key from a BIO using the pass phrase "hello":   key = PEM_read_bio_PrivateKey(bp, NULL, 0,        "hello"); 
    EVP_PKEY * priv_key_ret = NULL;
    EVP_PKEY * priv_key = PEM_read_bio_PrivateKey (pkey_bio, & priv_key_ret, ppcb2, u2);
    if (priv_key == NULL) {
      loge ("PEM_read_bio_PrivateKey() error");
      return (-1);
    }
    logd ("PEM_read_bio_PrivateKey() priv_key: %p", priv_key);
    ret = BIO_free (pkey_bio);
    if (ret != 1)
      loge ("BIO_free(pkey_bio) ret: %d", ret);
    else
      logd ("BIO_free(pkey_bio) ret: %d", ret);

    hu_ssl_method = (SSL_METHOD *) TLSv1_2_client_method ();
    if (hu_ssl_method == NULL) {
      loge ("TLSv1_2_client_method() error");
      return (-1);
    }
    logd ("TLSv1_2_client_method() hu_ssl_method: %p", hu_ssl_method);

    hu_ssl_ctx = SSL_CTX_new (hu_ssl_method);
    if (hu_ssl_ctx == NULL) {
      loge ("SSL_CTX_new() error");
      return (-1);
    }
    logd ("SSL_CTX_new() hu_ssl_ctx: %p", hu_ssl_ctx);

    ret = SSL_CTX_use_certificate (hu_ssl_ctx, x509_cert);
    if (ret != 1)
      loge ("SSL_CTX_use_certificate() ret: %d", ret);
    else
      logd ("SSL_CTX_use_certificate() ret: %d", ret);

    ret = SSL_CTX_use_PrivateKey (hu_ssl_ctx, priv_key);
    if (ret != 1)
      loge ("SSL_CTX_use_PrivateKey() ret: %d", ret);
    else
      logd ("SSL_CTX_use_PrivateKey() ret: %d", ret);
/*
    int cmd1 = 0;
    long larg1 = 0;
    void * parg1 = NULL;
    ret = SSL_CTX_ctrl (hu_ssl_ctx, cmd1, larg1, parg1);
    if (ret != 1) {
      loge ("SSL_CTX_ctrl() ret: %d", ret);
      //return (-1);
    }
    else
      logd ("SSL_CTX_ctrl() ret: %d", ret);
*/

    //SSL_CTX_set_options (hu_ssl_ctx, SSL_OP_SINGLE_DH_USE);

    // Must do all CTX setup before SSL_new() !!
    hu_ssl_ssl = SSL_new (hu_ssl_ctx);
    if (hu_ssl_ssl == NULL) {
      loge ("SSL_new() hu_ssl_ssl: %p", hu_ssl_ssl);
      return (-1);
    }
    logd ("SSL_new() hu_ssl_ssl: %p", hu_ssl_ssl);

    ret = SSL_check_private_key (hu_ssl_ssl);
    if (ret != 1) {
      loge ("SSL_check_private_key() ret: %d", ret);
      return (-1);
    }
    logd ("SSL_check_private_key() ret: %d", ret);


    hu_ssl_rm_bio = BIO_new (BIO_s_mem ());
    if (hu_ssl_rm_bio == NULL) {
      loge ("BIO_new() hu_ssl_rm_bio: %p", hu_ssl_rm_bio);
      return (-1);
    }
    logd ("BIO_new() hu_ssl_rm_bio: %p", hu_ssl_rm_bio);

    hu_ssl_wm_bio = BIO_new (BIO_s_mem ());
    if (hu_ssl_wm_bio == NULL) {
      loge ("BIO_new() hu_ssl_wm_bio: %p", hu_ssl_wm_bio);
      return (-1);
    }
    logd ("BIO_new() hu_ssl_wm_bio: %p", hu_ssl_wm_bio);

    SSL_set_bio (hu_ssl_ssl, hu_ssl_rm_bio, hu_ssl_wm_bio);                                  // Read from memory, write to memory

BIO_set_write_buf_size (hu_ssl_rm_bio, DEFBUF);                             // BIO_ctrl() API to increase the buffer size.
BIO_set_write_buf_size (hu_ssl_wm_bio, DEFBUF);

    SSL_set_connect_state (hu_ssl_ssl);                                        // Set ssl to work in client mode

    SSL_set_verify (hu_ssl_ssl, SSL_VERIFY_NONE, NULL);

/*
    X509_STORE * x509_store = X509_STORE_new ();
    if (x509_store == NULL) {
      loge ("X509_STORE_new() x509_store: %p", x509_store);
      return (-1);
    }
    logd ("X509_STORE_new() x509_store: %p", x509_store);

    ret = X509_STORE_add_cert (x509_store, x509_cert);
    if (ret != 1) {
      loge ("X509_STORE_add_cert() ret: %d", ret);
      return (-1);
    }
    logd ("X509_STORE_add_cert() ret: %d", ret);

    int store_flags = 0;
    ret = X509_STORE_set_flags (x509_store, store_flags);
    if (ret != 1) {
      loge ("X509_STORE_set_flags() ret: %d", ret);
//      return (-1);
    }
    logd ("X509_STORE_set_flags() ret: %d", ret);

    //int have_error_or_done = 1;//0;
//*/

    byte hs_buf [DEFBUF] = {0};
    int hs_ctr  = 0;

    int hs_finished = 0;//SSL_is_init_finished (hu_ssl_ssl)

    while (! hs_finished && hs_ctr ++ < 2) {

      ret = SSL_do_handshake (hu_ssl_ssl);                             // Do current handshake step processing
      logd ("SSL_do_handshake() ret: %d  hs_ctr: %d", ret, hs_ctr);

      if (ena_log_verbo || (SSL_get_error (hu_ssl_ssl, ret) != SSL_ERROR_WANT_READ)) {
        hu_ssl_ret_log (ret);
        hu_ssl_inf_log ();
      }

      ret = BIO_read (hu_ssl_wm_bio, & hs_buf [6], sizeof (hs_buf) - 6);         // Read from the BIO Client request: Hello/Key Exchange
      if (ret <= 0) {
        loge ("BIO_read() HS client req ret: %d", ret);
        return (-1);
      }
      logd ("BIO_read() HS client req ret: %d", ret);
      int len = ret + 6;
      ret = hu_aap_tra_set (AA_CH_CTR, 3, 3, hs_buf, len);              // chan:0/AA_CH_CTR   flags:first+last    msg_type:SSL
      ret = hu_aap_tra_send (hs_buf, len, 2000);                       // Send Client request to AA Server
      if (ret <= 0 || ret != len) {
        loge ("hu_aap_tra_send() HS client req ret: %d  len: %d", ret, len);
      }      

      ret = hu_aap_tra_recv (hs_buf, sizeof (hs_buf), 2000);           // Get Rx packet from Transport: Receive Server response: Hello/Change Cipher
      if (ret <= 0) {                                                   // If error, then done w/ error
        loge ("HS server rsp ret: %d", ret);
        return (-1);
      }  
      logd ("HS server rsp ret: %d", ret);

      ret = BIO_write (hu_ssl_rm_bio, & hs_buf [6], ret - 6);          // Write to the BIO Server response
      if (ret <= 0) {
        loge ("BIO_write() server rsp ret: %d", ret);
        return (-1);
      }
      logd ("BIO_write() server rsp ret: %d", ret);
      //hs_finished = SSL_is_init_finished (hu_ssl_ssl);
      //logd ("hs_finished: %d", hs_finished);
    }

    hs_finished = 1;    // !!!! SSL_is_init_finished() does not work for some reason !!!

    if (! hs_finished) {
      loge ("Handshake did not finish !!!!");
      return (-1);
    }

    return (0);

  }

/*
    X509_STORE_CTX * x509_store_ctx = X509_STORE_CTX_new ();

    X509 * peer_cert = SSL_get_peer_certificate (hu_ssl_ssl);
    STACK_OF(X509) * stackof_x509 = SSL_get_peer_cert_chain (hu_ssl_ssl);

    ret = X509_STORE_CTX_init (x509_store_ctx, x509_store, x509_cert, stackof_x509);
    logd ("X509_STORE_CTX_init ret: %d", ret);

    X509_STORE_CTX_set_chain (x509_store_ctx, stackof_x509);

    unsigned long time_flags = 0;
    time_t curr_time = time (NULL);
    X509_STORE_CTX_set_time (x509_store_ctx, time_flags, curr_time);

    ret = X509_verify_cert (x509_store_ctx);
    logd ("X509_verify_cert() ret: %d", ret);

    ret = X509_STORE_CTX_get_error (x509_store_ctx);
    logd ("X509_STORE_CTX_get_error() ret: %d", ret);

    const char * ver_cert_err = X509_verify_cert_error_string (ret);
    logd ("X509_verify_cert_error_string() ver_cert_err: %s", ver_cert_err);

    X509_STORE_CTX_cleanup (x509_store_ctx);
    X509_STORE_CTX_free (x509_store_ctx);



    int have_error_or_done = 1;//0;
    if (have_error_or_done) {    
      BIO_free_all (hu_ssl_rm_bio);  // Free the bio and all other BIOs in the list, walking the bio->next_bio list
      BIO_free_all (hu_ssl_wm_bio);  // Free the bio and all other BIOs in the list, walking the bio->next_bio list
      //BIO_free_all (wf_bio);  // Free the bio and all other BIOs in the list, walking the bio->next_bio list
      SSL_free (hu_ssl_ssl);
      SSL_CTX_free (hu_ssl_ctx);
      X509_STORE_free (x509_store);
      X509_free (x509_cert);
      EVP_PKEY_free (priv_key);

      ret = CONF_modules_free ();
      logd ("CONF_modules_free() ret: %d", ret);

      ret = ENGINE_cleanup ();
      logd ("ENGINE_cleanup() ret: %d", ret);

      int all = 0;  //  If all is set to 0 only modules loaded from DSOs will be unloads. If all is 1 all modules, including builtin modules will be unloaded.
      ret = CONF_modules_unload (all);
      logd ("CONF_modules_unload() ret: %d", ret);

      EVP_cleanup ();
      CRYPTO_cleanup_all_ex_data ();

      const CRYPTO_THREADID * tid = NULL;
      ERR_remove_thread_state (tid);
      ERR_free_strings ();
    }
*/

/*
  #include <pthread.h>

  #define MUTEX_TYPE       pthread_mutex_t
  #define MUTEX_SETUP(x)   pthread_mutex_init(&(x), NULL)
  #define MUTEX_CLEANUP(x) pthread_mutex_destroy(&(x))
  #define MUTEX_LOCK(x)    pthread_mutex_lock(&(x))
  #define MUTEX_UNLOCK(x)  pthread_mutex_unlock(&(x))
  #define THREAD_ID        pthread_self(  )


  static MUTEX_TYPE * mutex_buf= NULL;    // This array will store all of the mutexes available to OpenSSL.

  static void locking_function (int mode, int n, const char * file, int line) {
    if (mode & CRYPTO_LOCK)
      MUTEX_LOCK (mutex_buf [n]);
    else
      MUTEX_UNLOCK (mutex_buf [n]);
  }


  int server_verify_peer (SSL * ssl) {

    X509 * peer_cert = SSL_get_peer_certificate (ssl);

    STACK_OF(X509) * stackof_x509 = SSL_get_peer_cert_chain (ssl);

    if (peer_cert == NULL || stackof_x509 == NULL)
      return (-1);

    return (0);
  }

  int server_init (X509 * x509_cert, EVP_PKEY * priv_key) {
    int                 ret;

// "VmNativeSslInfo"
// "SslWrapperNative"

    SSL_load_error_strings ();

    ret = SSL_library_init ();                                          // Init
    logd ("SSL_library_init ret: %d", ret);
    if (ret < 0) {                                                      // Always returns "1", so it is safe to discard the return value.
      loge ("SSL_library_init() error");
      return (-1);
    }

    OPENSSL_add_all_algorithms_noconf ();                               // Add all algorithms, without using config file

  hu_ssl_method = (SSL_METHOD *) TLSv1_2_client_method ();  // Not ! ?

    hu_ssl_ctx = SSL_CTX_new (hu_ssl_method);
    if (hu_ssl_ctx == NULL) {
// "Failed to allocate ssl context!"
      loge ("SSL_CTX_new() error");
      return (-1);
    }

  //X509 * x509_cert = NULL;
    ret = SSL_CTX_use_certificate (hu_ssl_ctx, x509_cert);
    logd ("SSL_CTX_use_certificate ret: %d", ret);

  //EVP_PKEY * priv_key = NULL;
    ret = SSL_CTX_use_PrivateKey (hu_ssl_ctx, priv_key);
    logd ("SSL_CTX_use_PrivateKey ret: %d", ret);

    int cmd1 = 0;
    long larg1 = 0;
    void * parg1 = NULL;
    SSL_CTX_ctrl (hu_ssl_ctx, cmd1, larg1, parg1);

    hu_ssl_ssl = SSL_new (hu_ssl_ctx);
    if (hu_ssl_ssl == NULL) {
// "Failed to allocate ssl!"
      loge ("SSL_new() hu_ssl_ssl: %p", hu_ssl_ssl);
      return (-1);
    }
    logd ("SSL_new() hu_ssl_ssl: %p", hu_ssl_ssl);

    ret = SSL_check_private_key (hu_ssl_ssl);
    if (ret != 1) {
// "SSL private key check failed!"
      loge ("SSL_check_private_key() ret: %d", ret);
      return (-1);
    }
    logd ("SSL_check_private_key() ret: %d", ret);


  BIO * wf_bio = NULL;//BIO_new (BIO_f_ssl ());                         // Filter bio
  BIO * rf_bio = NULL;//BIO_new (BIO_f_ssl ());                         // Filter bio

    SSL_set_bio (hu_ssl_ssl, rf_bio, wf_bio);                                  // Read from memory, write to filter

    SSL_set_accept_state (hu_ssl_ssl);                                         // Set ssl to work in server mode

// "SSL native library successfully initialized!"
  }

  int server_handshake () {
    int ret = 0;

    ret = SSL_do_handshake (hu_ssl_ssl);                                       // HANDSHAKE: ServerHello+ServerCert, ClientKeyExchange+ChangeCipher
    logd ("SSL_do_handshake ret: %d", ret);

    hu_ssl_ret_log (ret);
    hu_ssl_inf_log ();

    return (0);
  }
*/

