CC=gcc
CPP=g++
LD=ld
INCLUDES=`pkg-config --cflags gstreamer-1.0 gstreamer-app-1.0 gstreamer-video-1.0 libcrypto libusb-1.0 openssl` -I$(TOP)/jni -I/usr/include/x86_64-linux-gnu/qt5/QtWidgets -I/usr/include/x86_64-linux-gnu/qt5 -I/usr/include/x86_64-linux-gnu/qt5/QtGui
CFLAGS=
CPPFLAGS=-fPIC
LFLAGS=`pkg-config --libs gstreamer-1.0 gstreamer-app-1.0 gstreamer-video-1.0 libcrypto libusb-1.0 openssl` -lQt5Core -lQt5Widgets -lQt5Gui
TOP = ..
AASRCS =	$(TOP)/jni/hu_aad.c	\
		$(TOP)/jni/hu_aap.c	\
	        $(TOP)/jni/hu_ssl.c	\
		$(TOP)/jni/hu_usb.c	\
		$(TOP)/jni/hu_uti.c
HSRCS =		main.cc
OBJS = $(AASRCS:.c=.o) $(HSRCS:.cc=.o)
APP = headunit

.PHONY: depend clean

all: $(APP)

$(APP): $(OBJS)
	$(CPP) -o $(APP) $(OBJS) $(LFLAGS)

.c.o:
	$(CC) $(CFLAGS) $(INCLUDES) -c $<  -o $@

.cc.o:
	$(CPP) $(CPPFLAGS) $(INCLUDES) -c $<  -o $@

clean:
	rm -f $(OBJS) *~ $(APP)

depend: $(SRCS)
	makedepend $^

# DO NOT DELETE
