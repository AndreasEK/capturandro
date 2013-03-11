package no.finn.capturandro;

import android.content.Intent;

public interface CapturandroEventHandler {
    public void onActivityResult(int requestCode, int resultCode, Intent data);
    public void onImportSuccess(String filename);
    public void onImportFailure(Exception e);
}