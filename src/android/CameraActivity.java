package com.cordovaplugincamerapreview;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;


public class CameraActivity extends Fragment {
    private static final int FLASH_OFF = 0;
    private static final int FLASH_ON = 1;
    private static final int FLASH_AUTO = 2;
    private static final int FOCUS_AUTO = 0;
    private static final int FOCUS_CONTINUOUS = 1;
    private static final String TAG = "CameraActivity";
    String defaultCamera;
    boolean tapToTakePicture;
    boolean dragEnabled;
    private CameraPreviewListener eventListener;
    private FrameLayout frameContainerLayout;
    private Preview mPreview;
    private boolean canTakePicture = true;
    private View view;
    private Camera.Parameters cameraParameters;
    private Camera mCamera;
    private int numberOfCameras;
    private int cameraCurrentlyLocked;
    // The first rear facing camera
    private int defaultCameraId;
    private int width;
    private int height;
    private int x;
    private int y;
    private String appResourcesPackage;
    private int currentFlashMode = FLASH_OFF;
    private int currentFocusMode = FOCUS_AUTO;

    void setEventListener(CameraPreviewListener listener) {
        eventListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        appResourcesPackage = getActivity().getPackageName();

        // Inflate the layout for this fragment
        view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);
        createCameraPreview();
        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private void createCameraPreview() {
        if (mPreview == null) {
            setDefaultCameraId();

            //set box position and size
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
            layoutParams.setMargins(x, y, 0, 0);
            frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
            frameContainerLayout.setLayoutParams(layoutParams);

            //video view
            mPreview = new Preview(getActivity());
            FrameLayout mainLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("video_view", "id", appResourcesPackage));
            mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            mainLayout.addView(mPreview);
            mainLayout.setEnabled(false);

            final GestureDetector gestureDetector = new GestureDetector(getActivity().getApplicationContext(), new TapGestureDetector());

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    frameContainerLayout.setClickable(true);
                    frameContainerLayout.setOnTouchListener(new View.OnTouchListener() {

                        private int mLastTouchX;
                        private int mLastTouchY;
                        private int mPosX = 0;
                        private int mPosY = 0;

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();


                            boolean isSingleTapTouch = gestureDetector.onTouchEvent(event);
                            if (event.getAction() != MotionEvent.ACTION_MOVE && isSingleTapTouch) {
                                if (tapToTakePicture) {
                                    takePicture(0, 0);
                                }
                                return true;
                            } else {
                                if (dragEnabled) {
                                    int x;
                                    int y;

                                    switch (event.getAction()) {
                                        case MotionEvent.ACTION_DOWN:
                                            if (mLastTouchX == 0 || mLastTouchY == 0) {
                                                mLastTouchX = (int) event.getRawX() - layoutParams.leftMargin;
                                                mLastTouchY = (int) event.getRawY() - layoutParams.topMargin;
                                            } else {
                                                mLastTouchX = (int) event.getRawX();
                                                mLastTouchY = (int) event.getRawY();
                                            }
                                            break;
                                        case MotionEvent.ACTION_MOVE:

                                            x = (int) event.getRawX();
                                            y = (int) event.getRawY();

                                            final float dx = x - mLastTouchX;
                                            final float dy = y - mLastTouchY;

                                            mPosX += dx;
                                            mPosY += dy;

                                            layoutParams.leftMargin = mPosX;
                                            layoutParams.topMargin = mPosY;

                                            frameContainerLayout.setLayoutParams(layoutParams);

                                            // Remember this touch position for the next move event
                                            mLastTouchX = x;
                                            mLastTouchY = y;

                                            break;
                                        default:
                                            break;
                                    }
                                }
                            }
                            return true;
                        }
                    });
                }
            });
        }
    }

    private void setDefaultCameraId() {

        // Find the total number of cameras available
        numberOfCameras = Camera.getNumberOfCameras();

        int camId = defaultCamera.equals("front") ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;

        // Find the ID of the default camera
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == camId) {
                defaultCameraId = camId;
                break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mCamera = Camera.open(defaultCameraId);

        if (cameraParameters != null) {
            mCamera.setParameters(cameraParameters);
        }

        // set continuous autofocus
        setFocusMode(currentFocusMode);
        setFlashMode(currentFlashMode);

        cameraCurrentlyLocked = defaultCameraId;

        if (mPreview.mPreviewSize == null) {
            mPreview.setCamera(mCamera, cameraCurrentlyLocked);
        } else {
            mPreview.switchCamera(mCamera, cameraCurrentlyLocked);
            mCamera.startPreview();
        }

        Log.d(TAG, "cameraCurrentlyLocked:" + cameraCurrentlyLocked);

        final FrameLayout frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
        ViewTreeObserver viewTreeObserver = frameContainerLayout.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    frameContainerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    frameContainerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    final RelativeLayout frameCamContainerLayout = (RelativeLayout) view.findViewById(getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage));

                    FrameLayout.LayoutParams camViewLayout = new FrameLayout.LayoutParams(frameContainerLayout.getWidth(), frameContainerLayout.getHeight());
                    camViewLayout.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
                    frameCamContainerLayout.setLayoutParams(camViewLayout);
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        if (mCamera != null) {
            mPreview.setCamera(null, -1);
            mCamera.release();
            mCamera = null;
        }
    }

    Camera getCamera() {
        return mCamera;
    }

    void switchCamera() {
        // check for availability of multiple cameras
        if (numberOfCameras == 1) {
            //There is only one camera available
            return;
        }
        Log.d(TAG, "numberOfCameras: " + numberOfCameras);

        // OK, we have multiple cameras.
        // Release this camera -> cameraCurrentlyLocked
        if (mCamera != null) {
            mCamera.stopPreview();
            mPreview.setCamera(null, -1);
            mCamera.release();
            mCamera = null;
        }

        // Acquire the next camera and request Preview to reconfigure
        // parameters.
        mCamera = Camera.open((cameraCurrentlyLocked + 1) % numberOfCameras);

        if (cameraParameters != null) {
            mCamera.setParameters(cameraParameters);
        }

        cameraCurrentlyLocked = (cameraCurrentlyLocked + 1) % numberOfCameras;
        mPreview.switchCamera(mCamera, cameraCurrentlyLocked);

        Log.d(TAG, "cameraCurrentlyLocked new: " + cameraCurrentlyLocked);

        // Start the preview
        mCamera.startPreview();
    }

    void setFlashMode(int flashMode) {

        Camera.Parameters parameters = mCamera.getParameters();
        List<String> supportedFlashModes = parameters.getSupportedFlashModes();

        if (supportedFlashModes != null) {
            if (flashMode == FLASH_OFF && supportedFlashModes.contains(Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            } else if (flashMode == FLASH_ON && supportedFlashModes.contains(Parameters.FLASH_MODE_ON)) {
                parameters.setFlashMode(Parameters.FLASH_MODE_ON);
            } else if (flashMode == FLASH_AUTO && supportedFlashModes.contains(Parameters.FLASH_MODE_AUTO)) {
                parameters.setFlashMode(Parameters.FLASH_MODE_AUTO);
            } else if (flashMode == FLASH_AUTO && supportedFlashModes.contains(Parameters.FLASH_MODE_ON)) {
                parameters.setFlashMode(Parameters.FLASH_MODE_ON);
            }
            mCamera.setParameters(parameters);
        }

        Log.d(TAG, "flashmode: " + flashMode);

        currentFlashMode = flashMode;
    }

    private void setFocusMode(int focusMode) {
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> supportedFocusModes = parameters.getSupportedFocusModes();

        if (supportedFocusModes != null) {
            if (focusMode == FOCUS_AUTO && supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
            } else if (focusMode == FOCUS_CONTINUOUS && supportedFocusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            mCamera.setParameters(parameters);
        }

        Log.d(TAG, "focusMode: " + focusMode);

        currentFocusMode = focusMode;
    }

    void setCameraParameters(Camera.Parameters params) {
        cameraParameters = params;

        if (mCamera != null && cameraParameters != null) {
            mCamera.setParameters(cameraParameters);
        }
    }

    public void takePicture(final double maxWidth, final double maxHeight){
        Log.d(TAG, "picture taken");

        final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
        if(mPreview != null) {

            if(!canTakePicture)
                return;

            canTakePicture = false;

            mPreview.setOneShotPreviewCallback(new Camera.PreviewCallback() {

                @Override
                public void onPreviewFrame(final byte[] data, final Camera camera) {

                    new Thread() {
                        public void run() {

                            //raw picture
                            byte[] bytes = mPreview.getFramePicture(data, camera); // raw bytes from preview
                            final Bitmap pic = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); // Bitmap from preview

                            //scale down
                            float scale = (float) pictureView.getWidth() / (float) pic.getWidth();
                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(pic, (int) (pic.getWidth() * scale), (int) (pic.getHeight() * scale), false);

                            final Matrix matrix = new Matrix();
                            if (cameraCurrentlyLocked == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                Log.d(TAG, "mirror y axis");
                                matrix.preScale(-1.0f, 1.0f);
                            }
                            Log.d(TAG, "preRotate " + mPreview.getDisplayOrientation() + "deg");
                            matrix.postRotate(mPreview.getDisplayOrientation());

                            final Bitmap fixedPic = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, false);
                            final Rect rect = new Rect(mPreview.mSurfaceView.getLeft(), mPreview.mSurfaceView.getTop(), mPreview.mSurfaceView.getRight(), mPreview.mSurfaceView.getBottom());

                            Log.d(TAG, mPreview.mSurfaceView.getLeft() + " " + mPreview.mSurfaceView.getTop() + " " + mPreview.mSurfaceView.getRight() + " " + mPreview.mSurfaceView.getBottom());

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pictureView.setImageBitmap(fixedPic);
                                    pictureView.layout(rect.left, rect.top, rect.right, rect.bottom);

                                    Bitmap finalPic = pic;

                                    Bitmap originalPicture = Bitmap.createBitmap(finalPic, 0, 0, (int) (finalPic.getWidth()), (int) (finalPic.getHeight()), matrix, false);

                                    generatePictureFromView(originalPicture);
                                    canTakePicture = true;
                                    camera.startPreview();
                                }
                            });
                        }
                    }.start();
                }
            });
        } else {
            canTakePicture = true;
        }
    }
    private File storeImage(Bitmap image, String suffix) {
        File pictureFile = getOutputMediaFile(suffix);
        if (pictureFile != null) {
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                image.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.close();
                return pictureFile;
            } catch (Exception ex) {
            }
        }
        return null;
    }
    private void generatePictureFromView(final Bitmap originalPicture) {

        //        final Bitmap image;
        final FrameLayout cameraLoader = (FrameLayout) view.findViewById(getResources().getIdentifier("camera_loader", "id", appResourcesPackage));
        cameraLoader.setVisibility(View.VISIBLE);
        final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
        new Thread() {
            public void run() {

                try {
                    final File originalPictureFile = storeImage(originalPicture, "_original");

                    eventListener.onPictureTaken(originalPictureFile.getAbsolutePath());

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cameraLoader.setVisibility(View.INVISIBLE);
                            pictureView.setImageBitmap(null);
                        }
                    });
                } catch (Exception e) {
                    //An unexpected error occurred while saving the picture.
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cameraLoader.setVisibility(View.INVISIBLE);
                            pictureView.setImageBitmap(null);
                        }
                    });
                }
            }
        }.start();
    }
