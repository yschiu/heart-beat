package pt.fraunhofer.pulse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.MyCameraBridgeViewBase;
import org.opencv.android.MyCameraBridgeViewBase.CvCameraViewListener;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.highgui.Highgui;

import pt.fraunhofer.pulse.Pulse.Face;
import pt.fraunhofer.pulse.dialog.BpmDialog;
import pt.fraunhofer.pulse.dialog.ConfigDialog;
import pt.fraunhofer.pulse.view.BpmView;
import pt.fraunhofer.pulse.view.PulseView;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class App extends Activity implements CvCameraViewListener {

	private static final String TAG = "Pulse::App";

	private MyCameraBridgeViewBase camera;
	private BpmView bpmView;
	private PulseView pulseView;
	private Pulse pulse;

	private Paint faceBoxPaint;
	private Paint faceBoxTextPaint;

	private ConfigDialog configDialog;

	private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {

			case LoaderCallbackInterface.SUCCESS:
				loaderCallbackSuccess();
				break;

			default:
				super.onManagerConnected(status);
				break;
			}
		}
	};

	private void loaderCallbackSuccess() {
		System.loadLibrary("pulse");

		pulse = new Pulse();
		pulse.setMagnification(initMagnification);
		pulse.setMagnificationFactor(initMagnificationFactor);

		File dir = getDir("cascade", Context.MODE_PRIVATE);
		File file = createFileFromResource(dir, R.raw.lbpcascade_frontalface,
				"xml");
		pulse.load(file.getAbsolutePath());
		dir.delete();

		pulseView.setGridSize(pulse.getMaxSignalSize());

		camera.enableView();
	}

	private File createFileFromResource(File dir, int id, String extension) {
		String name = getResources().getResourceEntryName(id) + "." + extension;
		InputStream is = getResources().openRawResource(id);
		File file = new File(dir, name);

		try {
			FileOutputStream os = new FileOutputStream(file);

			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = is.read(buffer)) != -1) {
				os.write(buffer, 0, bytesRead);
			}
			is.close();
			os.close();
		} catch (IOException ex) {
			Log.e(TAG, "Failed to create file: " + file.getPath(), ex);
		}

		return file;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.app);

		camera = (MyCameraBridgeViewBase) findViewById(R.id.camera);
		camera.setCvCameraViewListener(this);
		camera.SetCaptureFormat(Highgui.CV_CAP_ANDROID_COLOR_FRAME_RGB);
		camera.setMaxFrameSize(600, 600);

		bpmView = (BpmView) findViewById(R.id.bpm);
		bpmView.setBackgroundColor(Color.DKGRAY);
		bpmView.setTextColor(Color.LTGRAY);

		pulseView = (PulseView) findViewById(R.id.pulse);

		faceBoxPaint = initFaceBoxPaint();
		faceBoxTextPaint = initFaceBoxTextPaint();
	}

	private static final String CAMERA_ID = "camera-id";
	private static final String FPS_METER = "fps-meter";
	private static final String MAGNIFICATION = "magnification";
	private static final String MAGNIFICATION_FACTOR = "magnification-factor";

	private boolean initMagnification = true;
	private int initMagnificationFactor = 100;

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		camera.setCameraId(savedInstanceState.getInt(CAMERA_ID));
		camera.setFpsMeter(savedInstanceState.getBoolean(FPS_METER));

		initMagnification = savedInstanceState.getBoolean(MAGNIFICATION,
				initMagnification);
		initMagnificationFactor = savedInstanceState.getInt(
				MAGNIFICATION_FACTOR, initMagnificationFactor);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt(CAMERA_ID, camera.getCameraId());
		outState.putBoolean(FPS_METER, camera.isFpsMeterEnabled());

		outState.putBoolean(MAGNIFICATION, pulse.hasMagnification());
		outState.putInt(MAGNIFICATION_FACTOR, pulse.getMagnificationFactor());
	}

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_5, this,
				loaderCallback);
	}

	private MediaPlayer mPlayer;
	private Thread recordingThread;

	@Override
	public void onPause() {
		if (camera != null) {
			camera.disableView();
		}
		bpmView.setNoBpm();
		pulseView.setNoPulse();

		if (mPlayer != null && mPlayer.isPlaying()) {
			mPlayer.stop();
			mPlayer.release();
			mPlayer = null;
		}
		if (recordingThread != null && recordingThread.isAlive()) {
			recordingThread.interrupt();
		}
		recordingThread = null;

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.app, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.record:
			onRecord(item);
			return true;
		case R.id.switch_camera:
			camera.switchCamera();
			return true;
		case R.id.config:
			if (configDialog == null)
				configDialog = new ConfigDialog();
			configDialog.show(getFragmentManager(), null);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private boolean recording = false;
	private List<Double> recordedBpms;
	private BpmDialog bpmDialog;
	private double recordedBpmAverage;
	private ArrayList<Double> mSignals = new ArrayList<Double>();
	private String mSignalStr;

	private long sTime;
	private long eTime;

	Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			Log.d(TAG, "[lynn] onRecord: recording = " + recording);
			onRecord((MenuItem) msg.obj);
		}
	};

	private void onRecord(final MenuItem item) {
		recording = !recording;
		if (recording) {
			mSignalStr = "";
			sTime = System.currentTimeMillis();
			item.setIcon(android.R.drawable.ic_media_pause);

			if (recordedBpms == null)
				recordedBpms = new LinkedList<Double>();
			else
				recordedBpms.clear();

			mSignals.clear();

			Log.d(TAG, "[lynn] recording: mPlayer = " + mPlayer);

			try {
				if (mPlayer != null && mPlayer.isPlaying())
					mPlayer.stop();
				mPlayer = MediaPlayer.create(this, R.raw.recording);
				mPlayer.start();
				Log.d(TAG, "[lynn] recording = " + recording);

				if (recordingThread != null && recordingThread.isAlive()) {
					recordingThread.interrupt();
				}
				recordingThread = new Thread(new Runnable() {

					@Override
					public void run() {
						try {
							Thread.sleep(8000);
							mPlayer.stop();
							Log.d(TAG, "[lynn] record stop: recording = "
									+ recording);
							if (recording) {
								handler.sendMessage(handler.obtainMessage(0,
										item));
							}
						} catch (InterruptedException e) {
							e.printStackTrace();
							Log.e(TAG, "[lynn] record: " + e.toString());
						}
					}
				});
				recordingThread.start();
			} catch (IllegalStateException e) {
				e.printStackTrace();
				Log.e(TAG, "[lynn] record: " + e.toString());
			}
		} else {
			eTime = System.currentTimeMillis();
			item.setIcon(android.R.drawable.ic_media_play);

			recordedBpmAverage = 0;
			for (double bpm : recordedBpms)
				recordedBpmAverage += bpm;
			recordedBpmAverage /= recordedBpms.size();

			new PostBPMToServer((eTime - sTime), recordedBpmAverage, mSignalStr)
					.start();

			if (mResultDialog == null) {
				mResultDialog = new Dialog(App.this);
				mResultDialog.setContentView(R.layout.bpm);
			}
			mResultDialog.setTitle(String.format(
					getString(R.string.average_bpm), (int) recordedBpmAverage));
			if (!mResultDialog.isShowing())
				mResultDialog.show();

			TextView textView = ((TextView) mResultDialog.findViewById(R.id.tv));

			if (mPlayer != null && mPlayer.isPlaying())
				mPlayer.stop();
			if ((int) recordedBpmAverage < 60) {
				mPlayer = MediaPlayer.create(App.this, R.raw.bomb);
				textView.setBackgroundResource(R.drawable.fail);
			} else {
				mPlayer = MediaPlayer.create(App.this, R.raw.got_u);
				textView.setBackgroundResource(R.drawable.success);
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			mPlayer.start();
		}
	}

	private Dialog mResultDialog;

	public double getRecordedBpmAverage() {
		return recordedBpmAverage;
	}

	public Pulse getPulse() {
		return pulse;
	}

	public MyCameraBridgeViewBase getCamera() {
		return camera;
	}

	private Rect noFaceRect;

	private Rect initNoFaceRect(int width, int height) {
		double r = pulse.getRelativeMinFaceSize();
		int x = (int) (width * (1. - r) / 2.);
		int y = (int) (height * (1. - r) / 2.);
		int w = (int) (width * r);
		int h = (int) (height * r);
		return new Rect(x, y, w, h);
	}

	@Override
	public void onCameraViewStarted(int width, int height) {
		Log.d(TAG, "onCameraViewStarted(" + width + ", " + height + ")");
		pulse.start(width, height);
		noFaceRect = initNoFaceRect(width, height);
	}

	@Override
	public void onCameraViewStopped() {
	}

	@Override
	public Mat onCameraFrame(Mat frame) {
		pulse.onFrame(frame);
		return frame;
	}

	@Override
	public void onCameraFrame(Canvas canvas) {
		Face face = getCurrentFace(pulse.getFaces()); // TODO support multiple
														// faces
		if (face != null) {
			onFace(canvas, face);
		} else {
			// draw no face box
			canvas.drawPath(createFaceBoxPath(noFaceRect), faceBoxPaint);
			canvas.drawText("Face here", canvas.getWidth() / 2f,
					canvas.getHeight() / 2f, faceBoxTextPaint);

			// no faces
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					bpmView.setNoBpm();
					pulseView.setNoPulse();
				}
			});
		}
	}

	private int currentFaceId = 0;

	private Face getCurrentFace(Face[] faces) {
		Face face = null;

		if (currentFaceId > 0) {
			face = findFace(faces, currentFaceId);
		}

		if (face == null && faces.length > 0) {
			face = faces[0];
		}

		if (face == null) {
			currentFaceId = 0;
		} else {
			currentFaceId = face.getId();
		}

		return face;
	}

	private Face findFace(Face[] faces, int id) {
		for (Face face : faces) {
			if (face.getId() == id) {
				return face;
			}
		}
		return null;
	}

	private void onFace(Canvas canvas, Face face) {
		// grab face box
		Rect box = face.getBox();

		// draw face box
		canvas.drawPath(createFaceBoxPath(box), faceBoxPaint);

		// update views
		if (face.existsPulse()) {
			final double bpm = face.getBpm();
			final double[] signal = face.getPulse();
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					bpmView.setBpm(bpm);
					pulseView.setPulse(signal);
					for (int i = 0; i < signal.length; i++) {
						if (mSignalStr != "")
							mSignalStr += ",";
						mSignalStr += signal[i];
					}
				}
			});
			if (recording) {
				recordedBpms.add(bpm);
			}
		} else {
			// draw hint text
			canvas.drawText("Hold still", box.x + box.width / 2f, box.y
					+ box.height / 2f - 20, faceBoxTextPaint);
			canvas.drawText("in a", box.x + box.width / 2f, box.y + box.height
					/ 2f, faceBoxTextPaint);
			canvas.drawText("bright place", box.x + box.width / 2f, box.y
					+ box.height / 2f + 20, faceBoxTextPaint);

			// no pulse
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					bpmView.setNoBpm();
					pulseView.setNoPulse();
				}
			});
		}
	}

	private Paint initFaceBoxPaint() {
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setColor(Color.WHITE);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(4);
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setStrokeJoin(Paint.Join.ROUND);
		p.setShadowLayer(2, 0, 0, Color.BLACK);
		return p;
	}

	private Paint initFaceBoxTextPaint() {
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setColor(Color.WHITE);
		p.setShadowLayer(2, 0, 0, Color.DKGRAY);
		p.setTypeface(Typeface.createFromAsset(getAssets(),
				"fonts/ds_digital/DS-DIGIB.TTF"));
		p.setTextAlign(Paint.Align.CENTER);
		p.setTextSize(20f);
		return p;
	}

	private Path createFaceBoxPath(Rect box) {
		float size = box.width * 0.25f;
		Path path = new Path();
		// top left
		path.moveTo(box.x, box.y + size);
		path.lineTo(box.x, box.y);
		path.lineTo(box.x + size, box.y);
		// top right
		path.moveTo(box.x + box.width, box.y + size);
		path.lineTo(box.x + box.width, box.y);
		path.lineTo(box.x + box.width - size, box.y);
		// bottom left
		path.moveTo(box.x, box.y + box.height - size);
		path.lineTo(box.x, box.y + box.height);
		path.lineTo(box.x + size, box.y + box.height);
		// bottom right
		path.moveTo(box.x + box.width, box.y + box.height - size);
		path.lineTo(box.x + box.width, box.y + box.height);
		path.lineTo(box.x + box.width - size, box.y + box.height);
		return path;
	}

	class PostBPMToServer extends Thread {

		double mBpm;
		String mSignals;
		long mTime;

		public PostBPMToServer(long time, double bpm, String signals) {
			super();
			mBpm = bpm;
			mSignals = signals;
			mTime = time;
		}

		@Override
		public void run() {
			super.run();
			String uri = "http://192.168.1.8:3000/hearts/new_from_mobile?duration="
					+ mTime + "&device=" + android.os.Build.MODEL;
			String payload = mBpm + ";" + mSignals;
			payload += ";";
			try {
				HttpClient httpClient = new DefaultHttpClient();
				HttpPost post = new HttpPost(uri);
				StringEntity str = new StringEntity(payload);
				post.setEntity(str);
				HttpResponse response = httpClient.execute(post);
				final String respo = response.getStatusLine().toString();
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						Toast.makeText(getApplicationContext(), respo,
								Toast.LENGTH_SHORT).show();
					}
				});
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}
}
