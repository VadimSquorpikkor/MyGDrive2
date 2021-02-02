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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;

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
        findViewById(R.id.upload_btn).setOnClickListener(view -> createFolderNew(new java.io.File(sMainDir.toString()), null));
        findViewById(R.id.account).setOnClickListener(view -> selectAccount());
        findViewById(R.id.get_folder_id).setOnClickListener(view -> uploadFileByFilePath(new java.io.File(sMainDir.toString() + "/SomeNewFolder/SomeNewFile.txt")));
        findViewById(R.id.create_folder_2).setOnClickListener(view -> getFiles());
        findViewById(R.id.check_error_download).setOnClickListener(view -> checkPathSetSize());
        findViewById(R.id.start_queue).setOnClickListener(view -> downloadNextFile());
        findViewById(R.id.reset_queue).setOnClickListener(view -> downloadQueue = new ArrayList<>());//сброс (обнуление) очереди
        requestSignIn();
    }

    void checkPathSetSize() {
        Log.e(TAG, "****************  "+errorPathSet.size()+"  *****************");
        for (String s:errorPathSet) {
            Log.e(TAG, "checkPathSetSize: " + s);
        }
          if (errorPathSet.size() > 0) {
              for (String s:errorPathSet) {
                  uploadFileByFilePath(new java.io.File(s));
              }
          }
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
        /////////////////////////pathSet.add(localFile.getAbsolutePath());
        Log.e(TAG, "upload: TRY");
        if (mDriveServiceHelper != null/* && !localFile.getName().equals("null")*/) {
            Log.e(TAG, "Uploading a file.");

            mDriveServiceHelper.checkIfExist(localFile.getName(), file_Id).addOnSuccessListener(fileList -> {

                if (fileList.getFiles() != null && fileList.getFiles().size() == 0) {
                    Log.e(TAG, ".....FILE NOT EXISTS!!!");

                    mDriveServiceHelper.uploadFile(localFile, type, file_Id)
                            .addOnSuccessListener(fileId -> {
                                Log.e(TAG, "createFile ID = : " + fileId);
                                Log.e(TAG, "uploadFile: файл "+localFile.getAbsolutePath()+" успешно загружен, удалить адрес из pathSet");
                                Log.e(TAG, "uploadFile: errorPathSet.size() BEFORE = "+errorPathSet.size());
                                errorPathSet.remove(localFile.getAbsolutePath());
                                Log.e(TAG, "uploadFile: errorPathSet.size() AFTER = "+errorPathSet.size());
                                downloadNextFile();
                            }/*readFile(fileId)*/)
                            .addOnFailureListener(exception -> {
                                Log.e(TAG, "Couldn't create file.", exception);
                                errorPathSet.add(localFile.getAbsolutePath());
                            });

                } else {
                    Log.e(TAG, ".....FILE ALREADY EXISTS!!!");
                    errorPathSet.remove(localFile.getAbsolutePath());
                    downloadNextFile();
                }

            })
                    .addOnFailureListener(exception ->
                            errorPathSet.add(localFile.getAbsolutePath()));
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


    String searchIdByName(String name, List<File> list) {
        for (File file : list) {
            if (file.getName().equals(name)) return file.getId();
        }
        return "root";
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
        //////////////////////////pathSet.add(folder.getAbsolutePath());
        Log.e(TAG, "upload: TRY");
        Log.e(TAG, "uploadFolder: FOLDER NAME = " + folder.getName());
        Log.e(TAG, "uploadFolder: FOLDER PATH = " + folder.getAbsolutePath());
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
                                            createFolderNew(file, fileId);
                                            ////uploadFolderNew(file, fileId);//если это директория, то вызывается весь метод uploadFolder
                                    }
                                }

                            })
                            .addOnFailureListener(exception ->
                                    errorPathSet.add(folder.getAbsolutePath()));


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
                                createFolderNew(file, id);
                                /////uploadFolderNew(file, id);//если это директория, то вызывается весь метод uploadFolder
                        }
                    }

                }


            });

        }

    }



    private void createFolderAndStopNew(java.io.File folder, String cuttingPath, String folderId) {

        //todo проверить, походу сохраняется путь без "/" в конце
        /////updateCash(folder, cuttingPath, folderId);

        Log.e(TAG, "createFolder: TRY");
        if (mDriveServiceHelper != null) {
            Log.e(TAG, "Creating a folder.");
            mDriveServiceHelper.createFolder(folder.getName(), folderId)
                .addOnSuccessListener(fileId -> {
                    Log.e(TAG, "createFolder ID = : " + fileId);

                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "Couldn't create folder.", exception);
                    errorPathSet.add(folder.getAbsolutePath());
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
                        downloadNextFile();
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create folder.", exception);
                    });
        }

        return id[0];
    }

    private void createFolderAndContinue(java.io.File local_folder, java.io.File file, String cuttingPath, String parent_id) {
        if (mDriveServiceHelper != null) {
            mDriveServiceHelper.createFolder(file.getName(), parent_id)
                    .addOnSuccessListener(fileId -> {
                        Log.e(TAG, "createFolder name - "+ file.getName() + ", parentID - " + parent_id + ", created_folder_id - " + fileId);

                        updateCash(file.getAbsolutePath(), fileId);

                        doStuffNew(local_folder, cuttingPath, fileId);
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create folder.", exception);
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


    void uploadFileByFilePath(java.io.File local_folder) {
        String cuttingPath = local_folder.getAbsolutePath().replace(sMainDir.getAbsolutePath()+"/", "");
        getIdFromCash(local_folder, cuttingPath);
//        uploadFolderByFileList(local_folder, cuttingPath, null);
    }

    HashMap<String, String> cashMap = new HashMap<>();


    void getIdFromCash(java.io.File file, String cuttingPath) {
        String id = null;
        Log.e(TAG, ".......getIdFromCash: file.getParent() - "+file.getParent());
            Log.e(TAG, "..cuttingPath before - " + cuttingPath);
        if (cashMap.containsKey(file.getParent())) {
            id = cashMap.get(file.getParent());
            Log.e(TAG, "....................................");
            Log.e(TAG, "..   Есть ID по такому пути! -> "+id);
            Log.e(TAG, "....................................");
            //////cuttingPath = file.getAbsolutePath().replace(""+file.getParent(), "");
            cuttingPath = "";
            Log.e(TAG, "..cuttingPath after - " + cuttingPath);
        }
        uploadFolderByFileList(file, cuttingPath, id);
    }

    void updateCash(String pathToCash, String file_Id) {
        //todo для кэша: может быть такое: путь вместе с его id закеширован, но самого файла (папки) уже нет (пользователь удалил)
        // вставка по id родителя в этом случае не сработает
        //

//        pathToCash = pathToCash+"/";

        //todo кэш для файла не нужен!!!

        //todo в кэш надо будет записывать и id самого файла, тогда можно проверять наличие файла на диске по сохраненному кэшу
//        String pathToCash = local_folder.getAbsolutePath().replace(cuttingPath, "");
        if (!pathToCash.equals("")&&!cashMap.containsKey(pathToCash))cashMap.put(pathToCash, file_Id);
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

    static ArrayList<java.io.File> downloadQueue = new ArrayList<>();

    void uploadFolderByFileList(java.io.File local_folder, String cuttingPath, String parent_Id) {
        Log.e(TAG, "------- cuttingPath = " + cuttingPath);
        Log.e(TAG, "------- filePath = " + local_folder.getAbsolutePath());


        String path_for_file = local_folder.getAbsolutePath().replace(cuttingPath, "");
        Log.e(TAG, "path_for_file: "+path_for_file);
        java.io.File file_cut = new java.io.File(path_for_file);
        Log.e(TAG, "file_cut path: " + file_cut.getAbsolutePath());

        //updateCash(local_folder, file_cut.getAbsolutePath(), parent_Id);

        mDriveServiceHelper.checkIfExist(file_cut.getName(), parent_Id)
                .addOnSuccessListener(fileList -> {
                    Log.e(TAG, "------------------------------------------------------");
                    Log.e(TAG, "uploadFolderByFileList.checkIfExist: ON SUCCESS: name - " + file_cut.getName() + ", parent_Id - " + parent_Id);

                    //String new_id = null;
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
            errorPathSet.add(local_folder.getAbsolutePath());
        });
    }

    //убрал из doStuffNew загрузки файлов и папок — иначе пришлось бы проверять при создании на существование на облаке (ifExits).
    //теперь всё проверяется в uploadFolderByFileList
    void doStuffNew(java.io.File local_folder, String cuttingPath, String new_id) {
        String[] pathArr = cuttingPath.split("/");
        for (int i = 0; i < pathArr.length; i++) {
            Log.e(TAG, "" +i+ ": " + pathArr[i]);
        }
        if (cuttingPath.equals("")) {
            Log.e(TAG, ".....Конец цикла");
            downloadNextFile();
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
                    /////uploadFolderByFileList(local_folder, cuttingPath, file_Id);
                    uploadFileByFilePath(local_folder);//Возможно будет так лучше
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
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

    void downloadNextFile() {
        if (downloadQueue.size()>0){
            downloadQueue.remove(0);
        }
        if (downloadQueue.size()>0){
            Log.e(TAG, "....... Загружаем следующий .......");
            uploadFileByFilePath(downloadQueue.get(0));
        }
    }


    private RecursiveFileObserverNew createFileObserver(final String dirPath) {
        Log.e(TAG, "♦♦♦createFileObserver: START");
        return new RecursiveFileObserverNew(dirPath, (event, file) -> {
            Log.e(TAG, "♦♦♦onEvent: " + returnEvent(event));

            /*Thread thread = new Thread() {
                @Override
                public void run() {
                    uploadFileByFilePath(file);
                }
            };

            thread.start();*/

            downloadQueue.add(file);
            Log.e(TAG, "queue count = " + downloadQueue.size());

            if (downloadQueue.size() == 1) {
                uploadFileByFilePath(downloadQueue.get(0));
            }

            //////uploadFileByFilePath(file);
        });
    }

}