//  public boolean hasFrontCamera(){
//    return getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
//  }

//  public Bitmap cropBitmap(Bitmap bitmap, Rect rect){
//    int w = rect.right - rect.left;
//    int h = rect.bottom - rect.top;
//    Bitmap ret = Bitmap.createBitmap(w, h, bitmap.getConfig());
//    Canvas canvas= new Canvas(ret);
//    canvas.drawBitmap(bitmap, -rect.left, -rect.top, null);
//    return ret;
//  }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

//  private void generatePictureFromView(final Bitmap originalPicture, final Bitmap picture){
//
//    final FrameLayout cameraLoader = (FrameLayout)view.findViewById(getResources().getIdentifier("camera_loader", "id", appResourcesPackage));
//    cameraLoader.setVisibility(View.VISIBLE);
//    final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
//    new Thread() {
//      public void run() {
//
//        try {
//          final File picFile = storeImage(picture, "_preview");
//          final File originalPictureFile = storeImage(originalPicture, "_original");
//
//          eventListener.onPictureTaken(originalPictureFile.getAbsolutePath());
//
//          getActivity().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//              cameraLoader.setVisibility(View.INVISIBLE);
//              pictureView.setImageBitmap(null);
//            }
//          });
//        }
//        catch(Exception e){
//          //An unexpected error occurred while saving the picture.
//          getActivity().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//              cameraLoader.setVisibility(View.INVISIBLE);
//              pictureView.setImageBitmap(null);
//            }
//          });
//        }
//      }
//    }.start();
//  }
private File getOutputMediaFile(String suffix){

    File mediaStorageDir = getActivity().getApplicationContext().getFilesDir();
    /*if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED && Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED_READ_ONLY) {
      mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/Android/data/" + getActivity().getApplicationContext().getPackageName() + "/Files");
      }*/
    if (! mediaStorageDir.exists()){
        if (! mediaStorageDir.mkdirs()){
            return null;
        }
    }
    // Create a media file name
    String timeStamp = new SimpleDateFormat("dd_MM_yyyy_HHmm_ss").format(new Date());
    File mediaFile;
    String mImageName = "camerapreview_" + timeStamp + suffix + ".jpg";
    mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
    return mediaFile;
}
//  private File getOutputMediaFile(String suffix){
//
//    File mediaStorageDir = getActivity().getApplicationContext().getFilesDir();
//    /*if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED && Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED_READ_ONLY) {
//      mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/Android/data/" + getActivity().getApplicationContext().getPackageName() + "/Files");
//      }*/
//    if (! mediaStorageDir.exists()){
//      if (! mediaStorageDir.mkdirs()){
//        return null;
//      }
//    }
//    // Create a media file name
//    String timeStamp = new SimpleDateFormat("dd_MM_yyyy_HHmm_ss").format(new Date());
//    File mediaFile;
//    String mImageName = "camerapreview_" + timeStamp + suffix + ".jpg";
//    mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
//    return mediaFile;
//  }

