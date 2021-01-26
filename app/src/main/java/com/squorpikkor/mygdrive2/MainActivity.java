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
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION;

public class MainActivity extends AppCompatActivity {

    // пока "root", в последствии id будет равен id папки "Radiation Scanner Assistant", т.е.
    // все данные будут храниться не в корне gDrive, а в своей папке, так будет порядок, ведь у
    // пользователя на облаке может храниться не только RSA файлы
    //
    // Важно: id нужно будет не хранить в программе, а при подключении к облаку получать список
    // файлов, искать в нем папку RSA, и брать у неё id
    // ведь если программа будет удалена/переустановлена/установлена на новый телефон, нужно, чтобы
    // данные продолжали храниться в папке RSA, а не создавать ещё одну папку с таким же именем
    //
    // ! если пользователь создаст на облаке руками папку RSA, переместит в неё файлы из
    // оригинальной папки, а оригинальную папку затем удалит, то программа эту новую папку не увидит
    // (так как была создана не программой) и создаст новую папку
    private static final String ROOT_FOLDER_ID = "root";

    final String API_KEI = "AIzaSyABrX6WwM2WQC2djvG6auBwgTQqZzL5_kw";

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

    private DriveServiceHelper mDriveServiceHelper;
    private String mOpenFileId;

    private EditText mFileTitleEditText;
    private EditText mDocContentEditText;

    private FirebaseAuth mAuth;

    private RecursiveFileObserverNew mFileObserver;

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
        findViewById(R.id.open_btn).setOnClickListener(view -> openFilePicker());
        findViewById(R.id.create_btn).setOnClickListener(view -> createFile());
        findViewById(R.id.save_btn).setOnClickListener(view -> saveFile());
        findViewById(R.id.query_btn).setOnClickListener(view -> query());
//        findViewById(R.id.upload_btn).setOnClickListener(view -> openFilePickerToUpload());
//        findViewById(R.id.upload_btn).setOnClickListener(view -> uploadFolder(new java.io.File(sMainDir, "20201208_152907"), null));
//        findViewById(R.id.upload_btn).setOnClickListener(view -> uploadFolderNew(new java.io.File(sMainDir, "20201208_152907"), null));
        findViewById(R.id.upload_btn).setOnClickListener(view -> uploadFolderNew(new java.io.File(sMainDir.toString()), null));
        findViewById(R.id.account).setOnClickListener(view -> selectAccount());
//        findViewById(R.id.get_folder_id).setOnClickListener(view -> getGoogleFolderId(new java.io.File(sMainDir.toString() + "/crashReports/Folder/nuclib.txt")));
//        findViewById(R.id.get_folder_id).setOnClickListener(view -> uploadFileByFilePath(new java.io.File(sMainDir.toString() + "/SomeNewFolder/SomeFolder2")));
        findViewById(R.id.get_folder_id).setOnClickListener(view -> uploadFileByFilePath(new java.io.File(sMainDir.toString() + "/SomeNewFolder/SomeNewFile.txt")));
//        findViewById(R.id.create_folder_2).setOnClickListener(view -> mDriveServiceHelper.createFolderInDrive());
        findViewById(R.id.create_folder_2).setOnClickListener(view -> getFiles());
//        findViewById(R.id.get_folder_id).setOnClickListener(view -> getGoogleFolderIdNew(new java.io.File(sMainDir.toString() + "/crashReports/Folder/nuclib.txt")));

