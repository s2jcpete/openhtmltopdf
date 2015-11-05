/*
 * {{{ header & license
 * Copyright (c) 2004, 2005 Torbj�rn Gannholm
 * Copyright (c) 2006 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.pdfboxout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;

import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.resource.ImageResource;
import org.xhtmlrenderer.swing.NaiveUserAgent;
import org.xhtmlrenderer.util.XRLog;
import org.xhtmlrenderer.util.ImageUtil;

public class PdfBoxUserAgent extends NaiveUserAgent {
    private static final int IMAGE_CACHE_CAPACITY = 32;

    private SharedContext _sharedContext;

    private final PdfBoxOutputDevice _outputDevice;

    public PdfBoxUserAgent(PdfBoxOutputDevice outputDevice) {
		super(IMAGE_CACHE_CAPACITY);
		_outputDevice = outputDevice;
    }

    private byte[] readStream(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(is.available());
        byte[] buf = new byte[10240];
        int i;
        while ( (i = is.read(buf)) != -1) {
            out.write(buf, 0, i);
        }
        out.close();
        return out.toByteArray();
    }
    
    public ImageResource getImageResource(String uriStr) {
        ImageResource resource = null;
        if (ImageUtil.isEmbeddedBase64Image(uriStr)) {
            resource = loadEmbeddedBase64ImageResource(uriStr);
        } else {
            uriStr = resolveURI(uriStr);
            resource = (ImageResource) _imageCache.get(uriStr);
            if (resource == null) {
                InputStream is = resolveAndOpenStream(uriStr);
                if (is != null) {
                    try {
                        URI uri = new URI(uriStr);
                        if (uri.getPath() != null && uri.getPath().toLowerCase(Locale.US).endsWith(".pdf")) {
                            // TODO: Implement PDF AS IMAGE
//                            PdfReader reader = _outputDevice.getReader(uri);
//                            PDFAsImage image = new PDFAsImage(uri);
//                            Rectangle rect = reader.getPageSizeWithRotation(1);
//                            image.setInitialWidth(rect.getWidth() * _outputDevice.getDotsPerPoint());
//                            image.setInitialHeight(rect.getHeight() * _outputDevice.getDotsPerPoint());
//                            resource = new ImageResource(uriStr, image);
                        } else {
                            byte[] imgBytes = readStream(is);
                            PdfBoxImage fsImage = new PdfBoxImage(imgBytes, uriStr);
                            scaleToOutputResolution(fsImage);
                            resource = new ImageResource(uriStr, fsImage);
                         }
                        _imageCache.put(uriStr, resource);
                    } catch (Exception e) {
                        XRLog.exception("Can't read image file; unexpected problem for URI '" + uriStr + "'", e);
                    } finally {
                        try {
                            is.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }

            if (resource != null) {
                FSImage image = resource.getImage();
                if (image instanceof PdfBoxImage) {
                    // TODO: Make PdfBoxImage immutable so we don't stuff up the cache.
                    image = (PdfBoxImage) resource.getImage();
                }
                resource = new ImageResource(resource.getImageUri(), image);
            } else {
                resource = new ImageResource(uriStr, null);
            }
        }
        return resource;
    }
    
    private ImageResource loadEmbeddedBase64ImageResource(final String uri) {
        try {
            byte[] buffer = ImageUtil.getEmbeddedBase64Image(uri);
            PdfBoxImage fsImage = new PdfBoxImage(buffer, uri);
            scaleToOutputResolution(fsImage);
            return new ImageResource(null, fsImage);
        } catch (Exception e) {
            XRLog.exception("Can't read XHTML embedded image.", e);
        }
        return new ImageResource(null, null);
    }

    private void scaleToOutputResolution(PdfBoxImage image) {
        float factor = _sharedContext.getDotsPerPixel();
        if (factor != 1.0f) {
            image.scale((int) (image.getWidth() * factor), (int) (image.getHeight() * factor));
        }
    }

    public SharedContext getSharedContext() {
        return _sharedContext;
    }

    public void setSharedContext(SharedContext sharedContext) {
        _sharedContext = sharedContext;
    }
}