//  private File storeImage(Bitmap image, String suffix) {
//    File pictureFile = getOutputMediaFile(suffix);
//    if (pictureFile != null) {
//      try {
//        FileOutputStream fos = new FileOutputStream(pictureFile);
//        image.compress(Bitmap.CompressFormat.JPEG, 80, fos);
//        fos.close();
//        return pictureFile;
//      }
//      catch (Exception ex) {
//      }
//    }
//    return null;
//  }

//  public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
//    // Raw height and width of image
//    final int height = options.outHeight;
//    final int width = options.outWidth;
//    int inSampleSize = 1;
//
//    if (height > reqHeight || width > reqWidth) {
//
//      final int halfHeight = height / 2;
//      final int halfWidth = width / 2;
//
//      // Calculate the largest inSampleSize value that is a power of 2 and keeps both
//      // height and width larger than the requested height and width.
//      while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
//        inSampleSize *= 2;
//      }
//    }
//    return inSampleSize;
//  }

//  private Bitmap loadBitmapFromView(View v) {
//    Bitmap b = Bitmap.createBitmap( v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
//    Canvas c = new Canvas(b);
//    v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
//    v.draw(c);
//    return b;
//  }

    interface CameraPreviewListener {
        void onPictureTaken(String originalPicturePath);
    }
}
