package com.unicornwhodev.visiondatasetstudio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Standalone test-APK process: Android/Java only; the target APK's Kotlin runtime is not visible here. */
public final class SafFaultProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String key=uri.getLastPathSegment();
        if(key==null || !key.matches("[a-z-]+"))throw new IllegalArgumentException("key");
        File file=new File(getContext().getCacheDir(),"saf-"+key);
        if(mode.contains("w")) {
            if(key.equals("denied"))throw new FileNotFoundException("Injected storage removed");
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        if(key.equals("unreadable"))throw new FileNotFoundException("Injected read grant lost");
        if(key.equals("altered")) {
            try(FileOutputStream out=new FileOutputStream(file,true)){out.write("unexpected".getBytes(StandardCharsets.UTF_8));}
            catch(IOException e){throw new FileNotFoundException(e.getMessage());}
        }
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
