#include <glib.h>
#include <stdio.h>
#include <gst/gst.h>
#include <gst/app/gstappsrc.h>
#include <gst/video/videooverlay.h>
#include <QApplication>
//#include <QWidget>
#include <QMainWindow>
#include <QMouseEvent>


#include "hu_uti.h"
#include "hu_aap.h"

typedef struct {
	GstPipeline *pipeline;
	GstAppSrc *src;
	GstElement *sink;
	GstElement *decoder;
	GstElement *convert;
	guint sourceid;
} gst_app_t;

static gst_app_t gst_app;

static gboolean read_data(gst_app_t *app)
{
	GstBuffer *buffer;
	guint8 *ptr;
	GstFlowReturn ret;
	int iret;
	char *vbuf;
	int res_len = 0;

	iret = hu_aap_recv_process ();                                    // Process 1 message
	if (iret != 0) {
		printf("hu_aap_recv_process() iret: %d\n", iret);
		return FALSE;
	}

	/* Is there a video buffer queued? */
	vbuf = read_head_buffer_get (& res_len);
	if (vbuf != NULL) {
		printf("vbuf: %p  res_len: %d\n", vbuf, res_len);

		ptr = (guint8 *)g_malloc(res_len);
		g_assert(ptr);
		memcpy(ptr, vbuf, res_len);

		buffer = gst_buffer_new_wrapped(ptr, res_len);

		ret = gst_app_src_push_buffer(app->src, buffer);

		if(ret !=  GST_FLOW_OK){
			g_debug("push buffer returned %d for %d bytes \n", ret, res_len);
			return FALSE;
		}

#if 0
		if(size != BUFF_SIZE){
			ret = gst_app_src_end_of_stream(app->src);
			g_debug("eos returned %d at %d\n", ret, __LINE__);
			return FALSE;
		}
#endif
	}

	return TRUE;
}

static void start_feed (GstElement * pipeline, guint size, gst_app_t *app)
{
	if (app->sourceid == 0) {
		GST_DEBUG ("start feeding");
		app->sourceid = g_idle_add ((GSourceFunc) read_data, app);
	}
}

static void stop_feed (GstElement * pipeline, gst_app_t *app)
{
	if (app->sourceid != 0) {
		GST_DEBUG ("stop feeding");
		g_source_remove (app->sourceid);
		app->sourceid = 0;
	}
}

static void on_pad_added(GstElement *element, GstPad *pad)
{
	GstCaps *caps;
	GstStructure *str;
	gchar *name;
	GstPad *convertsink;
	GstPadLinkReturn ret;

	g_debug("pad added");

	caps = gst_pad_get_current_caps(pad);
	str = gst_caps_get_structure(caps, 0);

	g_assert(str);

	name = (gchar*)gst_structure_get_name(str);

	g_debug("pad name %s", name);

	if(g_strrstr(name, "video")){

		convertsink = gst_element_get_static_pad(gst_app.convert, "sink");
		g_assert(convertsink);
		ret = gst_pad_link(pad, convertsink);
		g_debug("pad_link returned %d\n", ret);
		gst_object_unref(convertsink);
	}
	gst_caps_unref(caps);
}

static gboolean bus_callback(GstBus *bus, GstMessage *message, gpointer *ptr)
{
	gst_app_t *app = (gst_app_t*)ptr;

	switch(GST_MESSAGE_TYPE(message)){

		case GST_MESSAGE_ERROR:{
					       gchar *debug;
					       GError *err;

					       gst_message_parse_error(message, &err, &debug);
					       g_print("Error %s\n", err->message);
					       g_error_free(err);
					       g_free(debug);
//					       g_main_loop_quit(app->loop);
				       }
				       break;

		case GST_MESSAGE_WARNING:{
						 gchar *debug;
						 GError *err;
						 gchar *name;

						 gst_message_parse_warning(message, &err, &debug);
						 g_print("Warning %s\nDebug %s\n", err->message, debug);

						 name = (gchar *)GST_MESSAGE_SRC_NAME(message);

						 g_print("Name of src %s\n", name ? name : "nil");
						 g_error_free(err);
						 g_free(debug);
					 }
					 break;

		case GST_MESSAGE_EOS:
					 g_print("End of stream\n");
//					 g_main_loop_quit(app->loop);
					 break;

		case GST_MESSAGE_STATE_CHANGED:
					 break;

		default:
					 g_print("got message %s\n", \
							 gst_message_type_get_name (GST_MESSAGE_TYPE (message)));
					 break;
	}

	return TRUE;
}

