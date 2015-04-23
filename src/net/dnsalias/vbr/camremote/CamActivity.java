package net.dnsalias.vbr.camremote;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.util.Log;
import android.os.AsyncTask;

public class CamActivity extends Activity {
	private static final String TAG = "CamActivity";
	private Camera mCamera;
	private CameraPreview mPreview;
	private PictureCallback mPicture;
	private Button capture, switchCamera;
	private Context myContext;
	private LinearLayout cameraPreview;
	private boolean cameraFront = false;

	// client socket part ?
	private Socket socket;
	private static final int SERVERPORT = 5000;
	private static final String SERVER_IP = "10.0.2.2";


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_cam);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		myContext = this;
		initialize();    

		new Thread(new ClientTask()).start();

	}

	private int findFrontFacingCamera() {
		int cameraId = -1;
		// Search for the front facing camera
		int numberOfCameras = Camera.getNumberOfCameras();
		Log.d(TAG, "findFrontFacingCamera - num:  " + numberOfCameras);

		for (int i = 0; i < numberOfCameras; i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			Log.d(TAG, "findFrontFacingCamera - num:  " + i + " face: " + info.facing);
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				cameraId = i;
				cameraFront = true;
				break;
			}
		}
		return cameraId;
	}

	private int findBackFacingCamera() {
		int cameraId = -1;
		//Search for the back facing camera
		//get the number of cameras
		int numberOfCameras = Camera.getNumberOfCameras();
		Log.d(TAG, "findBackFacingCamera - num:  " + numberOfCameras);

		//for every camera check
		for (int i = 0; i < numberOfCameras; i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			Log.d(TAG, "findBackFacingCamera - num:  " + i + " face: " + info.facing);
			if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
				Log.d(TAG, "findBackFacingCamera - num:  " + i + " is backfacing ");
				cameraId = i;
				cameraFront = false;
				break;
			}
		}
		return cameraId;
	}

	public void onResume() {
		super.onResume();
		if (!hasCamera(myContext)) {
			Toast toast = Toast.makeText(myContext, "Sorry, your phone does not have a camera!", Toast.LENGTH_LONG);
			Log.d(TAG, "onResume - Sorry, your phone does not have a camera!");
			toast.show();
			finish();
		}
		if (mCamera == null) {
			//if the front facing camera does not exist
			if (findFrontFacingCamera() < 0) {
				Toast.makeText(this, "No front facing camera found.", Toast.LENGTH_LONG).show();
				Log.d(TAG, "onResume - No front facing camera found.");
				switchCamera.setVisibility(View.GONE);
			}
			mCamera = Camera.open(findBackFacingCamera());
			mPicture = getPictureCallback();
			mPreview.refreshCamera(mCamera,0,0);
		}
	}

	public void initialize() {
		cameraPreview = (LinearLayout) findViewById(R.id.camera_preview);
		mPreview = new CameraPreview(myContext, mCamera);
		cameraPreview.addView(mPreview);

		capture = (Button) findViewById(R.id.button_capture);
		capture.setOnClickListener(captureListener);

		switchCamera = (Button) findViewById(R.id.button_ChangeCamera);
		switchCamera.setOnClickListener(switchCameraListener);
	}

	OnClickListener switchCameraListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			//get the number of cameras
			int camerasNumber = Camera.getNumberOfCameras();
			if (camerasNumber > 1) {
				//release the old camera instance
				//switch camera, from the front and the back and vice versa

				releaseCamera();
				chooseCamera();
			} else {
				Toast toast = Toast.makeText(myContext, "Sorry, your phone has only one camera!", Toast.LENGTH_LONG);
				Log.d(TAG, "switchCameraListener - Sorry, your phone has only one camera!");
				toast.show();
			}
		}
	};

	public void chooseCamera() {
		//if the camera preview is the front
		Log.d(TAG, "chooseCamera - check : " + cameraFront);
		if (cameraFront) {
			Log.d(TAG, "chooseCamera -  use front camera ");
			int cameraId = findFrontFacingCamera();
			if (cameraId >= 0) {
				//open the backFacingCamera
				//set a picture callback
				//refresh the preview

				mCamera = Camera.open(cameraId);
				mPicture = getPictureCallback();
				mPreview.refreshCamera(mCamera,0,0);
			}
		} else {
			Log.d(TAG, "chooseCamera -  use back camera ");
			int cameraId = findBackFacingCamera();
			if (cameraId >= 0) {
				//open the backFacingCamera
				//set a picture callback
				//refresh the preview

				mCamera = Camera.open(cameraId);
				mPicture = getPictureCallback();
				mPreview.refreshCamera(mCamera,0,0);
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		//when on Pause, release camera in order to be used from other applications
		releaseCamera();
	}

	private boolean hasCamera(Context context) {
		//check if the device has camera
		/*
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        } else {
            Log.d(TAG, "hasCamera -  no camera ?");
            //return false;
            return true;
        }
		 */
		/* for now ? */
		int numCameras = Camera.getNumberOfCameras();
		if (numCameras > 0) {
			return true;
		} else
			return false;
	}

	private PictureCallback getPictureCallback() {
		PictureCallback picture = new PictureCallback() {

			@Override
			public void onPictureTaken(byte[] data, Camera camera) {
				//make a new picture file

				File pictureFile = getOutputMediaFile();

				if (pictureFile == null) {
					return;
				}
				try {
					//write the file
					FileOutputStream fos = new FileOutputStream(pictureFile);
					fos.write(data);
					fos.close();
					Toast toast = Toast.makeText(myContext, "Picture saved: " + pictureFile.getName(), Toast.LENGTH_LONG);
					toast.show();

				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				}

				// use async for job
				//new SaveImageTask().execute(data);

				//refresh camera to continue preview
				//mPreview.refreshCamera(mCamera,0,0);
				Log.d(TAG, "onPictureTaken - jpeg wrote bytes: " + data.length + " to " + pictureFile.getAbsolutePath());
				//Log.d(TAG, "onPictureTaken - jpeg");
			}
		};
		return picture;
	}

	OnClickListener captureListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Log.d(TAG, "onClick Camera");
			//mCamera.stopPreview();

			Camera.Parameters mParameters = mCamera.getParameters();

			// get the best picture size ...
			List<Size> sizes = mParameters.getSupportedPictureSizes();
			int i = 0;
			int dim = 0;
			for (Camera.Size cs : sizes) {
				if(cs.width  >= 1024 && cs.height >= 768) dim = i;
				Log.d(TAG, "onClick Camera - supports:(" + (i++) + ") " + cs.width + "x" + cs.height);
			}
			Size size = sizes.get(0); // TODO : better
			mParameters.setPictureSize(size.width, size.height);

			Size cs = mParameters.getPictureSize();
			Log.d(TAG, "onClick Camera - current size : " + cs.width + " x " + cs.height );
			Log.d(TAG, "onClick Camera - current focus : " + mParameters.getFocusMode());
			Log.d(TAG, "onClick Camera - current expo  : " + mParameters.getExposureCompensation());
			Log.d(TAG, "onClick Camera - current zoom  : " + mParameters.getZoom());

			mParameters.setJpegQuality(100);//a value between 1 and 100
			mParameters.setPictureFormat(PixelFormat.JPEG);
			mCamera.setParameters(mParameters);

			//mCamera.startPreview();
			mCamera.takePicture(null, null, mPicture);

		}
	};

	/*
	 * async mode ?
	 */

	private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

		@Override
		protected Void doInBackground(byte[]... data) {
			FileOutputStream outStream = null;

			// Write to SD Card
			try {
				File pictureFile = getOutputMediaFile();

				outStream = new FileOutputStream(pictureFile);
				outStream.write(data[0]);
				outStream.flush();
				outStream.close();

				Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to " + pictureFile.getAbsolutePath());

				//refreshGallery(outFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			}
			return null;
		}

	}

	//make picture and save to a folder
	private static File getOutputMediaFile() {
		//make a new file directory inside the "sdcard" folder
		File mediaStorageDir = new File("/sdcard/", "JCG Camera");

		//if this "JCGCamera folder does not exist
		if (!mediaStorageDir.exists()) {
			//if you cannot make this folder return
			if (!mediaStorageDir.mkdirs()) {
				return null;
			}
		}

		//take the current timeStamp
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File mediaFile;
		//and make a media file:
		mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");

		return mediaFile;
	}

	private void releaseCamera() {
		// stop and release camera
		if (mCamera != null) {
			mCamera.release();
			mCamera = null;
		}
	}

	/*
	 * other callbacks ...
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.cam, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.focus) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/*
	 * client socket handling
	 */
	public class ClientTask implements Runnable {

		@Override
		public void run() {
			try {
				InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
				socket = new Socket(serverAddr, SERVERPORT);
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}

		}

	}
}
