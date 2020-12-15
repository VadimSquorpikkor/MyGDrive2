package com.squorpikkor.mygdrive2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;

import static android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION;

public class MainActivity extends AppCompatActivity {

    final String API_KEI = "AIzaSyABrX6WwM2WQC2djvG6auBwgTQqZzL5_kw";

    private static final String TAG = "..........";

    private static final int REQUEST_CODE_SIGN_IN = 1;
    private static final int REQUEST_CODE_OPEN_DOCUMENT = 2;
    private static final int REQUEST_CODE_OPEN_DOCUMENT_TO_UPLOAD = 3;

    private static final int MANAGE_ALL_FILES_ACCESS_PERMISSION = 4;
    private static final int PERMISSION_WRITE_MEMORY = 5;
    private static final int PERMISSION_FINE_LOCATION = 6;

    public static final String EMAIL = "nautizxatomtex@gmail.com";
    public static final String PASSWORD = "nautiz-x6";

    public static final String MIME_TEXT_FILE = "text/plain";
    public static final String MIME_IMAGE_JPEG ="image/jpeg";
    public static final String MIME_FOLDER = "application/vnd.google-apps.folder";

    private DriveServiceHelper mDriveServiceHelper;
    private String mOpenFileId;

    private EditText mFileTitleEditText;
    private EditText mDocContentEditText;

    private FirebaseAuth mAuth;

    private FileObserver mFileObserver;

