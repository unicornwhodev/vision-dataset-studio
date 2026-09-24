package com.unicornwhodev.visiondatasetstudio;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Platform-only probe for the actual minified Release. A Debug AndroidJUnitRunner
 * can depend on classes renamed/removed by R8; this runner has no app/AndroidX/Kotlin references.
 * It inspects real retained data under the same signer, without changing the Release APK. */
public final class ReleaseContinuityInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) {this.arguments=arguments;start();}
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    private static String hex(byte[] bytes) {
        StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(java.util.Locale.ROOT,"%02x",b));return value.toString();
    }
    private static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(FileInputStream input=new FileInputStream(file)) {byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}
        return hex(digest.digest());
    }
    private static JSONObject json(File file) throws Exception {
        require(file.length()<1024*1024,"QA marker is unexpectedly large");
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(FileInputStream input=new FileInputStream(file)) {byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}
        return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if(arguments.containsKey("backgroundCase")) {
                inspectBackground(result);finish(Activity.RESULT_OK,result);return;
            }
            String caseId=arguments.getString("preservationCase");
            require(caseId!=null && caseId.matches("[a-f0-9]{12}"),"Select an existing synthetic preservation fixture");
            Context context=getTargetContext();
            require((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)==0,"Target must be a real non-debuggable Release");
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
            require(info.signingInfo!=null && info.signingInfo.getApkContentsSigners().length==1,"Unexpected signer count");
            String certificate=hex(MessageDigest.getInstance("SHA-256").digest(info.signingInfo.getApkContentsSigners()[0].toByteArray()));
            require(certificate.equals("51ef3e4abf953c62c8427deaecf4349aefffc2270c593229315722457f126895"),"Distribution signer differs from the pinned identity");
            JSONObject expected=json(new File(context.getFilesDir(),"qa-evidence/preservation/"+caseId+".json"));
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath("vision_dataset_studio.db").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
                require(db.getVersion()==4,"Unexpected schema");
                try(Cursor c=db.rawQuery("SELECT lastRowCursor FROM projects WHERE id=?",new String[]{Long.toString(expected.getLong("project_id"))})) {
                    require(c.moveToFirst() && c.getLong(0)==expected.getLong("cursor"),"Cursor was not retained");
                }
                try(Cursor c=db.rawQuery("SELECT dataJson FROM annotations WHERE sampleId=?",new String[]{expected.getString("sample_id")})) {
                    require(c.moveToFirst() && c.getString(0).equals(expected.getString("annotation_json")),"Human annotation was not retained");
                }
                try(Cursor c=db.rawQuery("SELECT localImagePath,annotationStatus,syncStatus FROM samples WHERE sampleId=?",new String[]{expected.getString("sample_id")})) {
                    require(c.moveToFirst(),"Sample was not retained");
                    require(hash(new File(c.getString(0))).equals(expected.getString("image_sha256")),"Image bytes differ");
                    require(c.getString(1).equals("IN_PROGRESS") && c.getString(2).equals("NOT_EXPORTED"),"Sample state differs");
                }
            }
            JSONObject workflow=json(new File(context.getFilesDir(),"training-fixture/android-workflow-evidence.json"));
            require(workflow.getString("original_sha256_before").equals(hash(new File(context.getFilesDir(),"training-fixture/trainable-vision-fixture.tflite"))),"Original model bytes differ");
            require(workflow.getString("first_final_weights").equals(workflow.getString("second_initial_weights")),"Training continuation receipt differs");
            JSONObject evidence=new JSONObject().put("non_debuggable_release",true).put("certificate_sha256",certificate)
                .put("human_annotation_and_image_retained",true).put("cursor_retained",true).put("original_model_retained",true);
            result.putString("release_update_evidence",evidence.toString());result.putString("stream","Release continuity assertions passed\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            result.putString("stream",failure.getClass().getName()+": "+failure.getMessage()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }

    private void inspectBackground(Bundle result) throws Exception {
        Context context=getTargetContext();
        require((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)==0,"Expected real Release");
        String caseId=arguments.getString("backgroundCase");
        require(caseId!=null && caseId.matches("[0-9]{13}"),"Explicit synthetic background case required");
        JSONObject marker=json(new File(context.getFilesDir(),"long-training-fixture/preparation.json"));
        require(marker.getLong("project_id")==Long.parseLong(caseId),"Preparation marker does not match");
        JSONObject run=json(new File(context.getFilesDir(),"training/"+caseId+".json"));
        require(run.getString("id").equals(marker.getString("run_id")),"Training run changed");
        require(hash(new File(marker.getString("original_model"))).equals(marker.getString("original_sha256")),"Original model changed");
        require(!new File(run.getString("modelFile")).getCanonicalPath().equals(new File(marker.getString("original_model")).getCanonicalPath()),"Training must use a separate model copy");
        var samples=run.getJSONArray("samples");
        for(int i=0;i<samples.length();i++) {
            var sample=samples.getJSONObject(i);
            require(hash(new File(sample.getString("image"))).equals(sample.getString("sha256")),"Training image changed");
        }
        boolean checkpointVerified=false;
        if(!run.isNull("checkpoint")) {
            var checkpoint=run.getJSONObject("checkpoint");var hashes=checkpoint.getJSONObject("files");
            File directory=new File(checkpoint.getString("prefix")).getParentFile();
            var keys=hashes.keys();int count=0;
            while(keys.hasNext()) {String key=keys.next();require(hash(new File(directory,key)).equals(hashes.getString(key)),"Checkpoint bytes changed");count++;}
            require(count>0,"Empty checkpoint");checkpointVerified=true;
        }
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath("vision_dataset_studio.db").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
            try(Cursor c=db.rawQuery("SELECT count(*) FROM annotations a JOIN samples s ON a.sampleId=s.sampleId WHERE s.projectId=?",new String[]{caseId})) {
                require(c.moveToFirst() && c.getInt(0)==marker.getInt("images"),"Human annotations missing");
            }
            try(Cursor c=db.rawQuery("SELECT s.sourceRowIndex,a.dataJson FROM annotations a JOIN samples s ON a.sampleId=s.sampleId WHERE s.projectId=?",new String[]{caseId})) {
                while(c.moveToNext()) {
                    int index=c.getInt(0);var annotation=new JSONObject(c.getString(1));var tags=annotation.getJSONArray("tags");
                    require(tags.length()==1,"Synthetic human tags changed");var tag=tags.getJSONObject(0);
                    require(tag.getString("id").equals("tag-"+index) && tag.getString("label").equals(index%2==0?"rouge":"bleu")
                        && tag.getBoolean("isHumanVerified") && tag.getString("sourceProvenance").equals("human"),"Synthetic human annotation changed");
                }
            }
        }
        JSONObject evidence=new JSONObject().put("project_id",caseId).put("phase",run.getString("phase"))
            .put("completed_steps",run.getInt("completedSteps")).put("total_steps",run.getInt("totalSteps"))
            .put("checkpoint_verified",checkpointVerified).put("original_preserved",true).put("annotations_and_images_preserved",true)
            .put("separate_model_copy",true).put("human_annotation_contents_verified",true)
            .put("weights_changed",!run.isNull("initialWeightProbe") && !run.isNull("finalWeightProbe") && !run.getString("initialWeightProbe").equals(run.getString("finalWeightProbe")))
            .put("initial_loss",run.opt("initialLoss")).put("validation_loss",run.opt("validationLoss"))
            .put("error",run.opt("error"));
        result.putString("release_training_evidence",evidence.toString());result.putString("stream","Actual Release training data inspected\n");
    }
}