static int gst_pipeline_init(gst_app_t *app)
{
	GstBus *bus;
	GstStateChangeReturn state_ret;

	gst_init(NULL, NULL);

	app->pipeline = (GstPipeline*)gst_pipeline_new("mypipeline");
	bus = gst_pipeline_get_bus(app->pipeline);
	gst_bus_add_watch(bus, (GstBusFunc)bus_callback, app);
	gst_object_unref(bus);

	app->src = (GstAppSrc*)gst_element_factory_make("appsrc", "mysrc");
	app->decoder = gst_element_factory_make("decodebin", "mydecoder");
	app->convert = gst_element_factory_make("videoconvert", "myconvert");
	app->sink = gst_element_factory_make("xvimagesink", "myvsink");

	g_assert(app->src);
	g_assert(app->decoder);
	g_assert(app->convert);
	g_assert(app->sink);

	g_signal_connect(app->src, "need-data", G_CALLBACK(start_feed), app);
	g_signal_connect(app->src, "enough-data", G_CALLBACK(stop_feed), app);
	g_signal_connect(app->decoder, "pad-added",
			G_CALLBACK(on_pad_added), app->decoder);

	gst_bin_add_many(GST_BIN(app->pipeline), (GstElement*)app->src,
			app->decoder, app->convert, app->sink, NULL);

	if(!gst_element_link((GstElement*)app->src, app->decoder)){
		g_warning("failed to link src anbd decoder");
	}

	if(!gst_element_link(app->convert, app->sink)){
		g_warning("failed to link convert and sink");
	}

	gst_app_src_set_stream_type(app->src, GST_APP_STREAM_TYPE_STREAM);

	return 0;
}

static int gst_loop(gst_app_t *app, QApplication *qapp)
{
	int ret;
	GstStateChangeReturn state_ret;

	state_ret = gst_element_set_state((GstElement*)app->pipeline, GST_STATE_PLAYING);
	g_warning("set state returned %d\n", state_ret);

	ret = qapp->exec();

	state_ret = gst_element_set_state((GstElement*)app->pipeline, GST_STATE_NULL);
	g_warning("set state null returned %d\n", state_ret);

	gst_object_unref(app->pipeline);

	return ret;
}

class HuMainWindow: public QMainWindow
{
	public:
	HuMainWindow()
	{};
	~ HuMainWindow(){};

	void mouseReleaseEvent ( QMouseEvent * event )
	{
		if(event->button() == Qt::LeftButton) {
			printf("\n***\n");
			printf("YOU CLICKED MOUSE AT X:%d, Y:%d\n", event->x(), event->y());
			printf("***\n");
		}
	};
};

int main (int argc, char *argv[])
{
	gst_app_t *app = &gst_app;
	int ret = 0;
	errno = 0;
	byte ep_in_addr  = -1;
	byte ep_out_addr = -1;

	/* Init Qt window */
	QApplication qapp(argc, argv);
	qapp.connect(&qapp, SIGNAL(lastWindowClosed()), &qapp, SLOT(quit ()));

	HuMainWindow *window = new HuMainWindow();
	window->resize(800, 480);
	window->show();

	/* Init gstreamer pipelien */
	ret = gst_pipeline_init(app);
	if (ret < 0) {
		printf("gst_pipeline_init() ret: %d\n", ret);
		return (ret);
	}

	/* Overlay gst sink on the Qt window */
	WId xwinid = window->winId();
	gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(app->sink), xwinid);

	/* Start AA processing */
	ret = hu_aap_start (ep_in_addr, ep_out_addr);
	if (ret < 0) {
		printf("hu_app_start() ret: %d\n", ret);
		return (ret);
	}

	/* Start gstreamer pipeline and main loop */
	ret = gst_loop(app, &qapp);
	if (ret < 0) {
		printf("gst_loop() ret: %d\n", ret);
		return (ret);
	}

	/* Shut down window */
	window->hide();

	/* Stop AA processing */
	ret = hu_aap_stop ();
	if (ret < 0) {
		printf("hu_aap_stop() ret: %d\n", ret);
		return (ret);
	}

	return (ret);
}
