CC=gcc
CFLAGS=`pkg-config --cflags gstreamer-1.0 gstreamer-app-1.0 libcrypto libusb-1.0 openssl` -I$(TOP)/jni
LFLAGS=`pkg-config --libs gstreamer-1.0 gstreamer-app-1.0 libcrypto libusb-1.0 openssl`
TOP = ..
AASRCS =	$(TOP)/jni/hu_aad.c	\
		$(TOP)/jni/hu_aap.c	\
	        $(TOP)/jni/hu_ssl.c	\
		$(TOP)/jni/hu_usb.c	\
		$(TOP)/jni/hu_uti.c
SRCS =		$(AASRCS) main.c
OBJS = $(SRCS:.c=.o)
APP = headunit

.PHONY: depend clean

all: $(APP)

$(APP): $(OBJS)
	$(CC) $(CFLAGS) -o $(APP) $(OBJS) $(LFLAGS)

.c.o:
	$(CC) $(CFLAGS) -c $<  -o $@

clean:
	rm -f $(OBJS) *~ $(APP)

depend: $(SRCS)
	makedepend $^

# DO NOT DELETE
