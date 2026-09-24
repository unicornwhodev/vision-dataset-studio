package com.unicornwhodev.visiondatasetstudio.releaseqa;

import android.app.UiAutomation;
import android.content.pm.ApplicationInfo;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** Real optimized APK; fresh active-window snapshots, no app classes or storage access. */
@RunWith(AndroidJUnit4.class)
public final class ReleaseWorkflowTest {
    private static final String APP = "com.unicornwhodev.visiondatasetstudio";
    private UiAutomation automation;

    private static final class Node {
        String text, description, className;
        Rect bounds = new Rect();
        boolean enabled, checkable, checked, scrollable, focused;
        Node parent;
    }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor fd=automation.executeShellCommand(command);
             FileInputStream stream=new FileInputStream(fd.getFileDescriptor())) {
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    private void trace(String step) {
        var progress=new android.os.Bundle();progress.putString("qa_step",step);
        InstrumentationRegistry.getInstrumentation().sendStatus(2,progress);
    }

    private List<Node> tree() throws Exception {
        // UiDevice.findObject queries every display/window. On Honor those
        // repeated Binder window-list queries stall after an IME transition.
        // Inspect only the active app window, like the shell dump command.
        try { automation.waitForIdle(100,1000); } catch (java.util.concurrent.TimeoutException animation) { /* assert UI state below */ }
        if(android.os.Build.VERSION.SDK_INT>=34)automation.clearCache();
        AccessibilityNodeInfo root=automation.getRootInActiveWindow();
        List<Node> result=new ArrayList<>();
        if(root!=null)copy(root,null,result);
        return result;
    }

    private void copy(AccessibilityNodeInfo source,Node parent,List<Node> result) {
        try {
            if(!source.isVisibleToUser()||!APP.contentEquals(source.getPackageName()==null?"":source.getPackageName()))return;
            assertTrue("Unexpectedly large UI hierarchy",result.size()<10000);
            Node node=new Node();node.parent=parent;
            node.text=String.valueOf(source.getText()==null?"":source.getText());
            node.description=String.valueOf(source.getContentDescription()==null?"":source.getContentDescription());
            node.className=String.valueOf(source.getClassName());
            source.getBoundsInScreen(node.bounds);
            node.enabled=source.isEnabled();node.checkable=source.isCheckable();node.checked=source.isChecked();node.scrollable=source.isScrollable();
            node.focused=source.isFocused();
            result.add(node);
            for(int i=0;i<source.getChildCount();i++) {
                AccessibilityNodeInfo child=source.getChild(i);
                if(child!=null)copy(child,node,result);
            }
        } finally {source.recycle();}
    }

    private Node find(Predicate<Node> predicate,String label,boolean scroll) throws Exception {
        return find(predicate,label,scroll,true);
    }

    private Node find(Predicate<Node> predicate,String label,boolean scroll,boolean unique) throws Exception {
        trace("find: "+label);
        for(int attempt=0;attempt<(scroll?40:8);attempt++) {
            List<Node> nodes=tree();
            List<Node> matches=new ArrayList<>();
            for(Node node:nodes)if(predicate.test(node)&&!node.bounds.isEmpty())matches.add(node);
            assertTrue("Ambiguous control: "+label,!unique||matches.size()<=1);
            if(!matches.isEmpty())return matches.get(0);
            if(scroll) {
                Node container=nodes.stream().filter(n->n.scrollable&&!n.bounds.isEmpty()).findFirst().orElse(null);
                // A click may precede the next Compose frame or window change.
                // Keep polling fresh snapshots until the bounded deadline.
                if(container==null){Thread.sleep(100);continue;}
                Rect b=container.bounds;
                shell("input swipe "+b.centerX()+" "+(b.top+b.height()*4/5)+" "+b.centerX()+" "+(b.top+b.height()/5)+" 500");
            } else Thread.sleep(100);
        }
        throw new AssertionError("Missing control: "+label);
    }

    private Node text(String pattern,boolean scroll) throws Exception {
        return find(n->n.text.matches(pattern),pattern,scroll);
    }

    private void tap(Node node) throws Exception {
        assertTrue("Enabled visible control required",node.enabled&&!node.bounds.isEmpty());
        shell("input tap "+node.bounds.centerX()+" "+node.bounds.centerY());
    }

    private void click(String pattern) throws Exception {tap(text(pattern,false));}
    private void description(String pattern) throws Exception {tap(find(n->n.description.matches(pattern),pattern,false));}

    private void restart() throws Exception {
        trace("restart actual Release process");
        shell("input keyevent KEYCODE_HOME");
        // Wait for the lifecycle transition (including queued preference writes)
        // before killing the process, as a user leaving the screen would.
        automation.waitForIdle(200,5000);
        shell("am force-stop "+APP);
        shell("am start -W -n "+APP+"/.MainActivity");
        text("Atelier|Studio",false);
    }

    @Before public void launchActualRelease() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var info=instrumentation.getContext().getPackageManager().getApplicationInfo(APP,0);
        assertEquals("Expected actual non-debuggable Release",0,info.flags&ApplicationInfo.FLAG_DEBUGGABLE);
        automation=instrumentation.getUiAutomation();
        restart();
    }

    @Test public void homeRendersFromOptimizedApk() throws Exception {
        text("Outils|Tools",true);text("Modèles|Models",false);
    }

    @Test public void modelImportExportAndQualityRemainAccessible() throws Exception {
        click("Modèles|Models");click("Importer|Import");
        text("Choisir un \\.tflite|Choose a \\.tflite file",false);
        click("Export");text("Archive locale|Local archive",true);
        click("Qualité|Quality");text("Stockage|Storage",false);
    }

    @Test public void createdProjectSurvivesProcessRestart() throws Exception {
        String name="QA_Release_instrumented_"+System.currentTimeMillis();
        description("Gérer les projets|Manage projects");
        text("Nom du nouveau projet|New project name",true);
        Node field=find(n->n.className.equals("android.widget.EditText"),"Project name input",false);
        assertEquals("New project input must be empty","",field.text);
        // Scrolling can still move the field after it first becomes visible.
        // A tap at stale coordinates then sends the keys to no editor on API 36.
        Rect previous=null;
        for(int attempt=0;attempt<20&&!field.focused;attempt++) {
            if(previous!=null&&previous.equals(field.bounds))tap(field);
            previous=new Rect(field.bounds);
            Thread.sleep(100);
            field=find(n->n.className.equals("android.widget.EditText"),"Settled project name input",false);
        }
        assertTrue("Project name input must receive focus before typing",field.focused);
        shell("input text "+name);
        find(n->n.className.equals("android.widget.EditText")&&n.text.equals(name),"Entered project name",false);
        if(shell("dumpsys input_method").contains("mInputShown=true"))shell("input keyevent KEYCODE_BACK");
        // IME dismissal also moves the viewport. Wait for the window change,
        // then use fresh, stable button coordinates rather than the first frame.
        automation.waitForIdle(300,5000);
        Node create=text("Créer un projet|Create project",true);
        boolean settled=false;
        for(int attempt=0;attempt<20;attempt++) {
            Rect bounds=new Rect(create.bounds);
            Thread.sleep(100);
            create=text("Créer un projet|Create project",true);
            if(bounds.equals(create.bounds)){settled=true;break;}
        }
        assertTrue("Create project button must settle before tapping",settled);
        tap(create);
        find(n->!n.className.equals("android.widget.EditText")&&n.text.equals(name),"Created project outside input",false,false);
        restart();text(Pattern.quote(name),false);
    }

    private Node guidance() throws Exception {
        description("Réglages|Settings");
        return currentGuidance();
    }

    private Node currentGuidance() throws Exception {
        Node node=text("Afficher les conseils|Show guidance",true);
        while(node!=null&&!node.checkable)node=node.parent;
        assertNotNull("Guidance toggle",node);return node;
    }

    private void awaitGuidance(boolean checked) throws Exception {
        for(int i=0;i<8;i++) {
            if(currentGuidance().checked==checked)return;
            Thread.sleep(100);
        }
        throw new AssertionError("Guidance did not reach "+checked);
    }

    @Test public void displayPreferencePersistsAndIsRestored() throws Exception {
        Node toggle=guidance();boolean before=toggle.checked;
        try {
            tap(toggle);awaitGuidance(!before);restart();
            toggle=guidance();assertEquals("Persisted preference",!before,toggle.checked);
        } finally {
            restart();toggle=guidance();if(toggle.checked!=before){tap(toggle);awaitGuidance(before);}
            restart();assertEquals("Original preference restored",before,guidance().checked);
        }
    }
}
