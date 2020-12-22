package com.squorpikkor.mygdrive2;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.squorpikkor.mygdrive2.MainActivity.TAG;

/**
 * A utility for performing read/write operations on Drive files via the REST API and opening a
 * file picker UI via Storage Access Framework.
 */
public class DriveServiceHelper {
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Drive mDriveService;

    public DriveServiceHelper(Drive driveService) {
        mDriveService = driveService;
    }

    /**
     * Добавлено
     * Creates folder in the user's My Drive folder and returns its file ID.
     */
    public Task<String> createFolder(String name, String id) {
        String fileId;
        if (id == null) fileId = "root";
        else fileId = id;

//        Tasks tasks;

        /*checkIfExist(name).addOnSuccessListener(fileList -> {
            if (fileList != null && fileList.getFiles() != null && fileList.getFiles().size() != 0) {
                Log.e(TAG, ".....FILE NOT EXISTS!!!");
            } else Log.e(TAG, ".....FILE ALREADY EXISTS!!!");

        });*/


        return Tasks.call(mExecutor, () -> {
            File metadata = new File()
                    .setParents(Collections.singletonList(fileId))
                    .setMimeType("application/vnd.google-apps.folder")
                    .setName(name);

            File googleFile = mDriveService.files().create(metadata).execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }

            return googleFile.getId();
        });


    }

    /*public Task<String> createFolderIfNotExists(String name, String id) {
        checkIfExist(name).addOnSuccessListener(fileList -> {
            if (fileList != null && fileList.getFiles() != null && fileList.getFiles().size() != 0) {
                Log.e(TAG, ".....FILE NOT EXISTS!!!");
            } else Log.e(TAG, ".....FILE ALREADY EXISTS!!!");

        });

        return Tasks.call(mExecutor, () -> {
            File metadata = new File()
                    .setParents(Collections.singletonList(fileId))
                    .setMimeType("application/vnd.google-apps.folder")
                    .setName(name);

            File googleFile = mDriveService.files().create(metadata).execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }

            return googleFile.getId();
        });
    }*/

    /**
     * Creates a text file in the user's My Drive folder and returns its file ID.
     */
    public Task<String> createFile() {
        return Tasks.call(mExecutor, () -> {
            File metadata = new File()
                    .setParents(Collections.singletonList("root"))
                    .setMimeType("text/plain")
                    .setName("Untitled file");

            File googleFile = mDriveService.files().create(metadata).execute();
            if (googleFile == null) {
                throw new IOException("Null result when requesting file creation.");
            }

            return googleFile.getId();
        });
    }

    //альтернативная версия
    public Task<String> uploadFile(final java.io.File localFile, String type) {
        return Tasks.call(mExecutor, () -> {
            // Retrieve the metadata as a File object.
            File fileMetadata = new File();
            fileMetadata.setName(localFile.getName());
            java.io.File filePath = new java.io.File(localFile.getAbsolutePath());
            FileContent mediaContent = new FileContent(type, filePath);
            File file = mDriveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();
            System.out.println("File ID: " + file.getId());

            return file.getId();
        });
    }

    // TO UPLOAD A FILE ONTO DRIVE
    public Task<String> uploadFile(final java.io.File localFile,
                                 final String mimeType, @Nullable final String folderId) {
        Log.e(TAG, "uploadFile: " + folderId);
        return Tasks.call(mExecutor, () -> {
            // Retrieve the metadata as a File object.
            List<String> root;
            if (folderId == null) {
                root = Collections.singletonList("root");
            } else {
                root = Collections.singletonList(folderId);
            }
            File metadata = new File()
                    .setParents(root)
                    .setMimeType(mimeType)
                    .setName(localFile.getName());
            FileContent fileContent = new FileContent(mimeType, localFile);
            File fileMeta = mDriveService.files().create(metadata, fileContent).execute();

            if (fileMeta == null) {
                throw new IOException("Null result when requesting file creation.");
            }

            return fileMeta.getId();
        });
    }

    /**
     * Opens the file identified by {@code fileId} and returns a {@link Pair} of its name and
     * contents.
     */
    public Task<Pair<String, String>> readFile(String fileId) {
        return Tasks.call(mExecutor, () -> {
            // Retrieve the metadata as a File object.
            File metadata = mDriveService.files().get(fileId).execute();
            String name = metadata.getName();

            // Stream the file contents to a String.
            try (InputStream is = mDriveService.files().get(fileId).executeMediaAsInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                String contents = stringBuilder.toString();

                return Pair.create(name, contents);
            }
        });
    }

    /**
     * Updates the file identified by {@code fileId} with the given {@code name} and {@code
     * content}.
     */
    public Task<Void> saveFile(String fileId, String name, String content) {
        return Tasks.call(mExecutor, () -> {
            // Create a File containing any metadata changes.
            File metadata = new File().setName(name);

            // Convert content to an AbstractInputStreamContent instance.
            ByteArrayContent contentStream = ByteArrayContent.fromString("text/plain", content);

            // Update the metadata and contents.
            mDriveService.files().update(fileId, metadata, contentStream).execute();
            return null;
        });
    }



    /**
     * Returns a {@link FileList} containing all the visible files in the user's My Drive.
     *
     * <p>The returned list will only contain files visible to this app, i.e. those which were
     * created by this app. To perform operations on files not created by the app, the project must
     * request Drive Full Scope in the <a href="https://play.google.com/apps/publish">Google
     * Developer's Console</a> and be submitted to Google for verification.</p>
     */
    public Task<FileList> queryFiles() {
//        return Tasks.call(mExecutor, () -> mDriveService.files().list().setSpaces("drive").execute());//так было

        /*Files.List request=service().files().list().setQ(
                "mimeType='application/vnd.google-apps.folder' and trashed=false and name='"+folderName+"'");
        FileList files = request.execute();*/

//        todo "mimeType='application/vnd.google-apps.folder' and trashed=false");

        return Tasks.call(mExecutor, () -> mDriveService.files().list().setFields("files(id, name, parents, mimeType, trashed)").execute()); //я изменил, теперь можно получать инфу: id, имя, id родителя, mime тип, удален ли файл
    }

    public Task<FileList> checkIfExist(String name/*, String parentId*/) {
        Log.e(TAG, "checkIfExist");
//        return Tasks.call(mExecutor, () -> mDriveService.files().list().setFields("files(id, name, parents, mimeType) and trashed=false and name='"+name+"'").execute());
        return Tasks.call(mExecutor, () -> mDriveService.files().list().setQ("trashed=false and name='"+name+"'").execute());
    }

    /**
     * Returns an {@link Intent} for opening the Storage Access Framework file picker.
     */
    public Intent createFilePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");

        return intent;
    }

    /**
     * Opens the file at the {@code uri} returned by a Storage Access Framework {@link Intent}
     * created by {@link #createFilePickerIntent()} using the given {@code contentResolver}.
     */
    public Task<Pair<String, String>> openFileUsingStorageAccessFramework(
            ContentResolver contentResolver, Uri uri) {
        return Tasks.call(mExecutor, () -> {
            // Retrieve the document's display name from its metadata.
            String name;
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    name = cursor.getString(nameIndex);
                } else {
                    throw new IOException("Empty cursor returned for file.");
                }
            }

            // Read the document's contents as a String.
            String content;
            try (InputStream is = contentResolver.openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                content = stringBuilder.toString();
            }

            return Pair.create(name, content);
        });
    }

