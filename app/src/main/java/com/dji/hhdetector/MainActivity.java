package com.dji.hhdetector;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jsibbold.zoomage.ZoomageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Permission;
import java.util.ArrayList;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private final OkHttpClient client = new OkHttpClient();
    public static int index = 0;
    public final String dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INTERNET}, PackageManager.PERMISSION_GRANTED);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        Button button_load = findViewById(R.id.button_load);
        Button button_delete = findViewById(R.id.button_delete);
        Button button_shoot = findViewById(R.id.button_shoot);
        Button button_send = findViewById(R.id.button_send);

        Spinner spinner = findViewById(R.id.spinner);

        ArrayList<String> datasetList = new ArrayList<>();
        datasetList.add("XL-2021");
        datasetList.add("XL-2022");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, datasetList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        File file = new File(dir + index + ".jpg");
        file.delete();

        button_load.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Load", Toast.LENGTH_SHORT).show();

                button_send.setEnabled(true);
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, index);
            }
        });

        button_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Delete", Toast.LENGTH_SHORT).show();

                button_send.setEnabled(true);
                File newFile = new File(dir + index + ".jpg");

                try {
                    newFile.delete();
                    ZoomageView imageView = findViewById(R.id.image_view);
                    imageView.setImageBitmap(null);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        button_shoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Shoot", Toast.LENGTH_SHORT).show();

                button_send.setEnabled(true);
                File newFile = new File(dir + index + ".jpg");

                try {
                    newFile.delete();
                    newFile.createNewFile();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }

                Uri outputFileUri = Uri.fromFile(newFile);

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, index);
                }
            }
        });

        button_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Send", Toast.LENGTH_SHORT).show();

                try {
                    makePost();
                } catch (IOException | JSONException e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadAndStoreImage(Uri sourceUri) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = getContentResolver().openInputStream(sourceUri);
            File destinationFile = new File(dir, index + ".jpg");
            out = new FileOutputStream(destinationFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static int getOrientation(Uri uri) {
        try {
            ExifInterface exifInterface = new ExifInterface(uri.getPath());
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0; // Default to 0 if there's an error
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == index && resultCode == RESULT_OK) {
            ZoomageView imageView = findViewById(R.id.image_view);
            Uri uri = null;
            if (data != null && data.getData() != null) {
                uri = data.getData();
                loadAndStoreImage(uri);
            } else {
                File file = new File(dir + index + ".jpg");
                uri = Uri.fromFile(file);
            }
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                bitmap = fixOrientation(bitmap, uri);
                imageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap fixOrientation(Bitmap bitmap, Uri uri) {
        int orientation = getOrientation(uri); // You need to implement the method to get the orientation.

        // Rotate the bitmap if needed
        if (orientation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(orientation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        return bitmap;
    }

    private void makePost() throws IOException, JSONException {
        File file = new File(dir + 0 + ".jpg");

        RequestBody requestBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", file.getName(),
                    RequestBody.create(MediaType.parse("image/jpeg"), file))
            .build();

        TextView input_ip = findViewById(R.id.input_ip);
//        TextView input_port = findViewById(R.id.input_port);
        Spinner spinner = findViewById(R.id.spinner);

        String selectedDataset = spinner.getSelectedItem().toString();

        String url = "http://" + input_ip.getText().toString() + ":5000" +
//                input_port.getText().toString()
                "/android/" + selectedDataset;

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            ZoomageView imageView = findViewById(R.id.image_view);

            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(file));
            bitmap = fixOrientation(bitmap, Uri.fromFile(file));

            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            String jsonArrayString = response.body().string();
            JSONArray jsonArray = new JSONArray(jsonArrayString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                // access the elements of the individual JSONObjects
                float x_min = Float.parseFloat(jsonObject.getString("xmin"));
                float y_min = Float.parseFloat(jsonObject.getString("ymin"));
                float x_max = Float.parseFloat(jsonObject.getString("xmax"));
                float y_max = Float.parseFloat(jsonObject.getString("ymax"));
                String confidence = jsonObject.getString("confidence");
                String objClass = jsonObject.getString("class");

                String res = "(" + x_min + "," + x_max + ") ";
                res += "(" + y_min + "," + y_max + ") ";
                res += "confidence=" + confidence + ", class=" + objClass;

                Canvas canvas = new Canvas(mutableBitmap);
                Paint boundingBoxPaint = new Paint();
                boundingBoxPaint.setColor(Color.RED);
                boundingBoxPaint.setStyle(Paint.Style.STROKE);
                boundingBoxPaint.setStrokeWidth(10);
                canvas.drawRect(x_min, y_min, x_max, y_max, boundingBoxPaint);

                Paint textPaint = new Paint();
                textPaint.setColor(Color.RED);
                textPaint.setTextSize(50f);
                canvas.drawText(Float.parseFloat(confidence.substring(0, 4)) * 100 + "%", x_max+50, y_min, textPaint);

                FileOutputStream fos = new FileOutputStream(dir + index + ".jpg");
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();

//                Toast.makeText(MainActivity.this, res, Toast.LENGTH_SHORT).show();
            }

            if (jsonArray.length() == 0) {
                Toast.makeText(MainActivity.this, "Nothing found!", Toast.LENGTH_SHORT).show();
            }

            imageView.setImageDrawable(null);
            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(file));
            bitmap = fixOrientation(bitmap, Uri.fromFile(file));

            imageView.setImageBitmap(bitmap);

//            Button button_send = findViewById(R.id.button_send);
//            button_send.setEnabled(false);
        }
    }
}