        // Authenticate the user. For most apps, this should be done when the user performs an
        // action that requires Drive access rather than in onCreate.
        requestSignIn();
    }

    private void getFiles() {
//        String name = "nuclib.txt";
        String name = "com.atomtex.ascanner";
//        String id = "1ZDzWN2qNy_osWVMmh0y49ukDUtkS84aG";
//        String id = "17IqLtwR-9XK8EcMEtMhn_jTlYFw6YQqb";
//        String id = "1PDWSeibe-xhFOkSaCE4wSN5xXZKMc-vL";//20201208_153017
        String id = "14W2pd6V3N2NWTcjhxl5LYpyve3L24HkE";//com.atomtex.ascanner
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
                    if (uri != null) {
                        openFileFromFilePicker(uri);
                    }
                }
                break;
            case REQUEST_CODE_OPEN_DOCUMENT_TO_UPLOAD:
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    Uri uri = resultData.getData();
                    if (uri != null) {
                        uploadFile(new java.io.File(Environment.getExternalStorageDirectory(), "nuclib.txt"), MIME_TEXT_FILE, null);
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

    /*private void uploadFile(java.io.File localFile) {
        Log.e(TAG, "upload: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Uploading a file.");

            mDriveServiceHelper.uploadFile(localFile, MIME_TEXT_FILE, null)
                    .addOnSuccessListener(fileId -> Log.e(TAG, "createFile ID = : " + fileId)*//*readFile(fileId)*//*)
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Couldn't create file.", exception));
        }
    }*/

    //альтернативная версия
    /*private void uploadFile(java.io.File localFile, String type) {
        Log.e(TAG, "upload: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Uploading a file.");

            mDriveServiceHelper.uploadFile(localFile, type)
                    .addOnSuccessListener(fileId -> Log.e(TAG, "createFile ID = : " + fileId)*//*readFile(fileId)*//*)
                    .addOnFailureListener(exception ->
                            Log.e(TAG, "Couldn't create file.", exception));
        }
    }*/


    //squorpikkor Не корректно работает, скидывает все файлы в корень, игнорируя папки
    //косяк в том, что папки создаутся в отдельном треде, а файлы начинают аплодиться в главном треде не дожидаясь пока метод загрузки вернет id созданной папки
    /*private void uploadFolder(java.io.File folder, String mainId) { //
        Log.e(TAG, "upload: TRY");
        String name = folder.getName();
        Log.e(TAG, "uploadFolder: FOLDER NAME = " + name);
        Log.e(TAG, "uploadFolder: FOLDER PATH = " + folder.getAbsolutePath());
        String id = createFolder(name, mainId);//создать папку с именем и ID папки родителя. Получить id новой папки

        Log.e(TAG, "uploadFolder: FOLDER ID = " + id);
        java.io.File[] files = folder.listFiles(); //получить список файлов в папке, которую надо скопировать
        Log.e(TAG, "uploadFolder: files.size = " + files.length);
//        Log.e(TAG, "uploadFolder:  files.size = " + new java.io.File(Environment.getExternalStorageDirectory().getAbsolutePath()).listFiles().length);
        for (java.io.File file : files) {
            file.getName();
            Log.e(TAG, "........... id = " + id);
            if (!file.isDirectory())
                uploadFile(file, MIME_TEXT_FILE, id); //если это файл, аплодить его в папку с id новой папки
            else
                uploadFolder(file, id);//если это директория, то рекурсивно вызывается весь метод uploadFolder
        }
    }*/

    //альтернативная версия 2
    //Перед созданием файла проверяется (checkIfExist), если такой файл уже существует, то создаваться не будет.
    // При этом проверяется не весь список имен, а только список имен у родителя, потому как файл с таким именем может быть где-то
    // в другом месте (если бы проверялось по списку всех имен, файл не был бы создан, хоть в текущей директории такого файла и нет)
    //Метод uploadFile получает через метод checkIfExist список всех файлов с именем localFile.getName() при этом все эти файлы только для родителя с id = file_Id
    // Т.е. перед тем как создать файл "New" в папке "Folder" получаю список всех файлов с именем "New", находящихся в этой папке (на gDrive может в одной директории находиться несколько
    // файлов с одинаковым названием, здесь имя — это НЕ уникальный идентификатор, роль которого выполняет ID)
    // и, если таких файлов нет ни одного, создается.
    // Если на checkIfExist в качестве id подать null, то проверятся будет список файлов в корне
    private void uploadFile(java.io.File localFile, String type, String file_Id) {
        Log.e(TAG, "upload: TRY");
        if (mDriveServiceHelper != null/* && !localFile.getName().equals("null")*/) {
            Log.e(TAG, "Uploading a file.");

            mDriveServiceHelper.checkIfExist(localFile.getName(), file_Id).addOnSuccessListener(fileList -> {

                if (fileList.getFiles() != null && fileList.getFiles().size() == 0) {
                    Log.e(TAG, ".....FILE NOT EXISTS!!!");

                    mDriveServiceHelper.uploadFile(localFile, type, file_Id)
                            .addOnSuccessListener(fileId -> Log.e(TAG, "createFile ID = : " + fileId)/*readFile(fileId)*/)
                            .addOnFailureListener(exception ->
                                    Log.e(TAG, "Couldn't create file.", exception));

                } else {
                    Log.e(TAG, ".....FILE ALREADY EXISTS!!!");
                }

            });
        }
    }

    // squorpikkor
    // Метод работает в связке с createFolderNew
    // Загружается папка вместе с подпапками и файлами в ней
    // Вся загрузка идет в отдельном треде: если среди загружаемых файлов попадается папка,
    // то создается папка с именем загружаемой папки, создание папки происходит в отдельном треде
    // при успешном создании в эту папку закидываются файлы из списка файлов папки
    // Т.е. каждая папка открывает свой собственный тред для загрузки
    // подпапки загружаются рекурсивно
    private void uploadFolderNew(java.io.File folder, String mainId) { //
        Log.e(TAG, "upload: TRY");
        String name = folder.getName();
        Log.e(TAG, "uploadFolder: FOLDER NAME = " + name);
        Log.e(TAG, "uploadFolder: FOLDER PATH = " + folder.getAbsolutePath());
        createFolderNew(folder, mainId);//создать папку с именем и ID папки родителя. Получить id новой папки
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


    List<File> getDriveFileList() {
        List<File>[] temp = new List[0];
        if (mDriveServiceHelper != null) {

            //Перечень доступных свойств настраивается в mDriveService.files().list().setFields("files( ЗДЕСЬ )").execute()); (см. queryFiles())
            mDriveServiceHelper.queryFiles()
                    .addOnSuccessListener(fileList -> {
                        temp[0] = fileList.getFiles();
                    })
                    .addOnFailureListener(exception -> Log.e(TAG, "Unable to query files.", exception));
        }
        return temp[0];
    }

    List<File> temporally;

    List<File> getDriveFileListByParentID(String id) {
//        List<File>[] temp = new List[1];
        if (mDriveServiceHelper != null) {

            //Перечень доступных свойств настраивается в mDriveService.files().list().setFields("files( ЗДЕСЬ )").execute()); (см. queryFiles())
            mDriveServiceHelper.queryFiles()
                    .addOnSuccessListener(fileList -> {
                        Log.e(TAG, "...ON SUCCESS...");
                        List<File> allFiles = fileList.getFiles();
                        for (File file : allFiles) {
                            Log.e(TAG, ".....all" + file.getName());
                        }
                        List<File> selectedFiles = new ArrayList<>();
                        for (File file : allFiles) {
                            if (!file.getTrashed() && file.getParents().get(0).equals(id)) {
                                selectedFiles.add(file);
                                Log.e(TAG, "..........file: " + file.getName());
                            }
                        }
//                        temp[0] = selectedFiles;
                        temporally = selectedFiles;
                    })
                    .addOnFailureListener(exception -> Log.e(TAG, "Unable to query files.", exception));
        }
//        return temp[0];
        return temporally;
    }

    void getGoogleFolderIdNew(java.io.File inputFile) {

        String path = inputFile.getAbsolutePath();
        Log.e(TAG, "getGoogleFolderId: PATH = " + path);
        ArrayList<String> parentList = new ArrayList<>();
//        Log.e(TAG, "getGoogleFolderId: PARENTS = " + path.replace(sMainDir.getAbsolutePath(), "").replace(file.getName(), ""));
        path = path.replace(sMainDir.getAbsolutePath() + "/", "").replace(inputFile.getName(), "");
        Log.e(TAG, "getGoogleFolderId: PATH = " + path);
        String[] s = path.split("/");
        parentList.add("com.atomtex.ascanner");//в самое начало закидываю самую первую папку
        for (int i = 0; i < s.length; i++) {
            parentList.add(s[i]);
        }
        for (int i = 0; i < parentList.size(); i++) {
            Log.e(TAG, ".....parentList(" + i + ") = " + parentList.get(i));
        }
        String id;
        id = "root";


//        List<File>[] temp = new List[1];
        if (mDriveServiceHelper != null) {

            //Перечень доступных свойств настраивается в mDriveService.files().list().setFields("files( ЗДЕСЬ )").execute()); (см. queryFiles())
            mDriveServiceHelper.queryFiles()
                    .addOnSuccessListener(fileList -> {
                        Log.e(TAG, "...ON SUCCESS...");
                        List<File> allFiles = fileList.getFiles();
                        for (File file : allFiles) {
                            Log.e(TAG, ".....all" + file.getName());
                        }
                        List<File> selectedFiles = new ArrayList<>();
                        for (File file : allFiles) {
                            if (!file.getTrashed() && file.getParents().get(0).equals(id)) {
                                selectedFiles.add(file);
                                Log.e(TAG, "..........file: " + file.getName());
                            }
                        }
                        //////////////////////getNew(selectedFiles, parentList);
                    })
                    .addOnFailureListener(exception -> Log.e(TAG, "Unable to query files.", exception));
        }
    }

    String searchIdByName(String name, List<File> list) {
        for (File file : list) {
            if (file.getName().equals(name)) return file.getId();
        }
        return "root";
    }

    /*void getNew(List<File> allFiles, ArrayList<String> parentList) {

        for (int i = 0; i < parentList.size(); i++) {
            driveFileList = getDriveFileListByParentID(id);
            Log.e(TAG, "driveFileList.size: " + driveFileList.size());
            id = searchIdByName(parentList.get(0), driveFileList);
        }
        Log.e(TAG, "getGoogleFolderId: RETURNED ID = " + id);

    }*/

    /*List<File> getDriveFileListByParentID(List<File> allFiles, String parent) {
        List<File> list = new ArrayList<>();
        for (File file:allFiles) {
            if (file.getName().equals(parent))list.add(file)
        }

        for (int i = 0; i < parentList.size(); i++) {
            driveFileList = getDriveFileListByParentID(id);
            Log.e(TAG, "driveFileList.size: " + driveFileList.size());
            id = searchIdByName(parentList.get(0), driveFileList);
        }
    }*/


    void /*String*/ getGoogleFolderId(java.io.File file) {
        String path = file.getAbsolutePath();
        Log.e(TAG, "getGoogleFolderId: PATH = " + path);
        ArrayList<String> parentList = new ArrayList<>();
//        Log.e(TAG, "getGoogleFolderId: PARENTS = " + path.replace(sMainDir.getAbsolutePath(), "").replace(file.getName(), ""));
        path = path.replace(sMainDir.getAbsolutePath() + "/", "").replace(file.getName(), "");
        Log.e(TAG, "getGoogleFolderId: PATH = " + path);
        String[] s = path.split("/");
        parentList.add("com.atomtex.ascanner");//в самое начало закидываю самую первую папку
        for (int i = 0; i < s.length; i++) {
            parentList.add(s[i]);
        }
        for (int i = 0; i < parentList.size(); i++) {
            Log.e(TAG, ".....parentList(" + i + ") = " + parentList.get(i));
        }
        String id;
        id = "root";
        List<File> driveFileList;
        //todo два цикла закинуть в один
        for (int i = 0; i < parentList.size(); i++) {
            driveFileList = getDriveFileListByParentID(id);
            Log.e(TAG, "driveFileList.size: " + driveFileList.size());
            id = searchIdByName(parentList.get(0), driveFileList);
        }
        Log.e(TAG, "getGoogleFolderId: RETURNED ID = " + id);

    }

    //По-сути этот метод не создает новую папку, а загружает папку
    //На вход получает ФАЙЛ и загружает его
    //Чтобы создать папку нужен будет другой метод, который на вход будет принимать ИМЯ и ID родителя
    //TODO есть смысл переименовать этот метод в uploadFolderNew, чтобы не было путаницы

    // squorpikkor
    // Метод работает в связке с uploadFolderNew
    // Загружается папка вместе с подпапками и файлами в ней
    // Вся загрузка идет в отдельном треде: если среди загружаемых файлов попадается папка,
    // то создается папка с именем загружаемой папки, создание папки происходит в отдельном треде
    // при успешном создании в эту папку закидываются файлы из списка файлов папки
    // Т.е. каждая папка открывает свой собственный тред для загрузки
    // подпапки загружаются рекурсивно

    //Перед созданием папки проверяется (checkIfExist), если такая папка уже существует, то создаваться не будет.
    // Если существует, продолжается проверка подпапки и т.д.
    // При этом проверяется не весь список имен, а только список имен у родителя, потому как папка с таким именем может быть где-то
    // в другом месте (если бы проверялось по списку всех имен, папка не была бы создана, хоть в текущей директории такой папки и нет)
    //Метод createFolderNew рекурсивно получает через метод checkIfExist список всех файлов с именем folder.getName() при этом все эти файлы только для родителя с id = folderId
    // Т.е. перед тем как создать папку "New" в папке "Folder" получаю список всех файлов с именем "New", находящихся в этой папке (на gDrive может в одной директории находиться несколько
    // файлов с одинаковым названием, здесь имя — это НЕ уникальный идентификатор, роль которого выполняет ID) и, если таких файлов нет ни одного, создается.
    // Если на checkIfExist в качестве id подать null, то проверятся будет список файлов в корне
    private void createFolderNew(java.io.File folder, String folderId) {
        Log.e(TAG, "createFolder: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Creating a folder.");

            mDriveServiceHelper.checkIfExist(folder.getName(), folderId).addOnSuccessListener(fileList -> {

                if (fileList.getFiles() != null && fileList.getFiles().size() == 0) {
                    Log.e(TAG, ".....FILE NOT EXISTS!!!");

                    mDriveServiceHelper.createFolder(folder.getName(), folderId)
                            .addOnSuccessListener(fileId -> {
                                Log.e(TAG, "createFolder ID = : " + fileId);

                                if (folder.listFiles() != null) {

                                    for (java.io.File file : folder.listFiles()) {
                                        file.getName();
                                        Log.e(TAG, "........... id = " + fileId);
                                        if (!file.isDirectory())
                                            uploadFile(file, MIME_TEXT_FILE, fileId); //если это файл, аплодить его в папку с id новой папки
                                        else
                                            uploadFolderNew(file, fileId);//если это директория, то вызывается весь метод uploadFolder
                                    }
                                }

                            })
                            .addOnFailureListener(exception -> {
                                Log.e(TAG, "Couldn't create folder.", exception);
                            });


                } else {
                    Log.e(TAG, ".....FILE ALREADY EXISTS!!!");

                    //Если папка существует, берем её ID и перебираем её файлы и (если есть) подпапки

                    String id = fileList.getFiles().get(0).getId();

                    if (folder.listFiles() != null) {

                        for (java.io.File file : folder.listFiles()) {
                            file.getName();
                            Log.e(TAG, "........... id = " + id);
                            if (!file.isDirectory())
                                uploadFile(file, MIME_TEXT_FILE, id); //если это файл, аплодить его в папку с id новой папки
                            else
                                uploadFolderNew(file, id);//если это директория, то вызывается весь метод uploadFolder
                        }
                    }

                }


            });



            /*mDriveServiceHelper.createFolder(folder.getName(), folderId)
                    .addOnSuccessListener(fileId -> {
                        Log.e(TAG, "createFolder ID = : " + fileId);

                        for (java.io.File file : folder.listFiles()) {
                            file.getName();
                            Log.e(TAG, "........... id = " + fileId);
                            if (!file.isDirectory())
                                uploadFile(file, MIME_TEXT_FILE, fileId); //если это файл, аплодить его в папку с id новой папки
                            else
                                uploadFolderNew(file, fileId);//если это директория, то вызывается весь метод uploadFolder
                        }

                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create folder.", exception);
                    });*/
        }

    }

    //Пусть будет
//    private void createFolderAndStop(String name, String folderId) {
    private void createFolderAndStop(java.io.File folder, String folderId) {
        /*Log.e(TAG, "createFolder: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Creating a folder.");

            mDriveServiceHelper.createFolder(name, folderId)
                    .addOnSuccessListener(fileId -> {
                        Log.e(TAG, "createFolder ID = : " + fileId);
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create folder.", exception);
                    });
        }*/

        //-------------------------------------------------

        Log.e(TAG, "createFolder: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Creating a folder.");

            mDriveServiceHelper.checkIfExist(folder.getName(), folderId).addOnSuccessListener(fileList -> {

                if (fileList.getFiles() != null && fileList.getFiles().size() == 0) {
                    Log.e(TAG, ".....FILE NOT EXISTS!!!");

                    mDriveServiceHelper.createFolder(folder.getName(), folderId)
                            .addOnSuccessListener(fileId -> Log.e(TAG, "createFolder ID = : " + fileId))
                            .addOnFailureListener(exception -> Log.e(TAG, "Couldn't create folder.", exception));
                } else {
                    Log.e(TAG, ".....FILE ALREADY EXISTS!!!");
                }
            });
        }
    }

    //Пусть будет
    private String createFolder(String name, String folderId) {
        final String[] id = new String[1];
        Log.e(TAG, "createFolder: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Creating a folder.");

            mDriveServiceHelper.createFolder(name, folderId)
                    .addOnSuccessListener(fileId -> {
                        Log.e(TAG, "createFolder ID = : " + fileId);
                        id[0] = fileId;
                        /*readFile(fileId)*/
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create folder.", exception);
//                        id[0] = "0";
                    });
        }