    public static final String DIRECTORY = "Android/data/com.atomtex.ascanner";
    //public static final String APP_DIR = Environment.getExternalStorageDirectory() + DIRECTORY;
    java.io.File sMainDir;

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        ////updateUI(currentUser);
    }

    void updateUI(String email) {
        ((TextView)findViewById(R.id.account)).setText(email);
    }

    private void requestPermissions() {
        Log.e(TAG, "♠♠♠♠♠requestPermissions♠♠♠♠♠");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Log.e(TAG, "..........Разрешение ALL_FILES_ACCESS включено, идем дальше");
            } else {
                Log.e(TAG, "..........Разрешение ALL_FILES_ACCESS выключено, значит идем в настройки и включаем");
                Intent intent = new Intent(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, MANAGE_ALL_FILES_ACCESS_PERMISSION);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switch (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                case PackageManager.PERMISSION_DENIED:
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_WRITE_MEMORY);
                    break;
                case PackageManager.PERMISSION_GRANTED:
                    break;
            }

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            requestPermissions();
        }

        sMainDir = new java.io.File(Environment.getExternalStorageDirectory(), DIRECTORY);
        mFileObserver = createFileObserver(sMainDir.getAbsolutePath());
        mFileObserver.startWatching();

        mAuth = FirebaseAuth.getInstance();//todo а это вообще нужно???

        // Store the EditText boxes to be updated when files are opened/created/modified.
        mFileTitleEditText = findViewById(R.id.file_title_edittext);
        mDocContentEditText = findViewById(R.id.doc_content_edittext);

        // Set the onClick listeners for the button bar.
        findViewById(R.id.create_folder).setOnClickListener(view -> createFolder());
        findViewById(R.id.open_btn).setOnClickListener(view -> openFilePicker());
        findViewById(R.id.create_btn).setOnClickListener(view -> createFile());
        findViewById(R.id.save_btn).setOnClickListener(view -> saveFile());
        findViewById(R.id.query_btn).setOnClickListener(view -> query());
        findViewById(R.id.upload_btn).setOnClickListener(view -> openFilePickerToUpload());
        findViewById(R.id.account).setOnClickListener(view -> selectAccount());

        // Authenticate the user. For most apps, this should be done when the user performs an
        // action that requires Drive access rather than in onCreate.
        requestSignIn();
    }

    void selectAccount() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this, signInOptions);

        client.signOut();

        // The result of the sign-in Intent is handled in onActivityResult.
        startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        Log.e(TAG, "onActivityResult: requestCode = " + requestCode);
        Log.e(TAG, "onActivityResult: resultCode = " + resultCode);
        switch (requestCode) {
            case REQUEST_CODE_SIGN_IN:
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    handleSignInResult(resultData);
                }
                break;

            case REQUEST_CODE_OPEN_DOCUMENT:
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    Uri uri = resultData.getData();
                    if (uri != null) {
                        openFileFromFilePicker(uri);
                    }
                }
                break;
            case REQUEST_CODE_OPEN_DOCUMENT_TO_UPLOAD:
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    Uri uri = resultData.getData();
                    if (uri != null) {
                        uploadFile(new java.io.File(Environment.getExternalStorageDirectory(), "nuclib.txt"));
//                        uploadFile(new java.io.File(uri.getPath()));
                    }
                }
                break;
            case MANAGE_ALL_FILES_ACCESS_PERMISSION: //при открытии настройки включения разрешения чтения/записи, это реакция на то, включил пользователь доступ или нет
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Log.e(TAG, "..........разрешение включено, запускаем приложение");
                    } else {
                        Log.e(TAG, "..........разрешение НЕ ВКЛЮЧЕНО, приложение будет выключено");
                    }
                }
        }
        super.onActivityResult(requestCode, resultCode, resultData);
    }

    /**
     * Starts a sign-in activity using {@link #REQUEST_CODE_SIGN_IN}.
     */
    private void requestSignIn() {
        Log.e(TAG, "Requesting sign-in");

        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this, signInOptions);

        Log.e(TAG, "requestSignIn: client = " + client);


        // The result of the sign-in Intent is handled in onActivityResult.
        startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }

    /**
     * Handles the {@code result} of a completed sign-in activity initiated from {@link
     * #requestSignIn()}.
     */
    private void handleSignInResult(Intent result) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener(googleAccount -> {
                    Log.e(TAG, "Signed in as " + googleAccount.getEmail());
                    updateUI(googleAccount.getEmail());

                    // Use the authenticated account to sign in to the Drive service.
                    GoogleAccountCredential credential =
                            GoogleAccountCredential.usingOAuth2(
                                    this, Collections.singleton(DriveScopes.DRIVE_FILE));
                    credential.setSelectedAccount(googleAccount.getAccount());
                    Drive googleDriveService =
                            new Drive.Builder(
                                    AndroidHttp.newCompatibleTransport(),
                                    new GsonFactory(),
                                    credential)
                                    .setApplicationName("Drive API Migration")
                                    .build();

                    // The DriveServiceHelper encapsulates all REST API and SAF functionality.
                    // Its instantiation is required before handling any onClick actions.
                    mDriveServiceHelper = new DriveServiceHelper(googleDriveService);
                })
                .addOnFailureListener(exception -> Log.e(TAG, "Unable to sign in.", exception));
    }

    /**
     * Opens the Storage Access Framework file picker using {@link #REQUEST_CODE_OPEN_DOCUMENT}.
     */
    private void openFilePicker() {
        if (mDriveServiceHelper != null) {
            Log.d(TAG, "Opening file picker.");

            Intent pickerIntent = mDriveServiceHelper.createFilePickerIntent();

            // The result of the SAF Intent is handled in onActivityResult.
            startActivityForResult(pickerIntent, REQUEST_CODE_OPEN_DOCUMENT);
        }
    }

    private void openFilePickerToUpload() {
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Opening file picker.");

            Intent pickerIntent = mDriveServiceHelper.createFilePickerIntent();

            // The result of the SAF Intent is handled in onActivityResult.
            startActivityForResult(pickerIntent, REQUEST_CODE_OPEN_DOCUMENT_TO_UPLOAD);
        }
    }

    /**
     * Opens a file from its {@code uri} returned from the Storage Access Framework file picker
     * initiated by {@link #openFilePicker()}.
     */
    private void openFileFromFilePicker(Uri uri) {
        if (mDriveServiceHelper != null) {
            Log.d(TAG, "Opening " + uri.getPath());

            mDriveServiceHelper.openFileUsingStorageAccessFramework(getContentResolver(), uri)
                    .addOnSuccessListener(nameAndContent -> {
                        String name = nameAndContent.first;
                        String content = nameAndContent.second;

                        mFileTitleEditText.setText(name);
                        mDocContentEditText.setText(content);

                        // Files opened through SAF cannot be modified.
                        setReadOnlyMode();
                    })
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Unable to open file from picker.", exception));
        }
    }

    /**
     * Creates a new file via the Drive REST API.
     */
    private void createFile() {
        Log.e(TAG, "createFile: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Creating a file.");

            mDriveServiceHelper.createFile()
                    .addOnSuccessListener(fileId -> Log.e(TAG, "createFile ID = : " + fileId)/*readFile(fileId)*/)
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Couldn't create file.", exception));
        }
    }

    private void uploadFile(java.io.File localFile) {
        Log.e(TAG, "upload: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Uploading a file.");

            mDriveServiceHelper.uploadFile(localFile, MIME_TEXT_FILE, null)
                    .addOnSuccessListener(fileId -> Log.e(TAG, "createFile ID = : " + fileId)/*readFile(fileId)*/)
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Couldn't create file.", exception));
        }
    }

    //альтернативная версия
    private void uploadFile(java.io.File localFile, String type) {
        Log.e(TAG, "upload: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Uploading a file.");

            mDriveServiceHelper.uploadFile(localFile, type)
                    .addOnSuccessListener(fileId -> Log.e(TAG, "createFile ID = : " + fileId)/*readFile(fileId)*/)
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Couldn't create file.", exception));
        }
    }

    private void uploadFolder(java.io.File localFile) {
        Log.e(TAG, "upload: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Uploading a folder.");

            mDriveServiceHelper.uploadFile(localFile, MIME_FOLDER, null)
                    .addOnSuccessListener(fileId -> Log.e(TAG, "createFile ID = : " + fileId)/*readFile(fileId)*/)
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Couldn't create folder.", exception));
        }
    }

    /**
     * Retrieves the title and content of a file identified by {@code fileId} and populates the UI.
     */
    private void readFile(String fileId) {
        if (mDriveServiceHelper != null) {
            Log.d(TAG, "Reading file " + fileId);

            mDriveServiceHelper.readFile(fileId)
                    .addOnSuccessListener(nameAndContent -> {
                        String name = nameAndContent.first;
                        String content = nameAndContent.second;

                        mFileTitleEditText.setText(name);
                        mDocContentEditText.setText(content);

                        setReadWriteMode(fileId);
                    })
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Couldn't read file.", exception));
        }
    }

    /**
     * Saves the currently opened file created via {@link #createFile()} if one exists.
     */
    private void saveFile() {
        if (mDriveServiceHelper != null && mOpenFileId != null) {
            Log.d(TAG, "Saving " + mOpenFileId);

            String fileName = mFileTitleEditText.getText().toString();
            String fileContent = mDocContentEditText.getText().toString();

            mDriveServiceHelper.saveFile(mOpenFileId, fileName, fileContent)
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Unable to save file via REST.", exception));
        }
    }

    /**
     * Queries the Drive REST API for files visible to this app and lists them in the content view.
     */
    private void query() {
        if (mDriveServiceHelper != null) {
            Log.d(TAG, "Querying for files.");

            mDriveServiceHelper.queryFiles()
                    .addOnSuccessListener(fileList -> {
                        StringBuilder builder = new StringBuilder();
                        for (File file : fileList.getFiles()) {
//                            if (file.isEmpty()) builder.append("empty").append("\n");
                            //if (file.getExplicitlyTrashed()) builder.append("e_thrashed").append("\n");
//                            if (file.getTrashed()) builder.append("thrashed").append("\n");
//                            builder.append(file.getDescription()).append("\n");
//                            builder.append(file.getKind()).append("\n");
//                            builder.append(file.getFileExtension()).append("\n");
                            builder.append(file.getMimeType()).append("\n");
//                            builder.append(file.getSize()).append("\n");
//                            builder.append(file.getId()).append("\n");
                            builder.append(file.getName()).append("\n");
                        }
                        String fileNames = builder.toString();

                        mFileTitleEditText.setText("File List");
                        mDocContentEditText.setText(fileNames);

                        setReadOnlyMode();
                    })
                    .addOnFailureListener(exception -> Log.e(TAG, "Unable to query files.", exception));
        }
    }

    private void createFolder() {
        Log.e(TAG, "createFolder: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Creating a folder.");

            mDriveServiceHelper.createFolder("NewFolder")
                    .addOnSuccessListener(fileId -> Log.e(TAG, "createFile ID = : " + fileId)/*readFile(fileId)*/)
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Couldn't create folder.", exception));
        }

    }

    /**
     * Updates the UI to read-only mode.
     */
    private void setReadOnlyMode() {
        mFileTitleEditText.setEnabled(false);
        mDocContentEditText.setEnabled(false);
        mOpenFileId = null;
    }

    /**
     * Updates the UI to read/write mode on the document identified by {@code fileId}.
     */
    private void setReadWriteMode(String fileId) {
        mFileTitleEditText.setEnabled(true);
        mDocContentEditText.setEnabled(true);
        mOpenFileId = fileId;
    }

    /**
     * Sets up a FileObserver to watch the current directory.
     */
    private FileObserver createFileObserver(final String dirPath) {
        Log.e(TAG, "♦♦♦createFileObserver: START");
        return new FileObserver(dirPath, FileObserver.CREATE | FileObserver.DELETE
                | FileObserver.MOVED_FROM | FileObserver.MOVED_TO) {

            @Override
            public void onEvent(final int event, final String path) {
                Log.e(TAG, "♦♦♦onEvent: " + event);

                java.io.File file = new java.io.File(dirPath, path);
                Log.e(TAG, "onEvent: Path = " + dirPath);
                if (file.isDirectory()){
                    Log.e(TAG, "" + file.getAbsolutePath() + " IS DIRECTORY");
//                    uploadFolder(new java.io.File(dirPath, path));
                    uploadFile(file, MIME_FOLDER);
                }
                else{
                    Log.e(TAG, "" + file.getAbsolutePath() + " IS FILE");
                    uploadFile(file, MIME_TEXT_FILE);
                }



            }
        };
    }

}