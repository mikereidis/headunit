
  #include <openssl/bio.h>
  #include <openssl/ssl.h>
  #include <openssl/err.h>
  #include <openssl/pem.h>
  #include <openssl/x509.h>
  #include <openssl/x509_vfy.h>

  //SSL_METHOD  * hu_ssl_method  = NULL;
  //SSL_CTX     * hu_ssl_ctx     = NULL;
  extern SSL         * hu_ssl_ssl   ;//  = NULL;
  extern BIO         * hu_ssl_rm_bio;//  = NULL;
  extern BIO         * hu_ssl_wm_bio;//  = NULL;

  void hu_ssl_ret_log (int ret);

  int hu_ssl_handshake ();


  // Internal:

#ifdef  MR_SSL_INTERNAL
  // 2048 bits,  Signature Algorithm: sha256WithRSAEncryption

  #define cert_buf  hu_ssl_cert_mr_buf
  #define pkey_buf  hu_ssl_pkey_mr_buf

    char hu_ssl_cert_mr_buf [] = "-----BEGIN CERTIFICATE-----\n\
MIIDXTCCAkWgAwIBAgIJAN5RMTidgcQYMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV\n\
BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n\
aWRnaXRzIFB0eSBMdGQwHhcNMTUwNTIwMDExMDAxWhcNMTUwNTIxMDExMDAxWjBF\n\
MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50\n\
ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n\
CgKCAQEAyXh5TWY7hG1v4+c5aoMu/1vFJ+N9xM0+IjY74ozOqRvl8K6aSu+woJB0\n\
eCk5JkTYAbZnp7izzGEClDVLZyR381Sm2QPZT7JbfVIT+xEhALxchIXv65Wrr+og\n\
eP4B/RFk2SQ0wikaM1xVwfJaSLUNDZVVAFmR4czlav1C5FhVJzK6xXl000i/yYQX\n\
eVgjVsdiA17RLuG2IT9fP+3Ax+Ms74f/U9OIQq5UUsBXvN1FEHtSI29GC4lFgQZx\n\
qfFu4mZfofEAsHB9HzeN1rbhYjhMYlDgR6YMMPKQSqO5Cp9YRDsat5oqqqb1H8vU\n\
emgrQeJXO1K6C1shaT385cPTzrsLNwIDAQABo1AwTjAdBgNVHQ4EFgQUqYkdwPC3\n\
5cSWnJ+zP3uz4H+SbZYwHwYDVR0jBBgwFoAUqYkdwPC35cSWnJ+zP3uz4H+SbZYw\n\
DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAICHwyY2qUfy7AuJtSV0Z\n\
Y4XY0V/nkvTSqDchFsh0pG5jN8/GUfTNVf30vWKD46smzVFvqB815vAeAJscBcny\n\
GHdrJcpbL984Rj9/vp8q6UpfNJ+ySWQqiMt0pBI+X8DFW7s14biiFML0K9Piwm7V\n\
q2e9lmVBi4Z8lQMbx1htaz1Z/SE9RnyS/5sXwdQeB10neCAYUzCOFQoHGqB69BWT\n\
Y3aHzAoCIJ3ZwKpqYW9g69nYeVVZO54kZc4pmrvH1kpcqC8uVqqbWsU1+zXeS+Dx\n\
PK4ncPq+VUu2Hct88iy8K8am5iV32dbsNU74AttYi+WscV45dtWfWCD9qPCPl3DS\n\
WQ==\n\
-----END CERTIFICATE-----\n";

    char hu_ssl_pkey_mr_buf [] = "-----BEGIN RSA PRIVATE KEY-----\n\
MIIEowIBAAKCAQEAyXh5TWY7hG1v4+c5aoMu/1vFJ+N9xM0+IjY74ozOqRvl8K6a\n\
Su+woJB0eCk5JkTYAbZnp7izzGEClDVLZyR381Sm2QPZT7JbfVIT+xEhALxchIXv\n\
65Wrr+ogeP4B/RFk2SQ0wikaM1xVwfJaSLUNDZVVAFmR4czlav1C5FhVJzK6xXl0\n\
00i/yYQXeVgjVsdiA17RLuG2IT9fP+3Ax+Ms74f/U9OIQq5UUsBXvN1FEHtSI29G\n\
C4lFgQZxqfFu4mZfofEAsHB9HzeN1rbhYjhMYlDgR6YMMPKQSqO5Cp9YRDsat5oq\n\
qqb1H8vUemgrQeJXO1K6C1shaT385cPTzrsLNwIDAQABAoIBAHrByUd7zy/1boOy\n\
060umWhGhm6zkmJjnEREP2De4tzvfr+T47ddLIXo/s5ob8X9lJAWkDoFtKgHRAcC\n\
IhuKgPvmzHLWgYap6k0Fwd7spOtJ2iV1ZqZo39+kDH0saBHGk2grQ2o6mRhKXoZ6\n\
IMDEcFuibdR0vwqSSgdSoXt4xwnGLe+OpopQ7KXSyJpXnU8iIhwS7FjvuvADQ9bV\n\
kft/Usqm1UIBxjcIMVLMSdGZXctGzn4Y+rVrcEkK8MtCSc+uspAlQpg9CBJlTbcc\n\
ymeA/0rOd545AaR3wlB1Txx1z15IbTCAp6ryjZLfrhWwgmRDTNATp1Qwq6VI7lWs\n\
Vm24fWECgYEA8fRD30p56jyJ6pzSB23K0nECODCNYLlDPNOnvxLhyrImqp9dAiWQ\n\
/PJSIuyuQKXRLJHdR/hQLd3LnHYgOYXWY1OLmXhx4jh7lZ3Efehhq/8fB9inSoK+\n\
rAKAslw9DFajVL47k9TRqLfJwmwhaoy558qxf4HoS17aswowSp0KYssCgYEA1SqS\n\
z49GCEGQJilGFEa+PSoqSD4hVE4fPdKOhYIewbK9kkujX4PT0aBMYOHompTYMPln\n\
kqOBeSg48vXk0aWKdTKS7bxEQEfP5NvewvH08ejjne+lIhC3Zl7O5Gj1+qm3MKvP\n\
ukb78KT0cphZun839+tORoNyruvraDqLBYbVb8UCgYEAxHpJ/3I4LEl66YTtXVEA\n\
CgHw/nYW6HupKSTrLFOF/ZbLpXSCD7M32OBiaK/wFNlSUjIlEkOHwKdCp8yZAH05\n\
ijEWxMq1GDIr0WRrYp8paYVjynhZ6Tzg30etAKm8fV+BhNhyAusoUizk7zflruOW\n\
N682kkeIvmPJjuhwLLb37x8CgYBx2DfqJDGZzTIoP1jPEW0ei3tjc9MnDEYBJYe6\n\
Y+D7P/Ogw0awh15EEWFZSK2KiT3hAgI/vZUzWLj5gTvAf7Gvn9/6mda1oeS07HxP\n\
Dxvrap7NxaQiyly4jp/eOvRL+AH/O3NIoAqD3gUzgoxBXxpMDN6UKDXCl/r0gnem\n\
7vjkpQKBgGeLkU0YxewVActDRt9/WXvL57Z1rcTBV1cI7QYV9GNBS9ozG7AG/2k0\n\
Pf9wyuNGoUoGnjXoaiE4MgLddXPtIDkfSrvZjOlnF8sZDWf77gEbKmroq/Dlx/Rg\n\
q99fN5L3n8j85Sw21/JO/FIwBZhUNb1Fh3ddFpMnFkBf3LZj1kJh\n\
-----END RSA PRIVATE KEY-----\n";

#endif  //#ifdef  MR_SSL_INTERNAL