//        if (!id[0].equals("0") && id[0]!=null)return id[0];
//        else if (id[0].equals("0"))return null;


        /*while (id[0]==null) {
            Log.e(TAG, ".........try get id.......");
            synchronized (this) {
                try {
                    wait(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (id.equals("0"))return null;
        else */
        return id[0];
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

//--------------------------------------------------------------------------------------------------
    //Обертка для uploadFolderByFileList. В uploadFolderByFileList 3 параметра, к тому же этот метод (uploadFolderByFileList) рекурсивный и разделяется на потоки
    //Чтобы было проще с ним работать сделан этот класс только с одним параметром на входе
    //В итоге получается метод, который на вход получает локальную папку, которая загружается на GDrive в
    //СООТВЕТСТВУЮЩУЮ папку, как она находится относительно главной папки (с которой идет синхронизация)
    //Т.е.: папка com.atomtex.ascanner/folder_1/folder_2/folder_3/ будет загружена в MyDrive>com.atomtex.ascanner>folder_1>folder_2
    //Если в папке находятся ещё какие-то папки/файлы, они будут загружены вместе с папкой
    //Если папка уже есть, то она загружаться не будет, но находящиеся в ней фалы будут загружены (если их не было)
    //Если кроме создаваемой папки нет на облаке и её родителя, он будет создан
    //Проблема, которую решает этод метод, в том, что на gDrive нет пути, есть только Id родителя, т.е. нельзя просто указать путь, по которому сохранять папку

    //Добавлена абилити загружать и файлы тоже (изначально только папки)
    void uploadFileByFilePath(java.io.File local_folder) {
        String cuttingPath = local_folder.getAbsolutePath().replace(sMainDir.getAbsolutePath() + "/", "");
//        if (local_folder.isDirectory()) cuttingPath+="/"; //если файл — это директория, то в конце пути добавляю "/"
        Log.e(TAG, "start");
        Log.e(TAG, "beforeCut: " + local_folder.getAbsolutePath());
        Log.e(TAG, "cuttingPath: " + cuttingPath);
        Log.e(TAG, "-------IS FOLDER = " + local_folder.isDirectory());
        uploadFolderByFileList(local_folder, cuttingPath, null);
//        if (local_folder.isDirectory()) uploadFolderByFileList(local_folder, cuttingPath, null);
//        else uploadFileByFileList(local_folder, cuttingPath, null);
    }

    //Работает через обертку uploadFolderByFilePath. Описание см. там
    //На вход в самый первый раз (до рекурсии) метод получает в переменную cuttingPath всегда полный путь МИНУС все что идет до синхронизируемой папки:
    // если на телефоне файл лежит по адресу: /storage/emulated/0/Android/data/com.atomtex.com/folder/file.txt,
    // то cuttingPath будет выглядеть: com.atomtex.com/folder/file.txt
    // При каждом рекурсивном вызове uploadFolderByFileLis проверяется путь, если путь уже не содержит поддиректории (if (pathArr.length == 1)),
    // то метод аплодит файл/папку по полученному ранее ID, иначе cuttingPath будет уменьшаться на одну директорию СЛЕВА и вместе с полученным ID
    // будет рекурсивно вызван метод uploadFolderByFileList уже с новыми значениями ID и короткого пути
    // Другими словами, метод рекурсивно перебирает названия папок на gDrive от синхронизируемой папки до самого последнего чайлда, каждый раз запоминая ID текущей папки
    // И если чайлд оказался последним, то в папке с ID родителя создает файл (если его там ещё не было)
    // Итого: метод загружает файл/папку в папку по ID этой целевой папки, зная только путь локальной папки на телефоне
    void uploadFolderByFileList(java.io.File local_folder, String cuttingPath, String file_Id) {

        String path_for_file = local_folder.getAbsolutePath().replace(cuttingPath, "");
        java.io.File file_cut = new java.io.File(path_for_file);
        Log.e(TAG, "file_cut path: " + file_cut.getAbsolutePath());

        mDriveServiceHelper.checkIfExist(file_cut.getName(),
                file_Id).addOnSuccessListener(fileList -> {
            Log.e(TAG, "------------------------------------------------------");
            Log.e(TAG, "uploadFolderByFileList: ON SUCCESS: file_cut.getName() - " + file_cut.getName() + ", file_Id - " + file_Id);
            //получаю по имени файла и по ID родителя список файлов (вообще список всегда будет состоять только из одного ЕДИНСТВЕННОГО файла)
            // если такой файл и беру у этого файла ID
            if (fileList.getFiles() != null && fileList.getFiles().size() == 0) {
                //если не существует, создать папку с подпапками и закончить рекурсию (дальше уже не будет вызываться uploadFolderByFileList)
                Log.e(TAG, ".....FILE NOT EXISTS ON CLOUD!!!");
                //Вариант с рекуривным аплодом подпапок и файлов
//                createFolderNew(file_cut, file_Id);
                //Альтернативный вариант без рекуривного аплода подпапок и файлов
//                createFolderAndStop(file_cut.getName(), file_Id);
                //Альтернативный вариант без рекуривного аплода подпапок и файлов
                createFolderAndStop(file_cut, file_Id);

                //---------------------------------------------
//                String[] pathArr = cuttingPath.split("/");
//                String id = fileList.getFiles().get(0).getId();
//                String newPath = cuttingPath.replace(pathArr[0]+"/", "");// folder_1/folder_2/folder_3 -> folder_2/folder_3
//                Log.e(TAG, "NEW path - " + newPath);
//                uploadFolderByFileList(local_folder, newPath, id);
                //---------------------------------------------


//                if (local_folder.isDirectory()) createFolderNew(file_cut, file_Id);
//                else uploadFile(local_folder, MIME_TEXT_FILE, file_Id);
            } else {
                Log.e(TAG, ".....FILE EXISTS ON CLOUD!!!");
                String[] pathArr = cuttingPath.split("/");
                String id = fileList.getFiles().get(0).getId();

                if (pathArr.length == 1) {
                    Log.e(TAG, "uploadFolderByFileList: pathArr.length == 1");
                    if (local_folder.isDirectory())createFolderNew(local_folder, id);
                    else uploadFile(local_folder, MIME_TEXT_FILE, id);
                } else {
                    Log.e(TAG, "uploadFolderByFileList: pathArr.length != 1");
                    Log.e(TAG, "OLD path - " + cuttingPath);
                    String newPath = cuttingPath.replace(pathArr[0]+"/", "");// folder_1/folder_2/folder_3 -> folder_2/folder_3
                    Log.e(TAG, "NEW path - " + newPath);
                    uploadFolderByFileList(local_folder, newPath, id);
                }
            }
        }).addOnFailureListener(exception -> {
            Log.e(TAG, "CAN NOT uploadFolderByFileList", exception);
            Log.e(TAG, "CAN NOT upload: " + local_folder + ", cuttingPath = " + cuttingPath + ", id = " + file_Id);
            tryToUploadLater(local_folder, cuttingPath, file_Id);
        });
    }


    //Если файл/папку не удалось загрузить, то вызывается этот метод
    //Метод создает новый поток для каждого незагруженного файла, ждет 10 сек и пытается загрузить файл снова
    // todo временная мера, надо сделать, чтобы путь для незагруженного файла сохранялся в файл и попытки загрузки шли по сохраненному в файл пути
    //  так, файл будет дозагружен даже после перезапускав программы
    void tryToUploadLater(java.io.File local_folder, String cuttingPath, String file_Id) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(10000);
                    Log.e(TAG, "--- try after 10 sec ---------" + local_folder.getName() + "------------");
                    uploadFolderByFileList(local_folder, cuttingPath, file_Id);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }



//--------------------------------------------------------------------------------------------------


    /*private RecursiveFileObserver createFileObserver(final String dirPath) {
        Log.e(TAG, "♦♦♦createFileObserver: START");
        return new RecursiveFileObserver(dirPath, RecursiveFileObserver.CREATE*//* | RecursiveFileObserver.DELETE
                | RecursiveFileObserver.MOVED_FROM | RecursiveFileObserver.MOVED_TO*//*) {

            @Override
            public void onEvent(final int event, final String name) {
                Log.e(TAG, "♦♦♦onEvent: " + event);

                //Банальный, тупой но рабочий способ загрузки изменений в локальной папке на gDrive:
                //При срабатывании Обсервера начинается загрузка ВСЕЙ папки, так как будут загружаться только файлы,
                // которых нет на gDrive (так работает мой аплод), то в итоге загрузятся только те файлы, которых нет на gDrive
                // т.е. при добавлении файла этот файл автоматом загружается в соответствующую папку.
                // минус в том, что при срабатывании Обсервера будут перебираться на совпадение ВСЕ папки
                /////uploadFolderNew(new java.io.File(sMainDir.toString()), null);


                //Новая версия, при срабатывании Обсервера загружает файл или папку в конкретную директорию
                Log.e(TAG, ". ......................................................onEvent: Path = " + name);
                uploadFileByFilePath(new java.io.File(name));
                //////startWatching();
                //////mFileObserver = createFileObserver(sMainDir.getAbsolutePath());

            }
        };
    }*/

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

    private RecursiveFileObserverNew createFileObserver(final String dirPath) {
        Log.e(TAG, "♦♦♦createFileObserver: START");
        return new RecursiveFileObserverNew(dirPath, (event, file) -> {
            Log.e(TAG, "♦♦♦onEvent: " + returnEvent(event));
            uploadFileByFilePath(file);
        });
    }

}