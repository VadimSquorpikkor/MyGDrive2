package com.squorpikkor.mygdrive2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "_";

    private static final int REQUEST_CODE_SIGN_IN = 1;
    private static final int REQUEST_CODE_OPEN_DOCUMENT = 2;
    private static final int REQUEST_CODE_OPEN_DOCUMENT_TO_UPLOAD = 3;

    private static final int MANAGE_ALL_FILES_ACCESS_PERMISSION = 4;
    private static final int PERMISSION_WRITE_MEMORY = 5;
    private static final int PERMISSION_FINE_LOCATION = 6;

    public static final String EMAIL = "nautizxatomtex@gmail.com";
    public static final String PASSWORD = "nautiz-x6";

    public static final String MIME_TEXT_FILE = "text/plain";
    public static final String MIME_IMAGE_JPEG = "image/jpeg";
    public static final String MIME_FOLDER = "application/vnd.google-apps.folder";
    private static final String ERROR_SET_PREF = "error_set_pref";

    private DriveServiceHelper mDriveServiceHelper;
    private String mOpenFileId;

    private EditText mFileTitleEditText;
    private EditText mDocContentEditText;

    private FirebaseAuth mAuth;

    private RecursiveFileObserverNew mFileObserver;

    public static final String DIRECTORY = "Android/data/com.atomtex.ascanner";
    //public static final String APP_DIR = Environment.getExternalStorageDirectory() + DIRECTORY;
    java.io.File sMainDir;

    Switch cashSwitch;
    Switch deleteSwitch;
    Switch WiFiOnlySwitch;

    static public boolean uploadIsAllowed;


    // Whether there is a Wi-Fi connection.
    private static boolean wifiConnected = false;
    // Whether there is a mobile connection.
    private static boolean mobileConnected = false;
    // Whether the display should be refreshed.
    public static boolean refreshDisplay = true;//todo зачем???
    // The user's current network preference setting.
    public static String sPref = null;
    // The BroadcastReceiver that tracks network connectivity changes.
    private NetworkReceiver receiver = new NetworkReceiver();
    public static final String WIFI = "Wi-Fi";
    public static final String ANY = "Any";


    void updateUI(String email) {
        ((TextView) findViewById(R.id.account)).setText(email);
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
        findViewById(R.id.create_folder).setOnClickListener(view -> createFolder("NewFolder", null));
//        findViewById(R.id.open_btn).setOnClickListener(view -> openFilePicker());
        findViewById(R.id.open_btn).setOnClickListener(view -> openFilePickerToUpload());
        findViewById(R.id.create_btn).setOnClickListener(view -> createFile());
        findViewById(R.id.save_btn).setOnClickListener(view -> saveFile());
        findViewById(R.id.query_btn).setOnClickListener(view -> query());
        findViewById(R.id.upload_btn).setOnClickListener(view -> uploadFolder(sMainDir.toString()));
        findViewById(R.id.account).setOnClickListener(view -> selectAccount());
        findViewById(R.id.load_folder_by_path).setOnClickListener(view -> startUpload(new java.io.File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Folder/File.txt")));
        findViewById(R.id.create_folder_2).setOnClickListener(view -> getFiles());
        findViewById(R.id.upload_from_error_list).setOnClickListener(view -> uploadFromErrorList());
        findViewById(R.id.start_queue).setOnClickListener(view -> startQueue());
        findViewById(R.id.reset_queue).setOnClickListener(view -> uploadQueue = new ArrayList<>());//сброс (обнуление) очереди

        cashSwitch = findViewById(R.id.do_cash);
        deleteSwitch = findViewById(R.id.delete_after_upload);
        WiFiOnlySwitch = findViewById(R.id.wifi_only);

        WiFiOnlySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            canIUpload();
        });

        requestSignIn();
        restoreErrorPathSetFromFile();

        checkConnection();//todo эксперимент
        Log.e(TAG, "is online - "+isOnline());//todo эксперимент

        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new NetworkReceiver();
        this.registerReceiver(receiver, filter);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregisters BroadcastReceiver when app is destroyed.
        if (receiver != null) {
            this.unregisterReceiver(receiver);
        }
    }

    //todo это оставил только чтобы сохранить условие в IF, остальное не нужно. Нужно будет при
    // проверке: разрешена ли загрузка только по WiFi или по любой
    public void loadPage() {
        if (((sPref.equals(ANY)) && (wifiConnected || mobileConnected))
                || ((sPref.equals(WIFI)) && (wifiConnected))) {
            // AsyncTask subclass
            /////new DownloadXmlTask().execute(URL);
        } else {
            /////showErrorPage();
        }
    }

    //todo эксперимент
    void checkConnection() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isWifiConn = false;
        boolean isMobileConn = false;
        for (Network network : connMgr.getAllNetworks()) {
            NetworkInfo networkInfo = connMgr.getNetworkInfo(network);
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                isWifiConn |= networkInfo.isConnected();
            }
            if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                isMobileConn |= networkInfo.isConnected();
            }
        }

        Log.e(TAG, "Wifi connected: " + isWifiConn);
        Log.e(TAG, "Mobile connected: " + isMobileConn);
    }

    //todo эксперимент
    public boolean isOnline() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private void getFiles() {
        String name = "com.atomtex.ascanner";
        mDriveServiceHelper.checkIfExist(name, null).addOnSuccessListener(fileList -> {
            Log.e(TAG, "createFolderNew: fileList - " + fileList);
            Log.e(TAG, "createFolderNew: fileList size - " + fileList.getFiles().size());
            String p;
            for (int i = 0; i < fileList.getFiles().size(); i++) {

                if (fileList.getFiles().get(i).getParents() == null) p = "no_parents";
                else p = fileList.getFiles().get(i).getParents().get(0);
                Log.e(TAG, "......file: " + fileList.getFiles().get(i).getName() + " - " + i + " " + p);
            }
        })
                .addOnFailureListener(exception ->
                        Log.e(TAG, "косяк!!!", exception));
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
                    Log.e(TAG, "uri - "+uri);
                    if (uri != null) {
                        openFileFromFilePicker(uri);
                    }
                }
                break;
            case REQUEST_CODE_OPEN_DOCUMENT_TO_UPLOAD:
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    Uri uri = resultData.getData();
                    if (uri != null) {
//                        checkAndUploadFile(new java.io.File(Environment.getExternalStorageDirectory(), "nuclib.txt"), MIME_TEXT_FILE, null);
                        Log.e(TAG, "uri.getPath() - " + uri.getPath());
                        checkAndUploadFile(new java.io.File(uri.getPath()), MIME_TEXT_FILE, null);
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

    //Перед аплодом проверяет файл на существовании на облаке. При аплоде по событию обсервера используется UploadFile без проверки, так как проверка уже есть в методе uploadFolderByFileList
    private void checkAndUploadFile(java.io.File localFile, String type, String file_Id) {
        Log.e(TAG, "upload: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Uploading a file.");

            mDriveServiceHelper.checkIfExist(localFile.getName(), file_Id).addOnSuccessListener(fileList -> {

                if (fileList.getFiles() != null && fileList.getFiles().size() == 0) {
                    Log.e(TAG, ".....FILE NOT EXISTS!!!");

                    mDriveServiceHelper.uploadFile(localFile, type, file_Id)
                            .addOnSuccessListener(fileId -> {
                                Log.e(TAG, "createFile ID = : " + fileId);
                                Log.e(TAG, "uploadFile: файл "+localFile.getAbsolutePath()+" успешно загружен, удалить адрес из pathSet");
                                Log.e(TAG, "uploadFile: errorPathSet.size() BEFORE = "+errorPathSet.size());
                                //////errorPathSet.remove(localFile.getAbsolutePath());
                                Log.e(TAG, "uploadFile: errorPathSet.size() AFTER = "+errorPathSet.size());
                                if (deleteSwitch.isChecked()) deleteFile(localFile);
                                uploadNextFile();
                            })
                            .addOnFailureListener(exception -> {
                                Log.e(TAG, "Couldn't create file.", exception);
                                iCantUpload(localFile.getAbsolutePath()); ///errorPathSet.add(localFile.getAbsolutePath());
                            });

                } else {
                    Log.e(TAG, ".....FILE ALREADY EXISTS!!!");
                    ////////errorPathSet.remove(localFile.getAbsolutePath());
                    if (deleteSwitch.isChecked()) deleteFile(localFile);
                    uploadNextFile();
                }

            })
                    .addOnFailureListener(exception ->
                            iCantUpload(localFile.getAbsolutePath())); ///errorPathSet.add(localFile.getAbsolutePath()));
        }
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

                    canIUpload();////uploadFromErrorList();//АПЛОДИТЬ МОЖНО ТОЛЬКО ПОСЛЕ ТОГО, КАК ПРОИНИЦИАЛИЗИРОВАН mDriveServiceHelper


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

    private void uploadFile(java.io.File localFile, String type, String file_Id) {
        if (mDriveServiceHelper != null) {
            mDriveServiceHelper.uploadFile(localFile, type, file_Id)
                    .addOnSuccessListener(fileId -> {
                        Log.e(TAG, "createFile ID = : " + fileId);
                        Log.e(TAG, "uploadFile: файл "+localFile.getAbsolutePath()+" успешно загружен, удалить адрес из pathSet");
                        Log.e(TAG, "uploadFile: errorPathSet.size() BEFORE = "+errorPathSet.size());
                        ////errorPathSet.remove(localFile.getAbsolutePath());
                        Log.e(TAG, "uploadFile: errorPathSet.size() AFTER = "+errorPathSet.size());
                        if (deleteSwitch.isChecked()) deleteFile(localFile);
                        uploadNextFile();
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create file. "+localFile.getAbsolutePath(), exception);
                        iCantUpload(localFile.getAbsolutePath()); ///errorPathSet.add(localFile.getAbsolutePath());
                    });
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
    //Функция выводит в EditText список файлов на gDrive. Функция для проверки, работает не совсем корректно, но она и не будет нужна
    //Косяк в том, что выводится не весь список (только сколько влезит в EditText)
    //Для просмотра всего списка смотреть Лог
    private void query() {
        if (mDriveServiceHelper != null) {
            Log.d(TAG, "Querying for files.");

            //Перечень доступных свойств настраивается в mDriveService.files().list().setFields("files( ЗДЕСЬ )").execute()); (см. queryFiles())

            mDriveServiceHelper.queryFiles()
                    .addOnSuccessListener(fileList -> {
                        StringBuilder builder = new StringBuilder();
                        for (File file : fileList.getFiles()) {
                            if (/*file.getTrashed()!=null && */!file.getTrashed()) {
//                                builder.append("trashed\n");}
//                                else {
                                builder.append(file.getName()).append(" - ");
                                builder.append(file.getMimeType()).append(" - ");
                                /*if (file.getParents() == null) {
                                    builder.append("no_parents\n");
                                } else {
                                    builder.append(file.getParents().get(0)).append(" - ");
                                    builder.append(file.getParents().size()).append("\n");
                                }*/
//                              builder.append(file.getSize()).append("\n");
                                String parent;
                                if (file.getParents() == null) parent = "no_parents";
                                else parent = file.getParents().get(0);
                                builder.append(file.getId()).append("\n");
                                Log.e(TAG, "query: " + file.getName() + " " + file.getMimeType() + " " + file.getId() + " parent: " + parent);
                            }
                        }
                        String fileNames = builder.toString();

                        mFileTitleEditText.setText("File List");
                        mDocContentEditText.setText(fileNames);

                        setReadOnlyMode();
                    })
                    .addOnFailureListener(exception -> Log.e(TAG, "Unable to query files.", exception));
        }
    }

    /**По пути папки получает список всех файлов в папке и каждый файл добавляет в очередь
     * (автоматом стартует закачка). Если в папке попадается подпапка, то рекурсивно вызывается
     * uploadFolder и перебирает все файлы уже в подпапке и т.д. По сути: метод на вход получает
     * путь к локальной папке и загружает ВСЕ файлы, включая поддиректории. При этом на GDrive
     * полностью сохраняется структура директорий, как она была в локальной папке*/
    private void uploadFolder(String path) {
        java.io.File folder = new java.io.File(path);
        java.io.File[] files = folder.listFiles();
        for (java.io.File file:files) {
            if (file.isFile()) {
                startUpload(file);
            } else {
                uploadFolder(file.getAbsolutePath());
            }
        }
    }

    /**Создание папки на облаке по имени и id родителя*/
    private void createFolder(String name, String folderId) {
        Log.e(TAG, "createFolder: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Creating a folder.");

            mDriveServiceHelper.createFolder(name, folderId)
                    .addOnSuccessListener(fileId -> {
                        Log.e(TAG, "createFolder ID = : " + fileId);
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create folder.", exception);
                    });
        }
    }

    /** После создания папки метод переходит (через doStuffNew) к созданию подпапки.
     *  Наличие такой папки, проверка на "файл или папка", проверка, есть ли уже такая папка/файл
     *  на облаке происходит в uploadFolderByFileList())*/
    private void createFolderAndContinue(java.io.File local_folder, java.io.File file, String cuttingPath, String parent_id) {
        if (mDriveServiceHelper != null) {
            mDriveServiceHelper.createFolder(file.getName(), parent_id)
                    .addOnSuccessListener(fileId -> {
                        Log.e(TAG, "createFolder name - "+ file.getName() + ", parentID - " + parent_id + ", created_folder_id - " + fileId);

                        if (cashSwitch.isChecked())updateCash(file.getAbsolutePath(), fileId);

                        doStuffNew(local_folder, cuttingPath, fileId);
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create folder.", exception);
                        iCantUpload(local_folder.getAbsolutePath());
                    });
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

    HashSet<String> errorPathSet = new HashSet<>();


    /**Обертка для uploadFolderByFileList.
     * Чтобы было проще с ним работать сделан этот класс только с одним параметром на входе.
     * В итоге получается метод, который на вход получает локальную папку, которая загружается
     * на GDrive в СООТВЕТСТВУЮЩУЮ папку, как она находится относительно главной папки (с которой
     * идет синхронизация). Т.е.: папка storage/emulated/0/com.atomtex.ascanner/folder_1/folder_2/folder_3/
     * будет загружена в MyDrive>com.atomtex.ascanner>folder_1>folder_2. Если папка уже есть, то
     * она загружаться не будет, но находящиеся в ней фалы будут загружены (если их не было).
     * Если кроме создаваемой папки нет на облаке и её родителя, он будет создан. Проблема, которую
     * решает этод метод, в том, что на gDrive нет пути, есть только Id родителя,
     * т.е. нельзя просто указать путь, по которому сохранять папку
     *
     * Условие IF добавлено, чтобы загружать файлы НЕ из папки com.atomtex.ascanner
     *
     * pathWithoutRootFolder - * .....путь оставшийся после удаления из пути файла Environment.getExternalFilePath
     * (для файлов не из папки RSA) или mainDir (для RSA, mainDir = storage/emulated...ascanner/),
     * т.е. получаем путь, по которому файл будет храниться на облаке (не будет же он храниться на GDrive>storage>emulated>0 и т.д.):
     * storage/emulated/0/com.atomtex.scanner/folder/file --> folder/file */
    void uploadFileByFilePath(java.io.File local_file) {
        String pathWithoutRootFolder;
        if (local_file.getAbsolutePath().contains(sMainDir.getAbsolutePath())) pathWithoutRootFolder = local_file.getAbsolutePath().replace(sMainDir.getAbsolutePath()+"/", "");
        else pathWithoutRootFolder = local_file.getAbsolutePath().replace(Environment.getExternalStorageDirectory()+"/", "");
//        getIdFromCash(local_file, pathWithoutRootFolder);
        getIdFromCashNew(local_file);
    }

    HashMap<String, String> cashMap = new HashMap<>();


    void getIdFromCash(java.io.File file, String cuttingPath) {
        String id = null;
        if (cashSwitch.isChecked() && cashMap.containsKey(file.getParent())) {
            id = cashMap.get(file.getParent());
            Log.e(TAG, "....................................");
            Log.e(TAG, "..   Есть ID по такому пути! -> "+id);
            Log.e(TAG, "....................................");
            cuttingPath = "";
        }
        uploadFolderByFileList(file, cuttingPath, id);
    }

    /**Кэширует пути всех папок и файлов. По итогу: с кэшем 1:17, без кэша 1:27.
     * И оно того стоило???!!! И это ещё я не добавил проверку isIdExistsOnCloud,
     * а это ещё один запрос на облако для каждого файла
     *
     * Проверка пути в кэше идет от родительской папки до папки приложения:
     * sMainDir/folde1/folder2/folder3/file.txt первым проверяется путь
     * storage/emulated/0/com.atomtex.ascanner/folder1/folder2/folder3, если такой путь найден в кэше,
     * получаем id этой папки, при этом cutting_path будет равен ""; если не найден, то к
     * cutting_path слева добавляем имя папки (формируем маску, теперь cutting_path = "/file.txt"),
     * и далее следующий цикл, то же самое, но уже для родительской папки
     * (storage/emulated/0/com.atomtex.ascanner/folder1/folder2)*/
    void getIdFromCashNew(java.io.File file) {
        String cutting_path;
        String id = null;
        if (!cashSwitch.isChecked()){ cutting_path = file.getAbsolutePath().replace(sMainDir.getAbsolutePath(), "");}
        else {
            cutting_path = "";
            java.io.File tempFile;
            tempFile = file;
            //todo если переделать под do...while, то код можно сильно сократить, if вообще можно тогда убрать
            while (!tempFile.getAbsolutePath().equals(sMainDir.getAbsolutePath()) && id == null) { //путь перебирается не до корневой папки, а до папки com.atomtex.ascanner)
                if (cashMap.containsKey(tempFile.getParent())) {
                    Log.e(TAG, "....................................");
                    Log.e(TAG, "..   Есть ID по такому пути! -> " + id);
                    Log.e(TAG, "....................................");
                    id = cashMap.get(tempFile.getParent());
                } else {
                    cutting_path = "/" + tempFile.getName() + cutting_path;
                    tempFile = tempFile.getParentFile();
                }
            }
        }

        uploadFolderByFileList(file, cutting_path, id);
    }

    boolean isIdExistsOnCloud(String id) {
        //todo дописать реализацию
        return true;
    }

    void updateCash(String pathToCash, String file_Id) {
        //todo !!!для кэша: может быть такое: путь вместе с его id закеширован, но самого файла (папки) уже нет (пользователь удалил)
        // вставка по id родителя в этом случае не сработает. Пока не будет решено, кэширование не вкючать
        // Можно сделать, добавив проверку на наличие родителя по его id. Получится лишняя одна проверка ifExist, но с другой стороны
        // если это не первого уровня родитель, то всё равно будет только одна проверка, а не проверка каждой папки
        // поэтому смысл в кэше будет, даже с лишней проверкой
        // !!!

        //todo кэш для файла не нужен!!!

        //todo в кэш надо будет записывать и id самого файла, тогда можно проверять наличие файла на диске по сохраненному кэшу

        if (/*!pathToCash.equals("")&&*/!cashMap.containsKey(pathToCash))cashMap.put(pathToCash, file_Id);
        Log.e(TAG, "                       '");
        Log.e(TAG, "------ КЭШ ------");
        Log.e(TAG, "|   fileId = "+file_Id+", pathToCash = "+ pathToCash);
        int i = 1;
        for (HashMap.Entry<String, String> entry : cashMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            Log.e(TAG, ""+i+") path - "+key+" id - "+value);
            i++;
        }
        Log.e(TAG, "------ КЭШ ------");
        Log.e(TAG, "                       '");
    }

    /**Очередь за загружаемых файлов*/
    static ArrayList<java.io.File> uploadQueue = new ArrayList<>();

    /**метод рекурсивно перебирает названия папок на gDrive от синхронизируемой папки до самого
     * последнего чайлда, каждый раз запоминая ID текущей папки. И если чайлд оказался последним,
     * то в папке с ID родителя создает файл (если его там ещё не было). Итого: метод загружает
     * файл/папку в папку по ID этой целевой папки, зная только путь локальной папки на телефоне
     *
     * На вход в самый первый раз (до рекурсии) метод получает в переменную cuttingPath всегда
     * полный путь МИНУС все что идет до синхронизируемой папки: если на телефоне файл лежит по
     * адресу: /storage/emulated/0/Android/data/com.atomtex.com/folder/file.txt, то cuttingPath
     * будет выглядеть: com.atomtex.com/folder/file.txt. При каждом рекурсивном вызове
     * uploadFolderByFileLis проверяется путь, если путь уже не содержит поддиректории (cuttingPath.equals("")),
     * то метод аплодит файл/папку по полученному ранее ID, иначе cuttingPath будет уменьшаться на
     * одну директорию СЛЕВА (в методе doStuffNew) и вместе с полученным ID будет рекурсивно вызван
     * метод uploadFolderByFileList уже с новыми значениями ID и короткого пути
     *
     * cuttingPath - последняя часть пути локального файла, часть, которая будет отниматься от пути файла
     * При загрузке файла /storage/emulated/0/Android/data/com.atomtex.ascanner/count/count_20201229_103834.txt
     * первый cuttingPath = count/count_20201229_103834.txt, значит сразу будет загружаться папка
     * /storage/emulated/0/Android/data/com.atomtex.ascanner/ . Затем из cuttingPath отнимается часть до "/" включительно
     * получается путь для след папки: /storage/emulated/0/Android/data/com.atomtex.ascanner/count/ и т.д.
     * Когда от cuttingPath останется "" (и сам файл — не папка), это будет сигналом, что все подпапки загружены
     * и докачивается уже сам файл в родительскую папку по её id*/
    void uploadFolderByFileList(java.io.File local_folder, String cuttingPath, String parent_Id) {
        Log.e(TAG, "------- cuttingPath = " + cuttingPath);
        Log.e(TAG, "------- filePath = " + local_folder.getAbsolutePath());

        String path_for_file = local_folder.getAbsolutePath().replace(cuttingPath, "");
        Log.e(TAG, "path_for_file: "+path_for_file);
        java.io.File file_cut = new java.io.File(path_for_file);
        Log.e(TAG, "file_cut path: " + file_cut.getAbsolutePath());

        mDriveServiceHelper.checkIfExist(file_cut.getName(), parent_Id)
                .addOnSuccessListener(fileList -> {
                    Log.e(TAG, "------------------------------------------------------");
                    Log.e(TAG, "uploadFolderByFileList.checkIfExist: ON SUCCESS: name - " + file_cut.getName() + ", parent_Id - " + parent_Id);

                    if (fileList.getFiles() != null && fileList.getFiles().size() == 0) {
                        //--------------FILE NOT EXISTS ON CLOUD
                        Log.e(TAG, ".....FILE NOT EXISTS ON CLOUD!!! --- "+file_cut.getName() + ", parent_Id - " + parent_Id);
                        if (cuttingPath.equals("") && local_folder.isFile()) {//значит это самая последняя субдиректория и это файл (и его нет на GDrive)
                            uploadFile(local_folder, MIME_TEXT_FILE, parent_Id);
                        } else{
                            createFolderAndContinue(local_folder, file_cut, cuttingPath, parent_Id);
                        }
                    } else {
                        //--------------FILE EXISTS ON CLOUD
                        Log.e(TAG, ".....FILE EXISTS ON CLOUD!!! --- "+file_cut.getName() + ", parent_Id - " + parent_Id);
                        String existingFile_id = fileList.getFiles().get(0).getId();
                        doStuffNew(local_folder, cuttingPath, existingFile_id);
                    }
                }).addOnFailureListener(exception -> {
            iCantUpload(local_folder.getAbsolutePath()); ///errorPathSet.add(local_folder.getAbsolutePath());
        });
    }

    /**Если uploadFolderByFileList уже загрузил последнюю папку/файл содержащуюся в пути файла (т.е.
     * в пути "папка1/папка2/файл1" уже загружены "папки1" "папки2" "файл1"
     * */
    //убрал из doStuffNew загрузки файлов и папок — иначе пришлось бы проверять при создании на существование на облаке (ifExits).
    //теперь всё проверяется в uploadFolderByFileList
    void doStuffNew(java.io.File local_folder, String cuttingPath, String new_id) {
        cuttingPath = cuttingPath.replaceFirst("/", "");///для использования только с getIdFromCashNew
        Log.e(TAG, " ☺☺☺☺  doStuffNew: cuttingPath - "+cuttingPath);
        String[] pathArr = cuttingPath.split("/");
        for (int i = 0; i < pathArr.length; i++) {
            Log.e(TAG, "" +i+ ": " + pathArr[i]);
        }
        if (cuttingPath.equals("")) {
            Log.e(TAG, ".....Конец цикла");
            if (deleteSwitch.isChecked()) deleteFile(local_folder);
            uploadNextFile();
        } else {
            Log.e(TAG, "OLD cuttingPath - " + cuttingPath);
            //Если pathArr.length >1, то cuttingPath: папка/файл -> файл, иначе: ""
            String newCuttingPath = "";
            if (pathArr.length != 1) newCuttingPath = cuttingPath.replace(pathArr[0] + "/", "");
            Log.e(TAG, "NEW cuttingPath - " + newCuttingPath);
            Log.e(TAG, ".....След цикл");
            uploadFolderByFileList(local_folder, newCuttingPath, new_id);
        }
    }

    /** Метод для удаления файлов после успешного аплода на облако.
     * Удаляет локальный файл. После удаления проверяет, если родительская папка пустая (т.е. это был последний файл в папке),
     * то удаляется родительская папка. Если родительская папка родительской папки пуста... Короче — и т.д.*/
    //todo НЕЛЬЗЯ удалять файлы библиотеки, не будет работать программа, надо подумать (может просто сделать проверку в методе на имя)
    void deleteFile(java.io.File localFile) {
        java.io.File parent = localFile.getParentFile();
        Log.e(TAG, "try to delete file - "+localFile);
        if (localFile.delete()) Log.e(TAG, "Удалено успешно");
        else Log.e(TAG, "Удалить не получилось");
        if (parent!=null && parent.listFiles()!=null && parent.listFiles().length==0)deleteFile(parent);
    }

    private String returnEvent(int event) {
        switch (event) {
            case FileObserver.ACCESS:
                return "ACCESS";
            case FileObserver.MODIFY:
                return "MODIFY";
            case FileObserver.ATTRIB:
                return "ATTRIB";
            case FileObserver.CLOSE_WRITE:
                return "CLOSE_WRITE";
            case FileObserver.CLOSE_NOWRITE:
                return "CLOSE_NOWRITE";
            case FileObserver.OPEN:
                return "OPEN";
            case FileObserver.MOVED_FROM:
                return "MOVED_FROM";
            case FileObserver.MOVED_TO:
                return "MOVED_TO";
            case FileObserver.CREATE:
                return "CREATE";
            case FileObserver.DELETE:
                return "DELETE";
            case FileObserver.DELETE_SELF:
                return "DELETE_SELF";
            case FileObserver.MOVE_SELF:
                return "MOVE_SELF";
            case FileObserver.ALL_EVENTS:
                return "ALL_EVENTS";
            case 32768:
                return "ERROR";
            default:
                return "unknown " + event;
        }
    }

    void uploadNextFile() {
        if (uploadQueue.size()>0){
            uploadQueue.remove(0);
        }
        Log.e(TAG, "....... files in Queue left "+ uploadQueue.size()+" .......");
        if (uploadQueue.size()>0){
            Log.e(TAG, "....... Загружаем следующий .......");
            uploadFileByFilePath(uploadQueue.get(0));
        }
        if (uploadQueue.size() == 0) {
            saveErrorPathSetToFile();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveErrorPathSetToFile();
    }

    /**Если файл по каким то причинам не загрузился на облако, то его локальный путь сохраняется в
     * список незагруженных файлов. При закрытии/сворачивании приложения этот список сохраняется в
     * Preferences. При старте приложения список восстанавливается из Preferences, после входа в
     * аккаунт автоматом стартует аплод файлов из этого списка
     *
     * Сохранение только НЕПУСТОГО списка выглядит логично, но тогда, если в предыдущей сесии
     * были незагруженные файлы, которые были успешно аплодены при старте текущей сесии,
     * при перезагрузке будут загружены опять, так как список в Preferences так и остался
     * не обнуленным. Поэтому если список ошибок пустой, то он все равно будет сохраняться
     * в Preferences, тем самым перезаписывая и обнуляя прошлый список*/
    private void saveErrorPathSetToFile() {
        Log.e(TAG, "....... Сохранение path незагруженных файлов в preference. Size = "+errorPathSet.size());
        saveStringSet(errorPathSet, ERROR_SET_PREF);
    }

    private void restoreErrorPathSetFromFile() {
        Log.e(TAG, "....... Восстановление path незагруженных файлов из preference");
        errorPathSet = loadStringSet(ERROR_SET_PREF);

    }

    void startQueue() {
        if (uploadQueue.size()>0){
            Log.e(TAG, "....... Стартуем очередь .......");
            uploadFileByFilePath(uploadQueue.get(0));
        }
    }

    private RecursiveFileObserverNew createFileObserver(final String dirPath) {
        Log.e(TAG, "♦♦♦createFileObserver: START");
        return new RecursiveFileObserverNew(dirPath, (event, file) -> {
            Log.e(TAG, "♦♦♦onEvent: " + returnEvent(event));
            startUpload(file);
        });
    }

    //todo Вообще не нужно два массива для путей файлов (очередь и список_незагруженных) можно
    // использовать только один (очередь), просто при неудачной попытке загрузки путь не удаляется
    // из очереди, а uploadNextFile() пытается загрузить след., пока не дойдет до конца массива
    // так всегда будет список ещё_не_загруженных_файлов. Если приложение будет закрыто в момент
    // загрузки файлов, очередь будет сохранена в Pref, в случае использования списка_незагруженных
    // этот список просто не будет ещё создан (ведь не было ошибок во время загрузки) и при
    // перезапуске приложения эти файлы не будут загружены на облако
    // Тогда uploadQueue должна будет хранить String

    void startUpload(java.io.File file) {
        Log.e(TAG, "***************************startUpLoad: "+ file.getAbsolutePath() + " ***************************");
        uploadQueue.add(file);
        Log.e(TAG, "queue count = " + uploadQueue.size());

        if (uploadQueue.size() == 1) {
            uploadFileByFilePath(uploadQueue.get(0));
        }
    }

    /**Вызывается при ошибке загрузки*/
    public void iCantUpload(String file_path) {
        Log.e(TAG, "....... uploadError .......");
        uploadNextFile();
        Log.e(TAG, "....... Error List: ...................................");
        errorPathSet.add(file_path);
        int i = 0;
        for (String path:errorPathSet) {
            Log.e(TAG, ""+i+": "+path);
            i++;
        }
        Log.e(TAG, ".......................................................");
    }

    private void uploadFromErrorList() {
        for (String path:errorPathSet) {
            startUpload(new java.io.File(path));
        }
        errorPathSet = new HashSet<>();//обнулить errorPathSet
    }

    /**Сохранение HashSet в SharedPreferences*/
    private void saveStringSet(HashSet<String> set, String prefName) {
        SharedPreferences mPreferences;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.clear();//For save less variables than before, if do not clear, it will load old variables, from old session
        int i = 0;
        for (String s:set) {
            editor.putString(prefName + i, s);
            i++;
        }
        editor.apply();
    }

    /**Восстановление HashSet из SharedPreferences*/
    private HashSet<String> loadStringSet(String prefName) {
        SharedPreferences mPreferences;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        HashSet<String> set = new HashSet<>();
        int count = 0;
        while (mPreferences.contains(prefName + count)) {
            String s = mPreferences.getString(prefName + count, "");
            set.add(s);
            count++;
        }
        return set;
    }

    /** Управляет разрешением на аплод (uploadIsAllowed) в зависимости от статуса флагов GSM и WiFi
     *  и переключателя "Только WiFi". Если есть подключение и разрешена загрузка
     *  через любое подключение (и GSM, и WiFi) — загрузка разрешена;
     *  если разрешена загрузка только через WiFi и есть подключение по WiFi — загрузка разрешена.
     *  Иначе — загрузка запрещена.
     *  При срабатывании метода сразу начинается аплод незагруженных ранее файлов
     *  (если загрузка разрешена)*/
    void canIUpload() {
        uploadIsAllowed = ((!WiFiOnlySwitch.isChecked()) && (wifiConnected || mobileConnected))
                || (((WiFiOnlySwitch.isChecked()) && (wifiConnected)));
        if (uploadIsAllowed && mDriveServiceHelper!=null)uploadFromErrorList();
    }


    /** На основе полученного бродкаста управляет флагами GSM и WiFi: вкл/выкл и запускает canIUpload().
     *  Т.е. отслеживает подключение к GSM и WiFi и при изменении статуса разрешает или запрещает
     *  загрузку файлов; если загрузка разрешена, начинает загрузку незагруженных ранее файлов*/
    public class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager conn =  (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = conn.getActiveNetworkInfo();

            if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                wifiConnected = true;
                Log.e(TAG, "wifi_connected");
            } else if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                Log.e(TAG, "gsm connected");
                mobileConnected = true;
            } else {
                wifiConnected = false;
                mobileConnected = false;
                Log.e(TAG, "lost_connection");
            }
            canIUpload();
        }
    }


}
