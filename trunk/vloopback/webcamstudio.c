/*
 * v4l2loopback.c  --  video 4 linux loopback driver
 *
 * Copyright (C) 2005-2009
 * Vasily Levin (vasaka@gmail.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Modified by Patrick Balleux for WebcamStudio (http://www.ws4gl.org)
 */
#include <linux/version.h>
#include <linux/vmalloc.h>
#include <linux/mm.h>
#include <linux/time.h>
#include <linux/module.h>
#include <linux/videodev2.h>
#include <media/v4l2-ioctl.h>
#include <media/v4l2-common.h>

// sm-note: added

#include <linux/sched.h>

#define YAVLD_STREAMING

MODULE_DESCRIPTION("WebcamStudio Video Device");
MODULE_VERSION("0.1.1");
MODULE_AUTHOR("Patrick Balleux");
MODULE_LICENSE("GPL");

/* module structures */
/* TODO(vasaka) use typenames which are common to kernel, but first find out if
 * it is needed */
/* struct keeping state and settings of loopback device */
struct v4l2_loopback_device {
	struct video_device *vdev;
	/* pixel and stream format */
	struct v4l2_pix_format pix_format;
	struct v4l2_captureparm capture_param;
	/* buffers stuff */
	u8 *image;         /* pointer to actual buffers data */
	int buffers_number;  /* should not be big, 4 is a good choice */
	struct v4l2_buffer *buffers;	/* inner driver buffers */
	int write_position; /* number of last written frame + 1 */
	long buffer_size;
	/* sync stuff */
	atomic_t open_count;
	int ready_for_capture;/* set to true when at least one writer opened
			      * device and negotiated format */
	wait_queue_head_t read_event;
};

/* types of opener shows what opener wants to do with loopback */
enum opener_type {
	UNNEGOTIATED = 0,
	READER = 1,
	WRITER = 2,
};

/* struct keeping state and type of opener */
struct v4l2_loopback_opener {
	enum opener_type type;
	int buffers_number;
	int position; /* number of last processed frame + 1 or
		       * write_position - 1 if reader went out of sync */
	struct v4l2_buffer *buffers;
};

/* module parameters */
static int debug = 1;
module_param(debug, int, 0);
MODULE_PARM_DESC(debug, "if debug output is enabled, values are 0, 2 or 3");

static int max_buffers_number = 4;
module_param(max_buffers_number, int, 0);
MODULE_PARM_DESC(max_buffers_number, "how many buffers should be allocated");

static int max_openers = 10;
module_param(max_openers, int, 0);
MODULE_PARM_DESC(max_openers, "how many users can open loopback device");

/* module constants */
#define MAX_MMAP_BUFFERS 100  /* max buffers that can be mapped, actually they
				* are all mapped to max_buffers_number buffers*/

