/*
 * Copyright (C) 2015 Federico Iosue (federico.iosue@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.feio.android.omninotes.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.Bitmap.CompressFormat;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore.Images.Thumbnails;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import it.feio.android.omninotes.R;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.simplegallery.util.BitmapUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.*;


public class BitmapHelper {


    /**
     * Creates a thumbnail of requested size by doing a first sampled decoding of the bitmap to optimize memory
     */
	public static Bitmap getThumbnail(Context mContext, Uri uri, int reqWidth, int reqHeight) {
		Bitmap srcBmp = BitmapUtils.decodeSampledFromUri(mContext, uri, reqWidth, reqHeight);

		// If picture is smaller than required thumbnail
		Bitmap dstBmp;
		if (srcBmp.getWidth() < reqWidth && srcBmp.getHeight() < reqHeight) {
			dstBmp = ThumbnailUtils.extractThumbnail(srcBmp, reqWidth, reqHeight);

			// Otherwise the ratio between measures is calculated to fit requested thumbnail's one
		} else {
			// Cropping
			int x = 0, y = 0, width = srcBmp.getWidth(), height = srcBmp.getHeight();
			float ratio = ((float) reqWidth / (float) reqHeight) * ((float) srcBmp.getHeight() / (float) srcBmp
					.getWidth());
			if (ratio < 1) {
				x = (int) (srcBmp.getWidth() - srcBmp.getWidth() * ratio) / 2;
				width = (int) (srcBmp.getWidth() * ratio);
			} else {
				y = (int) (srcBmp.getHeight() - srcBmp.getHeight() / ratio) / 2;
				height = (int) (srcBmp.getHeight() / ratio);
			}

			int rotation = neededRotation(new File(uri.getPath()));
			if (rotation != 0) {
				Matrix matrix = new Matrix();
				matrix.postRotate(rotation);
				dstBmp = Bitmap.createBitmap(srcBmp, x, y, width, height, matrix, true);
			} else {
				dstBmp = Bitmap.createBitmap(srcBmp, x, y, width, height);
			}

		}
		return dstBmp;
	}


    /**
     * Retrieves a the bitmap relative to attachment based on mime type
     */
    public static Bitmap getBitmapFromAttachment(Context mContext, Attachment mAttachment, int width, int height) {
        Bitmap bmp = null;
        String path;
        mAttachment.getUri().getPath();

        // Video
        if (Constants.MIME_TYPE_VIDEO.equals(mAttachment.getMime_type())) {
            // Tries to retrieve full path from ContentResolver if is a new video
            path = StorageHelper.getRealPathFromURI(mContext,
                    mAttachment.getUri());
            // .. or directly from local directory otherwise
            if (path == null) {
                path = FileHelper.getPath(mContext, mAttachment.getUri());
            }
            bmp = ThumbnailUtils.createVideoThumbnail(path,
					Thumbnails.MINI_KIND);
            if (bmp == null) {
                return null;
            } else {
                bmp = BitmapUtils.createVideoThumbnail(mContext, bmp, width, height);
            }

		// Image
        } else if (Constants.MIME_TYPE_IMAGE.equals(mAttachment.getMime_type())
                || Constants.MIME_TYPE_SKETCH.equals(mAttachment.getMime_type())) {
            try {
                bmp = BitmapHelper.getThumbnail(mContext, mAttachment.getUri(), width, height);
            } catch (NullPointerException e) {
                bmp = null;
            }

		// Audio
        } else if (Constants.MIME_TYPE_AUDIO.equals(mAttachment.getMime_type())) {
            bmp = ThumbnailUtils.extractThumbnail(
                    decodeSampledBitmapFromResourceMemOpt(mContext.getResources().openRawResource(R.drawable.play),
                            width, height), width, height);

		// File
		} else if (Constants.MIME_TYPE_FILES.equals(mAttachment.getMime_type())) {

			// vCard
			if (Constants.MIME_TYPE_CONTACT_EXT.equals(FilenameUtils.getExtension(mAttachment.getName()))) {
				bmp = ThumbnailUtils.extractThumbnail(
						decodeSampledBitmapFromResourceMemOpt(mContext.getResources().openRawResource(R.drawable.vcard),
								width, height), width, height);
			} else {
				bmp = ThumbnailUtils.extractThumbnail(
						decodeSampledBitmapFromResourceMemOpt(mContext.getResources().openRawResource(R.drawable.files),

								width, height), width, height);
			}
		}

        return bmp;
    }


    public static Uri getThumbnailUri(Context mContext, Attachment mAttachment) {
        Uri uri = mAttachment.getUri();
        String mimeType = StorageHelper.getMimeType(uri.toString());
        if (!TextUtils.isEmpty(mimeType)) {
            String type = mimeType.split("/")[0];
            String subtype = mimeType.split("/")[1];
            switch (type) {
                case "image":
                case "video":
                    // Nothing to do, bitmap will be retrieved from this
                    break;
                case "audio":
                    uri = Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.drawable.play);
                    break;
                default:
					int drawable = "x-vcard".equals(subtype) ? R.drawable.vcard : R.drawable.files;
                    uri = Uri.parse("android.resource://" + mContext.getPackageName() + "/" + drawable);
                    break;
            }
        } else {
            uri = Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.drawable.files);
        }
        return uri;
    }


    /**
     * Checks if a bitmap is null and returns a placeholder in its place
     */
    private static int dpToPx(Context mContext, int dp) {
        float density = mContext.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }


    public static Bitmap decodeSampledBitmapFromResourceMemOpt(InputStream inputStream, int reqWidth, int reqHeight) {

        byte[] byteArr = new byte[0];
        byte[] buffer = new byte[1024];
        int len;
        int count = 0;

        try {
            while ((len = inputStream.read(buffer)) > -1) {
                if (len != 0) {
                    if (count + len > byteArr.length) {
                        byte[] newbuf = new byte[(count + len) * 2];
                        System.arraycopy(byteArr, 0, newbuf, 0, count);
                        byteArr = newbuf;
                    }

                    System.arraycopy(buffer, 0, byteArr, count, len);
                    count += len;
                }
            }

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(byteArr, 0, count, options);

            options.inSampleSize = BitmapUtils.calculateInSampleSize(options, reqWidth, reqHeight);
            options.inPurgeable = true;
            options.inInputShareable = true;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            return BitmapFactory.decodeByteArray(byteArr, 0, count, options);

        } catch (Exception e) {
            Log.d(Constants.TAG, "Explosion processing upgrade!", e);
            return null;
        }
    }


    public static int getDominantColor(Bitmap source) {
        return getDominantColor(source, true);
    }


    public static int getDominantColor(Bitmap source, boolean applyThreshold) {
        if (source == null)
            return Color.argb(255, 255, 255, 255);

        // Keep track of how many times a hue in a given bin appears in the image.
        // Hue values range [0 .. 360), so dividing by 10, we get 36 bins.
        int[] colorBins = new int[36];

        // The bin with the most colors. Initialize to -1 to prevent accidentally
        // thinking the first bin holds the dominant color.
        int maxBin = -1;

        // Keep track of sum hue/saturation/value per hue bin, which we'll use to
        // compute an average to for the dominant color.
        float[] sumHue = new float[36];
        float[] sumSat = new float[36];
        float[] sumVal = new float[36];
        float[] hsv = new float[3];

        int height = source.getHeight();
        int width = source.getWidth();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int row = 0; row < height; row += 2) {
            for (int col = 0; col < width; col += 2) {
                int c = pixels[col + row * width];
                // Ignore pixels with a certain transparency.
//                if (Color.alpha(c) < 128)
//                    continue;

                Color.colorToHSV(c, hsv);

                // If a threshold is applied, ignore arbitrarily chosen values for "white" and "black".
                if (applyThreshold && (hsv[1] <= 0.05f || hsv[2] <= 0.35f))
                    continue;

                // We compute the dominant color by putting colors in bins based on their hue.
                int bin = (int) Math.floor(hsv[0] / 10.0f);

                // Update the sum hue/saturation/value for this bin.
                sumHue[bin] = sumHue[bin] + hsv[0];
                sumSat[bin] = sumSat[bin] + hsv[1];
                sumVal[bin] = sumVal[bin] + hsv[2];

                // Increment the number of colors in this bin.
                colorBins[bin]++;

                // Keep track of the bin that holds the most colors.
                if (maxBin < 0 || colorBins[bin] > colorBins[maxBin])
                    maxBin = bin;
            }
        }

        // maxBin may never get updated if the image holds only transparent and/or black/white pixels.
        if (maxBin < 0)
            return Color.argb(255, 255, 255, 255);

        // Return a color with the average hue/saturation/value of the bin with the most colors.
        hsv[0] = sumHue[maxBin] / colorBins[maxBin];
        hsv[1] = sumSat[maxBin] / colorBins[maxBin];
        hsv[2] = sumVal[maxBin] / colorBins[maxBin];
        return Color.HSVToColor(hsv);
    }


    public static void changeImageViewDrawableColor(ImageView imageView, int color) {
        imageView.getDrawable().mutate().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }


	public static int neededRotation(File ff) {
		try {

			ExifInterface exif = new ExifInterface(ff.getAbsolutePath());
			int orientation = exif.getAttributeInt(
					ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

			if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
				return 270;
			}
			if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
				return 180;
			}
			if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
				return 90;
			}
			return 0;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
}
