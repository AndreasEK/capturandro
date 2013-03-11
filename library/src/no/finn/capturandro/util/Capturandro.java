package no.finn.capturandro.util;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;

import no.finn.capturandro.ICapturandroEventHandler;
import no.finn.capturandro.ICapturandroPicasaEventHandler;
import no.finn.capturandro.asynctask.DownloadFileAsyncTask;
import no.finn.capturandro.exception.CapturandroException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static no.finn.capturandro.Config.STORED_IMAGE_HEIGHT;
import static no.finn.capturandro.Config.STORED_IMAGE_WIDTH;

public class Capturandro {

    private final int IMAGE_FROM_CAMERA_RESULT = 1;
    private final int IMAGE_FROM_GALLERY_RESULT = 2;

    private static String[] PICASA_CONTENT_PROVIDERS = {
                "content://com.android.gallery3d.provider",
                "content://com.google.android.gallery3d",
                "content://com.android.sec.gallery3d",
                "content://com.sec.android.gallery3d"
    };

    private ICapturandroEventHandler eventHandler;
    private ICapturandroPicasaEventHandler picasaEventHandler;

    private String filename;
    private File storageDirectory;
    private Activity activity;


    public static class Builder {
        private ICapturandroEventHandler eventHandler;
        private ICapturandroPicasaEventHandler picasaEventHandler;
        private String filename;
        private File storageDirectory;
        private  Activity activity;

        public Builder(Activity activity){
            this.activity = activity;
        }
        public Builder withEventHandler(ICapturandroEventHandler eventHandler){
            this.eventHandler = eventHandler;

            return this;
        }

        public Builder withPicasaEventHandler(ICapturandroPicasaEventHandler picasaEventHandler){
            this.picasaEventHandler = picasaEventHandler;

            return this;
        }

        public Builder withFilename(String filename){
            this.filename = filename;

            return this;
        }

        public Builder withStorageDirectory(File path){
            this.storageDirectory = path;

            return this;
        }

        public Capturandro build(){
            return new Capturandro(this);
        }
    }

    public Capturandro(Builder builder) {
        this.activity = builder.activity;
        this.eventHandler = builder.eventHandler;
        this.picasaEventHandler = builder.picasaEventHandler;
        this.filename = builder.filename;
        this.storageDirectory = builder.storageDirectory;
    }

    public void importImageFromCamera(String filename) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(getStorageDirectory(), filename)));
        this.filename = filename;

        activity.startActivityForResult(intent, IMAGE_FROM_CAMERA_RESULT);
    }

    public void importImageFromGallery(String filename) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        this.filename = filename;
        activity.startActivityForResult(intent, IMAGE_FROM_GALLERY_RESULT);
    }

    public void onActivityResult(int reqCode, int resultCode, Intent intent) throws IllegalArgumentException {
        if (eventHandler == null){
            throw new IllegalStateException("Unable to import image. Did you remember to implement ICapturandroiEventHandler?");
        }

        switch (reqCode) {
            case IMAGE_FROM_CAMERA_RESULT:
                if (resultCode == Activity.RESULT_OK) {
                    if (filename != null){
                        File fileToStore = new File(getStorageDirectory(), filename);
                        try {
                            fileToStore.createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            eventHandler.onImportFailure(e);
                        }
                        saveBitmap(filename, fileToStore, fileToStore);
                        eventHandler.onImportSuccess(filename);
                    } else {
                        // Throw exception saying image couldnt be added. Or something. showImageCouldNotBeAddedDialog();
                        eventHandler.onImportFailure(new CapturandroException("Image could not be added"));
                    }
                }

                break;
            case IMAGE_FROM_GALLERY_RESULT:
                Uri selectedImage = null;

                if (intent != null){
                    selectedImage = intent.getData();
                }

                if (isUserAttemptingToAddVideo(selectedImage)){
                    eventHandler.onImportFailure(new CapturandroException("Video can't be added"));
                    break;
                }

                if (selectedImage != null) {
                    handleImageFromGallery(selectedImage, filename);
                }

                break;
        }
    }

    private void saveBitmap(String imageFilename, File inFile, File outFile) {
        // Store Exif information as it is not kept when image is copied
        ExifInterface exifInterface = BitmapUtil.getExifFromFile(inFile);

        try {
            if (exifInterface != null){
                BitmapUtil.resizeAndRotateAndSaveBitmapFile(inFile, outFile, exifInterface, STORED_IMAGE_WIDTH, STORED_IMAGE_HEIGHT);
            } else {
                BitmapUtil.resizeAndSaveBitmapFile(outFile, STORED_IMAGE_WIDTH, STORED_IMAGE_HEIGHT);
            }

            eventHandler.onImportSuccess(imageFilename);

        } catch (IllegalArgumentException e) {
            eventHandler.onImportFailure(e);
        }
    }

    private void handleImageFromGallery(Uri selectedImage, String filename) {
        final String[] filePathColumn = {
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME
        };

        Cursor cursor = activity.getContentResolver().query(selectedImage, filePathColumn, null, null, null);

        if (cursor != null) {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);

            if (isPicasaAndroid3Image(selectedImage)){
                fetchPicasaAndroid3Image(selectedImage, filename, cursor);
            } else {
                fetchLocalGalleryImageFile(filename, cursor, columnIndex);
            }

            cursor.close();
        } else if (isPicasaAndroid2Image(selectedImage)) {
            fetchPicasaImage(selectedImage, filename);
        }
    }

    private boolean isPicasaAndroid2Image(Uri selectedImage) {
        return selectedImage != null && selectedImage.toString().length() > 0;
    }

    private boolean isPicasaAndroid3Image(Uri selectedImage) {
        for (int i = 0; i < PICASA_CONTENT_PROVIDERS.length; i++){
            if (selectedImage.toString().startsWith(PICASA_CONTENT_PROVIDERS[i])){
                return true;
            }
        }

        return false;
    }

    private void fetchPicasaAndroid3Image(Uri selectedImage, String filename, Cursor cursor) {
        int columnIndex;
        columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
        if (columnIndex != -1) {
            fetchPicasaImage(selectedImage, filename);
        }
    }

    private void fetchPicasaImage(Uri selectedImage, String filename) {
        if (picasaEventHandler == null){
            throw new IllegalStateException("Unable to import image. Did you remember to implement ICapturandroPicasaEventHandler?");
        }

        new DownloadFileAsyncTask(activity, selectedImage, filename, picasaEventHandler).execute();
    }


    private void fetchLocalGalleryImageFile(String filename, Cursor cursor, int columnIndex) {
        // Resize and save so that the image is still kept if the user deletes it from the Gallery
        File inFile = new File(cursor.getString(columnIndex));
        File outFile = new File(activity.getExternalCacheDir(), filename);

        saveBitmap(filename, inFile, outFile);
        eventHandler.onImportSuccess(filename);
    }

    private boolean isUserAttemptingToAddVideo(Uri selectedImage) {
        return selectedImage != null && selectedImage.toString().startsWith("content://media/external/video/");

    }

    public ArrayList<Uri> getImagesFromIntent(Intent intent) {
        ArrayList<Uri> imageUris = new ArrayList<Uri>();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                imageUris.add((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM));
            }
        }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }
        }
        return imageUris;
    }

    public void handleSendImage(Uri imageUris, String filename) {
        handleImageFromGallery(imageUris,  filename);
    }


    public File getStorageDirectory() {
        if (storageDirectory == null){
            return activity.getExternalCacheDir();
        } else {
            return storageDirectory;
        }
    }

}