#define dprintk(fmt, args...)\
	if (debug > 1) {\
		printk(KERN_INFO "v4l2-loopback: " fmt, ##args);\
	}


#define dprintkrw(fmt, args...)\
	if (debug > 2) {\
		printk(KERN_INFO "v4l2-loopback: " fmt, ##args);\
	}

/* global module data */
struct v4l2_loopback_device *dev;
/* forward declarations */
static void init_buffers(int buffer_size);
static int allocate_buffers(void);
static const struct v4l2_file_operations v4l2_loopback_fops;
static const struct v4l2_ioctl_ops v4l2_loopback_ioctl_ops;
/* Queue helpers */
/* next functions sets buffer flags and adjusts counters accordingly */
static inline void set_done(struct v4l2_buffer *buffer)
{
	buffer->flags &= ~V4L2_BUF_FLAG_QUEUED;
	buffer->flags |= V4L2_BUF_FLAG_DONE;
}

static inline void set_queued(struct v4l2_buffer *buffer)
{
	buffer->flags &= ~V4L2_BUF_FLAG_DONE;
	buffer->flags |= V4L2_BUF_FLAG_QUEUED;
}

static inline void unset_flags(struct v4l2_buffer *buffer)
{
	buffer->flags &= ~V4L2_BUF_FLAG_QUEUED;
	buffer->flags &= ~V4L2_BUF_FLAG_DONE;
}
/* V4L2 ioctl caps and params calls */
/* returns device capabilities, called on VIDIOC_QUERYCAP ioctl*/
static int vidioc_querycap(struct file *file,
			   void *priv, struct v4l2_capability *cap)
{
	strlcpy(cap->driver, "v4l2 loopback", sizeof(cap->driver));
	strlcpy(cap->card, "WebcamStudio Video Device", sizeof(cap->card));
	cap->version = 1;
	cap->capabilities =
	    V4L2_CAP_VIDEO_CAPTURE | V4L2_CAP_VIDEO_OUTPUT |
	    V4L2_CAP_READWRITE
#ifdef YAVLD_STREAMING
	    | V4L2_CAP_STREAMING
#endif
	    ;
	return 0;
}

/* returns device formats, called on VIDIOC_ENUM_FMT ioctl*/
static int vidioc_enum_fmt_cap(struct file *file, void *fh,
			       struct v4l2_fmtdesc *f)
{
	if (dev->ready_for_capture == 0)
		return -EINVAL;
	if (f->index)
		return -EINVAL;
	strlcpy(f->description, "current format", sizeof(f->description));
	f->pixelformat = dev->pix_format.pixelformat;
	return 0;
};

/* returns current video format format fmt, called on VIDIOC_G_FMT ioctl */
static int vidioc_g_fmt_cap(struct file *file,
			    void *priv, struct v4l2_format *fmt)
{
	if (dev->ready_for_capture == 0)
		return -EINVAL;
	fmt->fmt.pix = dev->pix_format;
	return 0;
}

/* checks if it is OK to change to format fmt, called on VIDIOC_TRY_FMT ioctl
 * with v4l2_buf_type set to V4L2_BUF_TYPE_VIDEO_CAPTURE
 * actual check is done by inner_try_fmt_cap
 * just checking that pixelformat is OK and set other parameters, app should
 * obey this decision */
static int vidioc_try_fmt_cap(struct file *file,
			      void *priv, struct v4l2_format *fmt)
{
	struct v4l2_loopback_opener *opener = file->private_data;

	opener->type = READER;
	if (dev->ready_for_capture == 0)
		return -EINVAL;
	if (fmt->fmt.pix.pixelformat != dev->pix_format.pixelformat)
		return -EINVAL;
	fmt->fmt.pix = dev->pix_format;
	return 0;
}

/* checks if it is OK to change to format fmt, called on VIDIOC_TRY_FMT ioctl
 * with v4l2_buf_type set to V4L2_BUF_TYPE_VIDEO_OUTPUT
 * if format is negotiated do not change it */
static int vidioc_try_fmt_video_output(struct file *file,
				       void *priv, struct v4l2_format *fmt)
{
	struct v4l2_loopback_opener *opener = file->private_data;

	opener->type = WRITER;
	/* TODO(vasaka) loopback does not care about formats writer want to set,
	 * maybe it is a good idea to restrict format somehow */
	if (dev->ready_for_capture) {
		fmt->fmt.pix = dev->pix_format;
	} else {
		if (fmt->fmt.pix.sizeimage == 0)
			return -1;
		dev->pix_format = fmt->fmt.pix;
	}
	return 0;
};

/* sets new output format, if possible, called on VIDIOC_S_FMT ioctl
 * with v4l2_buf_type set to V4L2_BUF_TYPE_VIDEO_CAPTURE
 * actually format is set  by input and we even do not check it, just return
 * current one, but it is possible to set subregions of input TODO(vasaka) */
static int vidioc_s_fmt_cap(struct file *file,
			    void *priv, struct v4l2_format *fmt)
{
	return vidioc_try_fmt_cap(file, priv, fmt);
}

/* sets new output format, if possible, called on VIDIOC_S_FMT ioctl
 * with v4l2_buf_type set to V4L2_BUF_TYPE_VIDEO_OUTPUT
 * allocate data here because we do not know if it will be streaming or
 * read/write IO */
static int vidioc_s_fmt_video_output(struct file *file,
				     void *priv, struct v4l2_format *fmt)
{
	int ret = vidioc_try_fmt_video_output(file, priv, fmt);

	if (ret < 0)
		return ret;
	if (dev->ready_for_capture == 0) {
		dev->buffer_size = PAGE_ALIGN(dev->pix_format.sizeimage);
		fmt->fmt.pix.sizeimage = dev->buffer_size;
	}
	return ret;
}

/* get some data flaw parameters, only capability, fps and readbuffers has
 * effect on this driver, called on VIDIOC_G_PARM */
static int vidioc_g_parm(struct file *file, void *priv,
			 struct v4l2_streamparm *parm)
{
	/* do not care about type of opener, hope this enums would always be
	 * compatible */
	parm->parm.capture = dev->capture_param;
	return 0;
}

/* get some data flaw parameters, only capability, fps and readbuffers has
 * effect on this driver, called on VIDIOC_S_PARM */
static int vidioc_s_parm(struct file *file, void *priv,
			 struct v4l2_streamparm *parm)
{
	dprintk("vidioc_s_parm called frate=%d/%d\n",
	       parm->parm.capture.timeperframe.numerator,
	       parm->parm.capture.timeperframe.denominator);
	switch (parm->type) {
	case V4L2_BUF_TYPE_VIDEO_CAPTURE:
		parm->parm.capture = dev->capture_param;
		return 0;
	case V4L2_BUF_TYPE_VIDEO_OUTPUT:
		/* TODO(vasaka) do nothing now, but should set fps if
		 * needed */
		parm->parm.capture = dev->capture_param;
		return 0;
	default:
		return -1;
	}
}

/* sets a tv standard, actually we do not need to handle this any special way
 * added to support effecttv, can not be inline as I need pointer to it */
static int vidioc_s_std(struct file *file, void *private_data,
			v4l2_std_id *norm)
{
	return 0;
}

/* returns set of device inputs, in our case there is only one, but later I may
 * add more, called on VIDIOC_ENUMINPUT */
static int vidioc_enum_input(struct file *file, void *fh,
			     struct v4l2_input *inp)
{
	if (dev->ready_for_capture == 0)
		return -EINVAL;
	if (inp->index == 0) {
		strlcpy(inp->name, "loopback", sizeof(inp->name));
		inp->type = V4L2_INPUT_TYPE_CAMERA;
		inp->audioset = 0;
		inp->tuner = 0;
		inp->std = V4L2_STD_ALL;
		inp->status = 0;
		return 0;
	}
	return -EINVAL;
}

/* which input is currently active, called on VIDIOC_G_INPUT */
int vidioc_g_input(struct file *file, void *fh, unsigned int *i)
{
	*i = 0;
	return 0;
}

/* set input, can make sense if we have more than one video src,
 * called on VIDIOC_S_INPUT */
int vidioc_s_input(struct file *file, void *fh, unsigned int i)
{
	if (i == 0)
		return 0;
	return -EINVAL;
}

/* V4L2 ioctl buffer related calls */
/* negotiate buffer type, called on VIDIOC_REQBUFS
 * only mmap streaming supported */
static int vidioc_reqbufs(struct file *file, void *fh,
			  struct v4l2_requestbuffers *b)
{
	switch (b->memory) {
	case V4L2_MEMORY_MMAP:
		/* do nothing here, buffers are always allocated*/
		if (b->count == 0)
			return 0;
		b->count = dev->buffers_number;
		return 0;
	default:
		return -EINVAL;
	}
}

/* returns buffer asked for, called on VIDIOC_QUERYBUF
   give app as many buffers as it wants, if it less than MAX,
   but map them in our inner buffers */
static int vidioc_querybuf(struct file *file, void *fh,
			   struct v4l2_buffer *b)
{
	enum v4l2_buf_type type = b->type;
	int index = b->index;

	if ((b->type != V4L2_BUF_TYPE_VIDEO_CAPTURE) &&
	    (b->type != V4L2_BUF_TYPE_VIDEO_OUTPUT)) {
		return -EINVAL;
	}
	if (b->index > MAX_MMAP_BUFFERS)
		return -EINVAL;
	*b = dev->buffers[b->index % dev->buffers_number];
	b->type = type;
	b->index = index;
	return 0;
}

/* put buffer to queue, called on VIDIOC_QBUF */
static int vidioc_qbuf(struct file *file, void *private_data,
		       struct v4l2_buffer *buf)
{
	int index = buf->index % dev->buffers_number;

	if (buf->index > MAX_MMAP_BUFFERS)
		return -EINVAL;
	switch (buf->type) {
	case V4L2_BUF_TYPE_VIDEO_CAPTURE:
		set_queued(&dev->buffers[index]);
		return 0;
	case V4L2_BUF_TYPE_VIDEO_OUTPUT:
		do_gettimeofday(&dev->buffers[index].timestamp);
		set_done(&dev->buffers[index]);
		wake_up_all(&dev->read_event);
		return 0;
	default:
		return -EINVAL;
	}
}

/* put buffer to dequeue, called on VIDIOC_DQBUF */
static int vidioc_dqbuf(struct file *file, void *private_data,
			struct v4l2_buffer *buf)
{
	int index;
	struct v4l2_loopback_opener *opener = file->private_data;

	switch (buf->type) {
	case V4L2_BUF_TYPE_VIDEO_CAPTURE:
		if ((dev->write_position <= opener->position) &&
			(file->f_flags&O_NONBLOCK))
			return -EAGAIN;
		wait_event_interruptible(dev->read_event, (dev->write_position >
					 opener->position));
		if (dev->write_position > opener->position+2)
			opener->position = dev->write_position - 1;
		index = opener->position % dev->buffers_number;
		if (!(dev->buffers[index].flags&V4L2_BUF_FLAG_MAPPED)) {
			//printk(KERN_INFO "v4l2-loopback: "
			//       "trying to return not mapped buf\n");
			return -EINVAL;
		}
		++opener->position;
		unset_flags(&dev->buffers[index]);
		*buf = dev->buffers[index];
		return 0;
	case V4L2_BUF_TYPE_VIDEO_OUTPUT:
		index = dev->write_position % dev->buffers_number;
		unset_flags(&dev->buffers[index]);
		*buf = dev->buffers[index];
		++dev->write_position;
		return 0;
	default:
		return -EINVAL;
	}
}

static int vidioc_streamon(struct file *file, void *private_data,
			   enum v4l2_buf_type type)
{
	int ret;
	switch (type) {
	case V4L2_BUF_TYPE_VIDEO_OUTPUT:
		if (dev->ready_for_capture == 0) {
			ret = allocate_buffers();
			if (ret < 0)
				return ret;
			init_buffers(dev->buffer_size);
			dev->ready_for_capture = 1;
		}
		return 0;
	case V4L2_BUF_TYPE_VIDEO_CAPTURE:
		if (dev->ready_for_capture == 0)
			return -EIO;
		return 0;
	default:
		return -EINVAL;
	}
}

static int vidioc_streamoff(struct file *file, void *private_data,
			    enum v4l2_buf_type type)
{
	return 0;
}

#ifdef CONFIG_VIDEO_V4L1_COMPAT
int vidiocgmbuf(struct file *file, void *fh, struct video_mbuf *p)
{
	p->frames = dev->buffers_number;
	p->offsets[0] = 0;
	p->offsets[1] = 0;
	p->size = dev->buffer_size;
	return 0;
}
#endif
/* file operations */
static void vm_open(struct vm_area_struct *vma)
{
	/* TODO(vasaka) do open counter here */
}

static void vm_close(struct vm_area_struct *vma)
{
	/* TODO(vasaka) do open counter here */
}

static struct vm_operations_struct vm_ops = {
	.open = vm_open,
	.close = vm_close,
};

static int v4l2_loopback_mmap(struct file *file,
			      struct vm_area_struct *vma)
{

	struct page *page = NULL;
	unsigned long addr;
	unsigned long start = (unsigned long) vma->vm_start;
	unsigned long size = (unsigned long) (vma->vm_end - vma->vm_start);

	dprintk("entering v4l_mmap(), offset: %lu\n", vma->vm_pgoff);
	if (size > dev->buffer_size) {
		printk(KERN_INFO "v4l2-loopback: "
		       "userspace tries to mmap to much, fail\n");
		return -EINVAL;
	}
	if ((vma->vm_pgoff << PAGE_SHIFT) >
	    dev->buffer_size * (dev->buffers_number - 1)) {
		printk(KERN_INFO "v4l2-loopback: "
		       "userspace tries to mmap to far, fail\n");
		return -EINVAL;
	}
	addr = (unsigned long) dev->image + (vma->vm_pgoff << PAGE_SHIFT);

	while (size > 0) {
		page = (void *) vmalloc_to_page((void *) addr);

		if (vm_insert_page(vma, start, page) < 0)
			return -EAGAIN;

		start += PAGE_SIZE;
		addr += PAGE_SIZE;
		size -= PAGE_SIZE;
	}

	vma->vm_ops = &vm_ops;
	vma->vm_private_data = 0;
	dev->buffers[(vma->vm_pgoff<<PAGE_SHIFT)/dev->buffer_size].flags |=
		V4L2_BUF_FLAG_MAPPED;

	vm_open(vma);

	dprintk("leaving v4l_mmap()\n");

	return 0;
}

static unsigned int v4l2_loopback_poll(struct file *file,
				       struct poll_table_struct *pts)
{
	struct v4l2_loopback_opener *opener = file->private_data;
	int ret_mask = 0;

	switch (opener->type) {
	case WRITER:
		ret_mask = POLLOUT | POLLWRNORM;
		break;
	case READER:
		poll_wait(file, &dev->read_event, pts);
		if (dev->write_position > opener->position)
			ret_mask =  POLLIN | POLLRDNORM;
		break;
	default:
		ret_mask = -POLLERR;
	}
	return ret_mask;
}

/* do not want to limit device opens, it can be as many readers as user want,
 * writers are limited by means of setting writer field */
static int v4l_loopback_open(struct file *file)
{
	struct v4l2_loopback_opener *opener;

	dprintk("entering v4l_open()\n");
	if (dev->open_count.counter == max_openers)
		return -EBUSY;
	/* kfree on close */
	opener = kzalloc(sizeof(*opener), GFP_KERNEL);
	if (opener == NULL)
		return -ENOMEM;
	file->private_data = opener;
	atomic_inc(&dev->open_count);
	return 0;
}

static int v4l_loopback_close(struct file *file)
{
	struct v4l2_loopback_opener *opener = file->private_data;

	dprintk("entering v4l_close()\n");
	atomic_dec(&dev->open_count);
	/* TODO(vasaka) does the closed file means that mapped buffers are
	 * no more valid and one can free data? */
	if (dev->open_count.counter == 0) {
		vfree(dev->image);
		dev->image = NULL;
		dev->ready_for_capture = 0;
		dev->buffer_size = 0;
	}
	kfree(opener);
	return 0;
}

static ssize_t v4l_loopback_read(struct file *file, char __user *buf,
				 size_t count, loff_t *ppos)
{
	int read_index;
	struct v4l2_loopback_opener *opener = file->private_data;

	if ((dev->write_position <= opener->position) &&
		(file->f_flags&O_NONBLOCK)) {
		return -EAGAIN;
	}
	wait_event_interruptible(dev->read_event,
				 (dev->write_position > opener->position));
	if (count > dev->buffer_size)
		count = dev->buffer_size;
	if (dev->write_position > opener->position+2)
		opener->position = dev->write_position - 1;
	read_index = opener->position % dev->buffers_number;
	if (copy_to_user((void *) buf, (void *) (dev->image +
			 dev->buffers[read_index].m.offset), count)) {
		printk(KERN_ERR "v4l2-loopback: "
			"failed copy_from_user() in write buf\n");
		return -EFAULT;
	}
	++opener->position;
	dprintkrw("leave v4l2_loopback_read()\n");
	return count;
}

static ssize_t v4l_loopback_write(struct file *file,
				  const char __user *buf, size_t count,
				  loff_t *ppos)
{
	int write_index = dev->write_position % dev->buffers_number;
	int ret;

	if (dev->ready_for_capture == 0) {
		ret = allocate_buffers();
		if (ret < 0)
			return ret;
		init_buffers(dev->buffer_size);
		dev->ready_for_capture = 1;
	}
	dprintkrw("v4l2_loopback_write() trying to write %d bytes\n", count);
	if (count > dev->buffer_size)
		count = dev->buffer_size;
	if (copy_from_user(
		   (void *) (dev->image + dev->buffers[write_index].m.offset),
		   (void *) buf, count)) {
		printk(KERN_ERR "v4l2-loopback: "
		   "failed copy_from_user() in write buf, could not write %d\n",
		   count);
		return -EFAULT;
	}
	do_gettimeofday(&dev->buffers[write_index].timestamp);
	dev->buffers[write_index].sequence = dev->write_position++;
	wake_up_all(&dev->read_event);
	dprintkrw("leave v4l2_loopback_write()\n");
	return count;
}

/* init functions */
/* allocates buffers, if buffer_size is set */
static int allocate_buffers(void)
{
	/* vfree on close file operation in case no open handles left */
	if (dev->buffer_size == 0)
		return -EINVAL;
	dev->image = vmalloc(dev->buffer_size * dev->buffers_number);
	if (dev->image == NULL)
		return -ENOMEM;
	dprintk("vmallocated %ld bytes\n",
		dev->buffer_size * dev->buffers_number);
	return 0;
}
/* init inner buffers, they are capture mode and flags are set as
 * for capture mod buffers */
static void init_buffers(int buffer_size)
{
	int i;
	for (i = 0; i < dev->buffers_number; ++i) {
		dev->buffers[i].bytesused         = buffer_size;
		dev->buffers[i].length            = buffer_size;
		dev->buffers[i].field             = V4L2_FIELD_NONE;
		dev->buffers[i].flags             = 0;
		dev->buffers[i].index             = i;
		dev->buffers[i].input             = 0;
		dev->buffers[i].m.offset          = i * buffer_size;
		dev->buffers[i].memory            = V4L2_MEMORY_MMAP;
		dev->buffers[i].sequence          = 0;
		dev->buffers[i].timestamp.tv_sec  = 0;
		dev->buffers[i].timestamp.tv_usec = 0;
		dev->buffers[i].type              = V4L2_BUF_TYPE_VIDEO_CAPTURE;
	}
	dev->write_position = 0;
}

/* fills and register video device */
static void init_vdev(struct video_device *vdev)
{
	strlcpy(vdev->name, "WebcamStudio Video Driver", sizeof(vdev->name));
	vdev->tvnorms      = V4L2_STD_ALL;
	vdev->current_norm = V4L2_STD_ALL,
	vdev->vfl_type     = VFL_TYPE_GRABBER;
	vdev->fops         = &v4l2_loopback_fops;
	vdev->ioctl_ops    = &v4l2_loopback_ioctl_ops;
	vdev->release      = &video_device_release;
	vdev->minor        = -1;
	if (debug > 1)
		vdev->debug = V4L2_DEBUG_IOCTL | V4L2_DEBUG_IOCTL_ARG;
}

/* init default capture parameters, only fps may be changed in future */
static void init_capture_param(struct v4l2_captureparm *capture_param)
{
	capture_param->capability               = 0;
	capture_param->capturemode              = 0;
	capture_param->extendedmode             = 0;
	capture_param->readbuffers              = max_buffers_number;
	capture_param->timeperframe.numerator   = 1;
	capture_param->timeperframe.denominator = 30;
}

/* init loopback main structure */
static int v4l2_loopback_init(struct v4l2_loopback_device *dev)
{
	dev->vdev = video_device_alloc();
	if (dev->vdev == NULL)
		return -ENOMEM;
	init_vdev(dev->vdev);
	init_capture_param(&dev->capture_param);
	dev->buffers_number = max_buffers_number;
	atomic_set(&dev->open_count, 0);
	dev->ready_for_capture = 0;
	dev->buffer_size = 0;
	dev->image = NULL;
	/* kfree on module release */
	dev->buffers =
	    kzalloc(sizeof(*dev->buffers) * dev->buffers_number,
		    GFP_KERNEL);
	if (dev->buffers == NULL)
		return -ENOMEM;
	init_waitqueue_head(&dev->read_event);
	return 0;
};

/* LINUX KERNEL */
static const struct v4l2_file_operations v4l2_loopback_fops = {
      .owner   = THIS_MODULE,
      .open    = v4l_loopback_open,
      .release = v4l_loopback_close,
      .read    = v4l_loopback_read,
      .write   = v4l_loopback_write,
      .poll    = v4l2_loopback_poll,
      .mmap    = v4l2_loopback_mmap,
      .ioctl   = video_ioctl2,
};

static const struct v4l2_ioctl_ops v4l2_loopback_ioctl_ops = {
	.vidioc_querycap         = &vidioc_querycap,
	.vidioc_enum_fmt_vid_cap = &vidioc_enum_fmt_cap,
	.vidioc_enum_input       = &vidioc_enum_input,
	.vidioc_g_input          = &vidioc_g_input,
	.vidioc_s_input          = &vidioc_s_input,
	.vidioc_g_fmt_vid_cap    = &vidioc_g_fmt_cap,
	.vidioc_s_fmt_vid_cap    = &vidioc_s_fmt_cap,
	.vidioc_s_fmt_vid_out    = &vidioc_s_fmt_video_output,
	.vidioc_try_fmt_vid_cap  = &vidioc_try_fmt_cap,
	.vidioc_try_fmt_vid_out  = &vidioc_try_fmt_video_output,
	.vidioc_s_std            = &vidioc_s_std,
	.vidioc_g_parm           = &vidioc_g_parm,
	.vidioc_s_parm           = &vidioc_s_parm,
	.vidioc_reqbufs          = &vidioc_reqbufs,
	.vidioc_querybuf         = &vidioc_querybuf,
	.vidioc_qbuf             = &vidioc_qbuf,
	.vidioc_dqbuf            = &vidioc_dqbuf,
	.vidioc_streamon         = &vidioc_streamon,
	.vidioc_streamoff        = &vidioc_streamoff,
#ifdef CONFIG_VIDEO_V4L1_COMPAT
	.vidiocgmbuf             = &vidiocgmbuf,
#endif
};

int __init init_module()
{
	int ret;

	dprintk("entering init_module()\n");
	/* kfree on module release */
	dev = kzalloc(sizeof(*dev), GFP_KERNEL);
	if (dev == NULL)
		return -ENOMEM;
	ret = v4l2_loopback_init(dev);
	if (ret < 0)
		return ret;
	/* register the device -> it creates /dev/video* */
	if (video_register_device(dev->vdev, VFL_TYPE_GRABBER, -1) < 0) {
		video_device_release(dev->vdev);
		printk(KERN_ERR "failed video_register_device()\n");
		return -EFAULT;
	}
	printk(KERN_INFO "WebcamStudio module installed\n");
	return 0;
}

void __exit cleanup_module()
{
	dprintk("entering cleanup_module()\n");
	/* unregister the device -> it deletes /dev/video* */
	video_unregister_device(dev->vdev);
	kfree(dev->buffers);
	kfree(dev);
	printk(KERN_INFO "WebcamStudio module removed\n");
}