//--------------------------------------------------------------------------------------------------

    public void createFolderInDrive() {
        boolean existed = checkExistedFolder("MyFolder");

        if (!existed) {
            File fileMetadata = new File();
            fileMetadata.setName("MyFolder");
            fileMetadata.setMimeType("application/vnd.google-apps.folder");

            File file = null;
            try {
                file = mDriveService.files().create(fileMetadata)
                        .setFields("id")
                        .execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Folder ID: " + file.getId());

            Log.e(this.toString(), "Folder Created with ID:" + file.getId());



//            Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Folder is existed already");
        }


    }

    private boolean checkExistedFolder(String folderName) {
        //File file = null;
        boolean existedFolder = true;
        // check if the folder exists already
        try {
            //String query = "mimeType='application/vnd.google-apps.folder' and trashed=false and title='" + "Evacuation Kit" + "'";
            String query = "mimeType='application/vnd.google-apps.folder' and trashed=false and name='" + folderName + "'";
            // add parent param to the query if needed
            //if (parentId != null) {
            //query = query + " and '" + parentId + "' in parents";
            // }

            Drive.Files.List request = mDriveService.files().list().setQ(query);
            FileList fileList = request.execute();

            if (fileList.getFiles().size() == 0) {
                // file = fileList.getFiles().get(0);
                existedFolder = false;

            }


        } catch (IOException e) {
            e.printStackTrace();

        }
        return existedFolder;
    }




}
