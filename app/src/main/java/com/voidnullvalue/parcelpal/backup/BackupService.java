package com.voidnullvalue.parcelpal.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.voidnullvalue.parcelpal.data.DatabaseHelper;
import com.voidnullvalue.parcelpal.data.ShipmentRepository;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public final class BackupService {
    private static final int MAX_BACKUP_BYTES = 20_000_000;
    private final Context context;
    private final ShipmentRepository repository;

    public BackupService(Context context) {
        this.context = context.getApplicationContext();
        this.repository = new ShipmentRepository(context);
    }

    public void exportTo(Uri uri, char[] password) throws Exception {
        char[] copy = password == null ? new char[0] : Arrays.copyOf(password, password.length);
        try {
            String json = repository.exportJson().toString(2);
            String encoded = BackupCodec.encode(json, copy);
            ContentResolver resolver = context.getContentResolver();
            try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Could not open the selected file");
                out.write(encoded.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } finally {
            Arrays.fill(copy, '\0');
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    public boolean requiresPassword(Uri uri) throws IOException {
        return BackupCodec.isEncrypted(read(uri));
    }

    public DatabaseHelper.ImportSummary importFrom(Uri uri, char[] password) throws Exception {
        char[] copy = password == null ? new char[0] : Arrays.copyOf(password, password.length);
        try {
            String raw = read(uri);
            String json = BackupCodec.decode(raw, copy);
            return repository.importJson(new JSONObject(json));
        } catch (GeneralSecurityException e) {
            throw new GeneralSecurityException("Wrong password or damaged encrypted backup", e);
        } finally {
            Arrays.fill(copy, '\0');
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private String read(Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open the selected file");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BACKUP_BYTES) throw new IOException("Backup exceeds 20 MB");
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
