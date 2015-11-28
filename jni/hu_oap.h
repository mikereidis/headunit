
// Accessory/ADB/Audio

//    char AAP_VAL_MAN [31] = "Android";
//    char AAP_VAL_MOD [97] = "Android Auto";    // "Android Open Automotive Protocol"


#define AAP_VAL_MAN   "Android"
#define AAP_VAL_MOD   "Android Auto"    // "Android Open Automotive Protocol"
//#define AAP_VAL_DES   "Description"
//#define AAP_VAL_VER   "VersionName"
//#define AAP_VAL_URI   "https://developer.android.com/auto/index.html"
//#define AAP_VAL_SER   "62skidoo"

#define ACC_IDX_MAN   0   // Manufacturer
#define ACC_IDX_MOD   1   // Model
//#define ACC_IDX_DES   2   // Description
//#define ACC_IDX_VER   3   // Version
//#define ACC_IDX_URI   4   // URI
//#define ACC_IDX_SER   5   // Serial Number

#define ACC_REQ_GET_PROTOCOL        51
#define ACC_REQ_SEND_STRING         52
#define ACC_REQ_START               53

//#define ACC_REQ_REGISTER_HID        54
//#define ACC_REQ_UNREGISTER_HID      55
//#define ACC_REQ_SET_HID_REPORT_DESC 56
//#define ACC_REQ_SEND_HID_EVENT      57
#define ACC_REQ_AUDIO               58

#define USB_SETUP_HOST_TO_DEVICE                0x00    // transfer direction - host to device transfer   = USB_DIR_OUT (Output from host)
#define USB_SETUP_DEVICE_TO_HOST                0x80    // transfer direction - device to host transfer   = USB_DIR_IN  (Input  to   host)

//#define USB_SETUP_TYPE_STANDARD                 0x00    // type - standard
//#define USB_SETUP_TYPE_CLASS                    0x20    // type - class
#define USB_SETUP_TYPE_VENDOR                   0x40    // type - vendor   = USB_TYPE_VENDOR

#define USB_SETUP_RECIPIENT_DEVICE              0x00    // recipient - device
//#define USB_SETUP_RECIPIENT_INTERFACE           0x01    // recipient - interface
//#define USB_SETUP_RECIPIENT_ENDPOINT            0x02    // recipient - endpoint
//#define USB_SETUP_RECIPIENT_OTHER               0x03    // recipient - other

