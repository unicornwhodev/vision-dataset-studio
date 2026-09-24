# Exact APIs referenced by the separately compiled Android instrumentation.
# Generated with R8 TraceReferences --keep-rules (stable separately compiled API names).
# No wildcard keep and no global optimization switch. See docs/RELEASE_TESTING.md.
-keep,allowaccessmodification class androidx.activity.ComponentActivity {
  public void setContentView(android.view.View);
}
-keep,allowaccessmodification class androidx.activity.compose.ComponentActivityKt {
  public static void setContent(androidx.activity.ComponentActivity,androidx.compose.runtime.CompositionContext,kotlin.jvm.functions.Function2);
}
-keep,allowaccessmodification class androidx.activity.compose.LocalActivityResultRegistryOwner {
  public androidx.activity.result.ActivityResultRegistryOwner getCurrent(androidx.compose.runtime.Composer,int);
  public androidx.compose.runtime.ProvidedValue provides(androidx.activity.result.ActivityResultRegistryOwner);
  int $stable;
  androidx.activity.compose.LocalActivityResultRegistryOwner INSTANCE;
}
-keep,allowaccessmodification interface androidx.activity.result.ActivityResultRegistryOwner {
}
-keep @interface androidx.annotation.CheckResult {
}
-keep @interface androidx.annotation.ChecksSdkIntAtLeast {
  public int api();
  public int extension();
}
-keep @interface androidx.annotation.DoNotInline {
}
-keep @interface androidx.annotation.GuardedBy {
  public java.lang.String value();
}
-keep @interface androidx.annotation.NonNull {
}
-keep @interface androidx.annotation.Nullable {
}
-keep @interface androidx.annotation.RequiresApi {
  public int value();
}
-keep @interface androidx.annotation.RestrictTo {
  public androidx.annotation.RestrictTo$Scope[] value();
}
-keep enum androidx.annotation.RestrictTo$Scope {
  androidx.annotation.RestrictTo$Scope LIBRARY;
  androidx.annotation.RestrictTo$Scope LIBRARY_GROUP;
}
-keep @interface androidx.annotation.VisibleForTesting {
}
-keep,allowaccessmodification class androidx.arch.core.executor.ArchTaskExecutor {
  public static java.util.concurrent.Executor getIOThreadExecutor();
}
-keep,allowaccessmodification class androidx.compose.foundation.layout.BoxKt {
  public static androidx.compose.ui.layout.MeasurePolicy maybeCachedBoxMeasurePolicy(androidx.compose.ui.Alignment,boolean);
}
-keep,allowaccessmodification interface androidx.compose.foundation.layout.BoxScope {
}
-keep,allowaccessmodification class androidx.compose.foundation.layout.BoxScopeInstance {
  androidx.compose.foundation.layout.BoxScopeInstance INSTANCE;
}
-keep,allowaccessmodification class androidx.compose.foundation.layout.SizeKt {
  public static androidx.compose.ui.Modifier size-3ABfNKs(androidx.compose.ui.Modifier,float);
}
-keep,allowaccessmodification class androidx.compose.material3.ColorScheme {
}
-keep,allowaccessmodification class androidx.compose.material3.MaterialThemeKt {
  public static void MaterialTheme(androidx.compose.material3.ColorScheme,androidx.compose.material3.Shapes,androidx.compose.material3.Typography,kotlin.jvm.functions.Function2,androidx.compose.runtime.Composer,int,int);
}
-keep,allowaccessmodification class androidx.compose.material3.Shapes {
}
-keep,allowaccessmodification class androidx.compose.material3.TextKt {
  public static void Text--4IGK_g(java.lang.String,androidx.compose.ui.Modifier,long,long,androidx.compose.ui.text.font.FontStyle,androidx.compose.ui.text.font.FontWeight,androidx.compose.ui.text.font.FontFamily,long,androidx.compose.ui.text.style.TextDecoration,androidx.compose.ui.text.style.TextAlign,long,int,boolean,int,int,kotlin.jvm.functions.Function1,androidx.compose.ui.text.TextStyle,androidx.compose.runtime.Composer,int,int,int);
}
-keep,allowaccessmodification class androidx.compose.material3.Typography {
}
-keep,allowaccessmodification interface androidx.compose.runtime.Applier {
}
-keep @interface androidx.compose.runtime.Composable {
}
-keep @interface androidx.compose.runtime.ComposableInferredTarget {
  public java.lang.String scheme();
}
-keep @interface androidx.compose.runtime.ComposableTarget {
  public java.lang.String applier();
}
-keep,allowaccessmodification class androidx.compose.runtime.ComposablesKt {
  public static int getCurrentCompositeKeyHash(androidx.compose.runtime.Composer,int);
  public static void invalidApplier();
}
-keep,allowaccessmodification interface androidx.compose.runtime.Composer {
  public void apply(java.lang.Object,kotlin.jvm.functions.Function2);
  public boolean changed(long);
  public boolean changed(java.lang.Object);
  public boolean changedInstance(java.lang.Object);
  public java.lang.Object consume(androidx.compose.runtime.CompositionLocal);
  public void createNode(kotlin.jvm.functions.Function0);
  public void endNode();
  public void endReplaceGroup();
  public androidx.compose.runtime.ScopeUpdateScope endRestartGroup();
  public androidx.compose.runtime.Applier getApplier();
  public androidx.compose.runtime.CompositionLocalMap getCurrentCompositionLocalMap();
  public boolean getInserting();
  public boolean getSkipping();
  public java.lang.Object rememberedValue();
  public void skipToGroupEnd();
  public void startReplaceGroup(int);
  public androidx.compose.runtime.Composer startRestartGroup(int);
  public void startReusableNode();
  public void updateRememberedValue(java.lang.Object);
  public void useNode();
  androidx.compose.runtime.Composer$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.runtime.Composer$Companion {
  public java.lang.Object getEmpty();
}
-keep,allowaccessmodification class androidx.compose.runtime.ComposerKt {
  public static boolean isTraceInProgress();
  public static void sourceInformation(androidx.compose.runtime.Composer,java.lang.String);
  public static void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
  public static void sourceInformationMarkerStart(androidx.compose.runtime.Composer,int,java.lang.String);
  public static void traceEventEnd();
  public static void traceEventStart(int,int,int,java.lang.String);
}
-keep,allowaccessmodification class androidx.compose.runtime.CompositionContext {
}
-keep,allowaccessmodification class androidx.compose.runtime.CompositionLocal {
}
-keep,allowaccessmodification class androidx.compose.runtime.CompositionLocalKt {
  public static void CompositionLocalProvider(androidx.compose.runtime.ProvidedValue,kotlin.jvm.functions.Function2,androidx.compose.runtime.Composer,int);
  public static void CompositionLocalProvider(androidx.compose.runtime.ProvidedValue[],kotlin.jvm.functions.Function2,androidx.compose.runtime.Composer,int);
}
-keep,allowaccessmodification interface androidx.compose.runtime.CompositionLocalMap {
}
-keep,allowaccessmodification interface androidx.compose.runtime.MonotonicFrameClock {
  public java.lang.Object withFrameNanos(kotlin.jvm.functions.Function1,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class androidx.compose.runtime.MonotonicFrameClock$DefaultImpls {
  public static java.lang.Object fold(androidx.compose.runtime.MonotonicFrameClock,java.lang.Object,kotlin.jvm.functions.Function2);
  public static kotlin.coroutines.CoroutineContext$Element get(androidx.compose.runtime.MonotonicFrameClock,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext minusKey(androidx.compose.runtime.MonotonicFrameClock,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext plus(androidx.compose.runtime.MonotonicFrameClock,kotlin.coroutines.CoroutineContext);
}
-keep,allowaccessmodification interface androidx.compose.runtime.MutableState {
  public void setValue(java.lang.Object);
}
-keep,allowaccessmodification class androidx.compose.runtime.ProvidableCompositionLocal {
  public androidx.compose.runtime.ProvidedValue provides(java.lang.Object);
}
-keep,allowaccessmodification class androidx.compose.runtime.ProvidedValue {
  int $stable;
}
-keep,allowaccessmodification class androidx.compose.runtime.RecomposeScopeImplKt {
  public static int updateChangedFlags(int);
}
-keep,allowaccessmodification class androidx.compose.runtime.Recomposer {
  public <init>(kotlin.coroutines.CoroutineContext);
  public void cancel();
  public boolean getHasPendingWork();
  public java.lang.Object runRecomposeAndApplyChanges(kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface androidx.compose.runtime.ScopeUpdateScope {
  public void updateScope(kotlin.jvm.functions.Function2);
}
-keep,allowaccessmodification interface androidx.compose.runtime.SnapshotMutationPolicy {
}
-keep,allowaccessmodification class androidx.compose.runtime.SnapshotStateKt {
  public static androidx.compose.runtime.MutableState mutableStateOf$default(java.lang.Object,androidx.compose.runtime.SnapshotMutationPolicy,int,java.lang.Object);
}
-keep @interface androidx.compose.runtime.Stable {
}
-keep,allowaccessmodification interface androidx.compose.runtime.State {
  public java.lang.Object getValue();
}
-keep,allowaccessmodification class androidx.compose.runtime.Updater {
  public static androidx.compose.runtime.Composer constructor-impl(androidx.compose.runtime.Composer);
  public static void set-impl(androidx.compose.runtime.Composer,java.lang.Object,kotlin.jvm.functions.Function2);
}
-keep,allowaccessmodification interface androidx.compose.runtime.internal.ComposableLambda {
}
-keep,allowaccessmodification class androidx.compose.runtime.internal.ComposableLambdaKt {
  public static androidx.compose.runtime.internal.ComposableLambda composableLambdaInstance(int,boolean,java.lang.Object);
  public static androidx.compose.runtime.internal.ComposableLambda rememberComposableLambda(int,boolean,java.lang.Object,androidx.compose.runtime.Composer,int);
}
-keep @interface androidx.compose.runtime.internal.StabilityInferred {
  public int parameters();
}
-keep,allowaccessmodification interface androidx.compose.runtime.saveable.SaveableStateRegistry {
  public boolean canBeSaved(java.lang.Object);
  public java.lang.Object consumeRestored(java.lang.String);
  public java.util.Map performSave();
  public androidx.compose.runtime.saveable.SaveableStateRegistry$Entry registerProvider(java.lang.String,kotlin.jvm.functions.Function0);
}
-keep,allowaccessmodification interface androidx.compose.runtime.saveable.SaveableStateRegistry$Entry {
}
-keep,allowaccessmodification class androidx.compose.runtime.saveable.SaveableStateRegistryKt {
  public static androidx.compose.runtime.saveable.SaveableStateRegistry SaveableStateRegistry(java.util.Map,kotlin.jvm.functions.Function1);
  public static androidx.compose.runtime.ProvidableCompositionLocal getLocalSaveableStateRegistry();
}
-keep,allowaccessmodification class androidx.compose.runtime.snapshots.Snapshot {
  public boolean hasPendingChanges();
  androidx.compose.runtime.snapshots.Snapshot$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.runtime.snapshots.Snapshot$Companion {
  public androidx.compose.runtime.snapshots.Snapshot getCurrent();
  public void sendApplyNotifications();
}
-keep,allowaccessmodification interface androidx.compose.ui.Alignment {
  androidx.compose.ui.Alignment$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.Alignment$Companion {
  public androidx.compose.ui.Alignment getTopStart();
}
-keep,allowaccessmodification class androidx.compose.ui.ComposedModifierKt {
  public static androidx.compose.ui.Modifier materializeModifier(androidx.compose.runtime.Composer,androidx.compose.ui.Modifier);
}
-keep,allowaccessmodification interface androidx.compose.ui.Modifier {
  androidx.compose.ui.Modifier$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.Modifier$Companion {
  public androidx.compose.ui.Modifier then(androidx.compose.ui.Modifier);
}
-keep,allowaccessmodification class androidx.compose.ui.geometry.Offset {
  public static androidx.compose.ui.geometry.Offset box-impl(long);
  public static boolean equals-impl0(long,long);
  public static float getDistance-impl(long);
  public static float getX-impl(long);
  public static float getY-impl(long);
  public static boolean isValid-impl(long);
  public static long minus-MK-Hz9U(long,long);
  public static long plus-MK-Hz9U(long,long);
  public static java.lang.String toString-impl(long);
  public long unbox-impl();
  androidx.compose.ui.geometry.Offset$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.geometry.Offset$Companion {
  public long getUnspecified-F1C5BW0();
  public long getZero-F1C5BW0();
}
-keep,allowaccessmodification class androidx.compose.ui.geometry.OffsetKt {
  public static long Offset(float,float);
  public static boolean isSpecified-k-4lQ0M(long);
  public static long lerp-Wko1d7g(long,long,float);
}
-keep,allowaccessmodification class androidx.compose.ui.geometry.Rect {
  public <init>(float,float,float,float);
  public boolean contains-k-4lQ0M(long);
  public float getBottom();
  public float getHeight();
  public float getLeft();
  public float getRight();
  public long getSize-NH-jbRc();
  public float getTop();
  public long getTopLeft-F1C5BW0();
  public float getWidth();
  public androidx.compose.ui.geometry.Rect intersect(androidx.compose.ui.geometry.Rect);
  public boolean isEmpty();
  public androidx.compose.ui.geometry.Rect translate-k-4lQ0M(long);
}
-keep,allowaccessmodification class androidx.compose.ui.geometry.RectKt {
  public static androidx.compose.ui.geometry.Rect Rect-tz77jQw(long,long);
}
-keep,allowaccessmodification class androidx.compose.ui.geometry.Size {
  public static float getHeight-impl(long);
  public static float getWidth-impl(long);
}
-keep,allowaccessmodification class androidx.compose.ui.graphics.AndroidImageBitmap_androidKt {
  public static androidx.compose.ui.graphics.ImageBitmap asImageBitmap(android.graphics.Bitmap);
}
-keep,allowaccessmodification interface androidx.compose.ui.graphics.ImageBitmap {
}
-keep,allowaccessmodification class androidx.compose.ui.input.key.Key {
  public static androidx.compose.ui.input.key.Key box-impl(long);
  public static boolean equals-impl(long,java.lang.Object);
  public static boolean equals-impl0(long,long);
  public static java.lang.String toString-impl(long);
  public long unbox-impl();
  androidx.compose.ui.input.key.Key$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.input.key.Key$Companion {
  public long getAltLeft-EK5gGoQ();
  public long getAltRight-EK5gGoQ();
  public long getCapsLock-EK5gGoQ();
  public long getCtrlLeft-EK5gGoQ();
  public long getCtrlRight-EK5gGoQ();
  public long getFunction-EK5gGoQ();
  public long getMetaLeft-EK5gGoQ();
  public long getMetaRight-EK5gGoQ();
  public long getNumLock-EK5gGoQ();
  public long getScrollLock-EK5gGoQ();
  public long getShiftLeft-EK5gGoQ();
  public long getShiftRight-EK5gGoQ();
}
-keep,allowaccessmodification class androidx.compose.ui.input.key.KeyEvent_androidKt {
  public static long getKey-ZmokQxo(android.view.KeyEvent);
}
-keep,allowaccessmodification class androidx.compose.ui.input.key.Key_androidKt {
  public static int getNativeKeyCode-YVgTNJs(long);
}
-keep,allowaccessmodification class androidx.compose.ui.input.pointer.util.VelocityTracker {
  public <init>();
  public void addPosition-Uv8p0NA(long,long);
  public long calculateVelocity-9UxMQ8M();
}
-keep,allowaccessmodification class androidx.compose.ui.input.pointer.util.VelocityTrackerKt {
  public static boolean getVelocityTrackerStrategyUseImpulse();
}
-keep,allowaccessmodification class androidx.compose.ui.layout.AlignmentLine {
}
-keep,allowaccessmodification interface androidx.compose.ui.layout.LayoutCoordinates {
  public androidx.compose.ui.layout.LayoutCoordinates getParentLayoutCoordinates();
}
-keep,allowaccessmodification class androidx.compose.ui.layout.LayoutCoordinatesKt {
  public static androidx.compose.ui.geometry.Rect boundsInParent(androidx.compose.ui.layout.LayoutCoordinates);
  public static long positionInRoot(androidx.compose.ui.layout.LayoutCoordinates);
}
-keep,allowaccessmodification interface androidx.compose.ui.layout.LayoutInfo {
  public androidx.compose.ui.layout.LayoutCoordinates getCoordinates();
  public androidx.compose.ui.unit.Density getDensity();
  public androidx.compose.ui.unit.LayoutDirection getLayoutDirection();
  public androidx.compose.ui.layout.LayoutInfo getParentInfo();
  public androidx.compose.ui.platform.ViewConfiguration getViewConfiguration();
  public boolean isDeactivated();
  public boolean isPlaced();
}
-keep,allowaccessmodification class androidx.compose.ui.layout.LayoutModifierKt {
  public static androidx.compose.ui.Modifier layout(androidx.compose.ui.Modifier,kotlin.jvm.functions.Function3);
}
-keep,allowaccessmodification interface androidx.compose.ui.layout.Measurable {
  public androidx.compose.ui.layout.Placeable measure-BRTryo0(long);
}
-keep,allowaccessmodification interface androidx.compose.ui.layout.MeasurePolicy {
  public androidx.compose.ui.layout.MeasureResult measure-3p2s80s(androidx.compose.ui.layout.MeasureScope,java.util.List,long);
}
-keep,allowaccessmodification interface androidx.compose.ui.layout.MeasureResult {
}
-keep,allowaccessmodification interface androidx.compose.ui.layout.MeasureScope {
  public static androidx.compose.ui.layout.MeasureResult layout$default(androidx.compose.ui.layout.MeasureScope,int,int,java.util.Map,kotlin.jvm.functions.Function1,int,java.lang.Object);
}
-keep,allowaccessmodification class androidx.compose.ui.layout.Placeable {
  public int getHeight();
  public int getWidth();
}
-keep,allowaccessmodification class androidx.compose.ui.layout.Placeable$PlacementScope {
  public static void placeRelative$default(androidx.compose.ui.layout.Placeable$PlacementScope,androidx.compose.ui.layout.Placeable,int,int,float,int,java.lang.Object);
}
-keep,allowaccessmodification class androidx.compose.ui.layout.SubcomposeLayoutKt {
  public static void SubcomposeLayout(androidx.compose.ui.Modifier,kotlin.jvm.functions.Function2,androidx.compose.runtime.Composer,int,int);
}
-keep,allowaccessmodification interface androidx.compose.ui.layout.SubcomposeMeasureScope {
  public java.util.List subcompose(java.lang.Object,kotlin.jvm.functions.Function2);
}
-keep,allowaccessmodification interface androidx.compose.ui.node.ComposeUiNode {
  androidx.compose.ui.node.ComposeUiNode$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.node.ComposeUiNode$Companion {
  public kotlin.jvm.functions.Function0 getConstructor();
  public kotlin.jvm.functions.Function2 getSetCompositeKeyHash();
  public kotlin.jvm.functions.Function2 getSetMeasurePolicy();
  public kotlin.jvm.functions.Function2 getSetModifier();
  public kotlin.jvm.functions.Function2 getSetResolvedCompositionLocals();
}
-keep,allowaccessmodification interface androidx.compose.ui.node.RootForTest {
  public androidx.compose.ui.semantics.SemanticsOwner getSemanticsOwner();
  public void measureAndLayoutForTest();
  public boolean sendKeyEvent-ZmokQxo(android.view.KeyEvent);
}
-keep,allowaccessmodification class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
  public static androidx.compose.runtime.ProvidableCompositionLocal getLocalConfiguration();
  public static androidx.compose.runtime.ProvidableCompositionLocal getLocalContext();
}
-keep,allowaccessmodification class androidx.compose.ui.platform.CompositionLocalsKt {
  public static androidx.compose.runtime.ProvidableCompositionLocal getLocalDensity();
  public static androidx.compose.runtime.ProvidableCompositionLocal getLocalFontFamilyResolver();
  public static androidx.compose.runtime.ProvidableCompositionLocal getLocalLayoutDirection();
}
-keep,allowaccessmodification interface androidx.compose.ui.platform.InfiniteAnimationPolicy {
  public java.lang.Object onInfiniteOperation(kotlin.jvm.functions.Function1,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class androidx.compose.ui.platform.InfiniteAnimationPolicy$DefaultImpls {
  public static java.lang.Object fold(androidx.compose.ui.platform.InfiniteAnimationPolicy,java.lang.Object,kotlin.jvm.functions.Function2);
  public static kotlin.coroutines.CoroutineContext$Element get(androidx.compose.ui.platform.InfiniteAnimationPolicy,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext minusKey(androidx.compose.ui.platform.InfiniteAnimationPolicy,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext plus(androidx.compose.ui.platform.InfiniteAnimationPolicy,kotlin.coroutines.CoroutineContext);
}
-keep,allowaccessmodification interface androidx.compose.ui.platform.PlatformTextInputInterceptor {
  public java.lang.Object interceptStartInputMethod(androidx.compose.ui.platform.PlatformTextInputMethodRequest,androidx.compose.ui.platform.PlatformTextInputSession,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface androidx.compose.ui.platform.PlatformTextInputMethodRequest {
}
-keep,allowaccessmodification class androidx.compose.ui.platform.PlatformTextInputModifierNodeKt {
  public static void InterceptPlatformTextInput(androidx.compose.ui.platform.PlatformTextInputInterceptor,kotlin.jvm.functions.Function2,androidx.compose.runtime.Composer,int);
}
-keep,allowaccessmodification interface androidx.compose.ui.platform.PlatformTextInputSession {
  public java.lang.Object startInputMethod(androidx.compose.ui.platform.PlatformTextInputMethodRequest,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface androidx.compose.ui.platform.ViewConfiguration {
  public long getDoubleTapMinTimeMillis();
  public long getDoubleTapTimeoutMillis();
  public long getLongPressTimeoutMillis();
}
-keep,allowaccessmodification interface androidx.compose.ui.platform.ViewRootForTest {
  public boolean getHasPendingMeasureOrLayout();
  public android.view.View getView();
  androidx.compose.ui.platform.ViewRootForTest$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.platform.ViewRootForTest$Companion {
  public kotlin.jvm.functions.Function1 getOnViewCreatedCallback();
  public void setOnViewCreatedCallback(kotlin.jvm.functions.Function1);
}
-keep,allowaccessmodification interface androidx.compose.ui.platform.WindowRecomposerFactory {
  public androidx.compose.runtime.Recomposer createRecomposer(android.view.View);
}
-keep,allowaccessmodification class androidx.compose.ui.platform.WindowRecomposerPolicy {
  public boolean compareAndSetFactory(androidx.compose.ui.platform.WindowRecomposerFactory,androidx.compose.ui.platform.WindowRecomposerFactory);
  public androidx.compose.ui.platform.WindowRecomposerFactory getAndSetFactory(androidx.compose.ui.platform.WindowRecomposerFactory);
  androidx.compose.ui.platform.WindowRecomposerPolicy INSTANCE;
}
-keep,allowaccessmodification class androidx.compose.ui.res.StringResources_androidKt {
  public static java.lang.String stringResource(int,androidx.compose.runtime.Composer,int);
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.AccessibilityAction {
  public kotlin.Function getAction();
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.CustomAccessibilityAction {
  public kotlin.jvm.functions.Function0 getAction();
  public java.lang.String getLabel();
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.ProgressBarRangeInfo {
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.ScrollAxisRange {
  public kotlin.jvm.functions.Function0 getMaxValue();
  public boolean getReverseScrolling();
  public kotlin.jvm.functions.Function0 getValue();
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.SemanticsActions {
  public androidx.compose.ui.semantics.SemanticsPropertyKey getCustomActions();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getInsertTextAtCursor();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getOnClick();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getOnImeAction();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getRequestFocus();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getScrollBy();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getScrollToIndex();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getSetSelection();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getSetText();
  androidx.compose.ui.semantics.SemanticsActions INSTANCE;
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.SemanticsConfiguration {
  public boolean contains(androidx.compose.ui.semantics.SemanticsPropertyKey);
  public java.lang.Object get(androidx.compose.ui.semantics.SemanticsPropertyKey);
  public java.lang.Object getOrElseNullable(androidx.compose.ui.semantics.SemanticsPropertyKey,kotlin.jvm.functions.Function0);
  public boolean isClearingSemantics();
  public boolean isMergingSemanticsOfDescendants();
  public java.util.Iterator iterator();
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.SemanticsConfigurationKt {
  public static java.lang.Object getOrNull(androidx.compose.ui.semantics.SemanticsConfiguration,androidx.compose.ui.semantics.SemanticsPropertyKey);
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.SemanticsNode {
  public int getAlignmentLinePosition(androidx.compose.ui.layout.AlignmentLine);
  public androidx.compose.ui.geometry.Rect getBoundsInRoot();
  public androidx.compose.ui.geometry.Rect getBoundsInWindow();
  public java.util.List getChildren();
  public androidx.compose.ui.semantics.SemanticsConfiguration getConfig();
  public int getId();
  public androidx.compose.ui.layout.LayoutInfo getLayoutInfo();
  public androidx.compose.ui.semantics.SemanticsNode getParent();
  public long getPositionInRoot-F1C5BW0();
  public long getPositionInWindow-F1C5BW0();
  public androidx.compose.ui.node.RootForTest getRoot();
  public long getSize-YbymL2g();
  public androidx.compose.ui.geometry.Rect getTouchBoundsInRoot();
  public boolean isRoot();
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.SemanticsOwner {
  public androidx.compose.ui.semantics.SemanticsNode getRootSemanticsNode();
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.SemanticsOwnerKt {
  public static java.util.List getAllSemanticsNodes(androidx.compose.ui.semantics.SemanticsOwner,boolean,boolean);
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.SemanticsProperties {
  public androidx.compose.ui.semantics.SemanticsPropertyKey getContentDescription();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getDisabled();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getEditableText();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getFocused();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getHeading();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getHorizontalScrollAxisRange();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getImeAction();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getIndexForKey();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getIsDialog();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getIsEditable();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getIsPopup();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getProgressBarRangeInfo();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getSelected();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getStateDescription();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getTestTag();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getText();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getToggleableState();
  public androidx.compose.ui.semantics.SemanticsPropertyKey getVerticalScrollAxisRange();
  androidx.compose.ui.semantics.SemanticsProperties INSTANCE;
}
-keep,allowaccessmodification class androidx.compose.ui.semantics.SemanticsPropertyKey {
  public java.lang.String getName();
}
-keep enum androidx.compose.ui.state.ToggleableState {
  androidx.compose.ui.state.ToggleableState Off;
  androidx.compose.ui.state.ToggleableState On;
}
-keep,allowaccessmodification class androidx.compose.ui.text.AnnotatedString {
  public <init>(java.lang.String,java.util.List,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public java.util.List getParagraphStyles();
  public java.util.List getSpanStyles();
  public java.util.List getStringAnnotations(int,int);
  public java.lang.String getText();
}
-keep,allowaccessmodification class androidx.compose.ui.text.TextRange {
  public static int getMax-impl(long);
  public static int getMin-impl(long);
}
-keep,allowaccessmodification class androidx.compose.ui.text.TextStyle {
}
-keep,allowaccessmodification class androidx.compose.ui.text.font.FontFamily {
}
-keep,allowaccessmodification interface androidx.compose.ui.text.font.FontFamily$Resolver {
}
-keep,allowaccessmodification class androidx.compose.ui.text.font.FontFamilyResolver_androidKt {
  public static androidx.compose.ui.text.font.FontFamily$Resolver createFontFamilyResolver(android.content.Context);
}
-keep,allowaccessmodification class androidx.compose.ui.text.font.FontStyle {
}
-keep,allowaccessmodification class androidx.compose.ui.text.font.FontWeight {
}
-keep,allowaccessmodification class androidx.compose.ui.text.input.ImeAction {
  public static androidx.compose.ui.text.input.ImeAction box-impl(int);
  androidx.compose.ui.text.input.ImeAction$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.text.input.ImeAction$Companion {
  public int getDefault-eUduSuo();
}
-keep,allowaccessmodification class androidx.compose.ui.text.intl.Locale {
  public java.lang.String toLanguageTag();
}
-keep,allowaccessmodification class androidx.compose.ui.text.intl.LocaleList {
  public java.util.List getLocaleList();
}
-keep,allowaccessmodification class androidx.compose.ui.text.style.TextAlign {
}
-keep,allowaccessmodification class androidx.compose.ui.text.style.TextDecoration {
}
-keep,allowaccessmodification class androidx.compose.ui.unit.AndroidDensity_androidKt {
  public static androidx.compose.ui.unit.Density Density(android.content.Context);
}
-keep,allowaccessmodification class androidx.compose.ui.unit.Constraints {
  public static int getMaxHeight-impl(long);
  public static int getMaxWidth-impl(long);
  public long unbox-impl();
  androidx.compose.ui.unit.Constraints$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.unit.Constraints$Companion {
  public long fixed-JhjzzOo(int,int);
}
-keep,allowaccessmodification class androidx.compose.ui.unit.ConstraintsKt {
  public static long constrain-N9IONVI(long,long);
}
-keep,allowaccessmodification interface androidx.compose.ui.unit.Density {
  public float getDensity();
  public int roundToPx--R2X_6o(long);
  public int roundToPx-0680j_4(float);
  public float toDp-u2uoSUM(float);
  public float toDp-u2uoSUM(int);
  public long toDpSize-k-rfVVM(long);
  public float toPx--R2X_6o(long);
  public float toPx-0680j_4(float);
  public androidx.compose.ui.geometry.Rect toRect(androidx.compose.ui.unit.DpRect);
  public long toSize-XkaWNTQ(long);
  public long toSp-kPz2Gy4(float);
  public long toSp-kPz2Gy4(int);
}
-keep,allowaccessmodification class androidx.compose.ui.unit.DensityKt {
  public static androidx.compose.ui.unit.Density Density(float,float);
}
-keep,allowaccessmodification class androidx.compose.ui.unit.Dp {
  public static androidx.compose.ui.unit.Dp box-impl(float);
  public static int compareTo-0680j_4(float,float);
  public static float constructor-impl(float);
  public static java.lang.String toString-impl(float);
  public float unbox-impl();
  androidx.compose.ui.unit.Dp$Companion Companion;
}
-keep,allowaccessmodification class androidx.compose.ui.unit.Dp$Companion {
  public float getUnspecified-D9Ej5fM();
}
-keep,allowaccessmodification class androidx.compose.ui.unit.DpRect {
  public <init>(float,float,float,float,kotlin.jvm.internal.DefaultConstructorMarker);
  public float getBottom-D9Ej5fM();
  public float getLeft-D9Ej5fM();
  public float getRight-D9Ej5fM();
  public float getTop-D9Ej5fM();
}
-keep,allowaccessmodification class androidx.compose.ui.unit.DpSize {
  public static float getHeight-D9Ej5fM(long);
  public static float getWidth-D9Ej5fM(long);
}
-keep,allowaccessmodification interface androidx.compose.ui.unit.FontScaling {
  public float getFontScale();
  public float toDp-GaN1DYA(long);
  public long toSp-0xMU5do(float);
}
-keep,allowaccessmodification class androidx.compose.ui.unit.IntSize {
  public static androidx.compose.ui.unit.IntSize box-impl(long);
  public static int getHeight-impl(long);
  public static int getWidth-impl(long);
  public long unbox-impl();
}
-keep,allowaccessmodification class androidx.compose.ui.unit.IntSizeKt {
  public static long IntSize(int,int);
  public static long toSize-ozmzZPI(long);
}
-keep enum androidx.compose.ui.unit.LayoutDirection {
  public static androidx.compose.ui.unit.LayoutDirection[] values();
  androidx.compose.ui.unit.LayoutDirection Ltr;
  androidx.compose.ui.unit.LayoutDirection Rtl;
}
-keep,allowaccessmodification class androidx.compose.ui.unit.Velocity {
  public static float getX-impl(long);
}
-keep,allowaccessmodification class androidx.compose.ui.util.ListUtilsKt {
  public static java.lang.String fastJoinToString$default(java.util.List,java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1,int,java.lang.Object);
}
-keep,allowaccessmodification class androidx.compose.ui.util.MathHelpersKt {
  public static float lerp(float,float,float);
  public static long lerp(long,long,float);
}
-keep,allowaccessmodification interface androidx.compose.ui.window.DialogWindowProvider {
  public android.view.Window getWindow();
}
-keep,allowaccessmodification class androidx.concurrent.futures.CallbackToFutureAdapter {
  public static com.google.common.util.concurrent.ListenableFuture getFuture(androidx.concurrent.futures.CallbackToFutureAdapter$Resolver);
}
-keep,allowaccessmodification class androidx.concurrent.futures.CallbackToFutureAdapter$Completer {
  public boolean set(java.lang.Object);
  public boolean setException(java.lang.Throwable);
}
-keep,allowaccessmodification interface androidx.concurrent.futures.CallbackToFutureAdapter$Resolver {
  public java.lang.Object attachCompleter(androidx.concurrent.futures.CallbackToFutureAdapter$Completer);
}
-keep,allowaccessmodification class androidx.core.os.ConfigurationCompat {
  public static void setLocales(android.content.res.Configuration,androidx.core.os.LocaleListCompat);
}
-keep,allowaccessmodification class androidx.core.os.LocaleListCompat {
  public static androidx.core.os.LocaleListCompat forLanguageTags(java.lang.String);
}
-keep,allowaccessmodification class androidx.core.view.ViewConfigurationCompat {
  public static float getScaledHorizontalScrollFactor(android.view.ViewConfiguration,android.content.Context);
  public static float getScaledVerticalScrollFactor(android.view.ViewConfiguration,android.content.Context);
}
-keep,allowaccessmodification class androidx.graphics.path.PathIterator {
  public <init>(android.graphics.Path,androidx.graphics.path.PathIterator$ConicEvaluation,float);
  public <init>(android.graphics.Path,androidx.graphics.path.PathIterator$ConicEvaluation,float,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static int calculateSize$default(androidx.graphics.path.PathIterator,boolean,int,java.lang.Object);
}
-keep enum androidx.graphics.path.PathIterator$ConicEvaluation {
  androidx.graphics.path.PathIterator$ConicEvaluation AsConic;
  androidx.graphics.path.PathIterator$ConicEvaluation AsQuadratics;
}
-keep,allowaccessmodification class androidx.graphics.path.PathSegment {
  public android.graphics.PointF[] getPoints();
  public androidx.graphics.path.PathSegment$Type getType();
}
-keep enum androidx.graphics.path.PathSegment$Type {
  androidx.graphics.path.PathSegment$Type Close;
  androidx.graphics.path.PathSegment$Type Conic;
  androidx.graphics.path.PathSegment$Type Move;
  androidx.graphics.path.PathSegment$Type Quadratic;
}
-keep,allowaccessmodification class androidx.lifecycle.Lifecycle {
  public void addObserver(androidx.lifecycle.LifecycleObserver);
  public void removeObserver(androidx.lifecycle.LifecycleObserver);
}
-keep enum androidx.lifecycle.Lifecycle$Event {
  androidx.lifecycle.Lifecycle$Event ON_RESUME;
}
-keep enum androidx.lifecycle.Lifecycle$State {
  public static androidx.lifecycle.Lifecycle$State[] values();
  androidx.lifecycle.Lifecycle$State CREATED;
  androidx.lifecycle.Lifecycle$State DESTROYED;
  androidx.lifecycle.Lifecycle$State RESUMED;
  androidx.lifecycle.Lifecycle$State STARTED;
}
-keep,allowaccessmodification interface androidx.lifecycle.LifecycleEventObserver {
  public void onStateChanged(androidx.lifecycle.LifecycleOwner,androidx.lifecycle.Lifecycle$Event);
}
-keep,allowaccessmodification interface androidx.lifecycle.LifecycleObserver {
}
-keep,allowaccessmodification interface androidx.lifecycle.LifecycleOwner {
  public androidx.lifecycle.Lifecycle getLifecycle();
}
-keep,allowaccessmodification class androidx.lifecycle.ViewModel {
}
-keep,allowaccessmodification class androidx.lifecycle.ViewModelProvider {
  public <init>(androidx.lifecycle.ViewModelStore,androidx.lifecycle.ViewModelProvider$Factory,androidx.lifecycle.viewmodel.CreationExtras,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public androidx.lifecycle.ViewModel get(java.lang.Class);
}
-keep,allowaccessmodification class androidx.lifecycle.ViewModelProvider$AndroidViewModelFactory {
  androidx.lifecycle.ViewModelProvider$AndroidViewModelFactory$Companion Companion;
}
-keep,allowaccessmodification class androidx.lifecycle.ViewModelProvider$AndroidViewModelFactory$Companion {
  public androidx.lifecycle.ViewModelProvider$AndroidViewModelFactory getInstance(android.app.Application);
}
-keep,allowaccessmodification interface androidx.lifecycle.ViewModelProvider$Factory {
}
-keep,allowaccessmodification class androidx.lifecycle.ViewModelStore {
  public <init>();
  public void clear();
}
-keep,allowaccessmodification class androidx.lifecycle.ViewTreeLifecycleOwner {
  public static androidx.lifecycle.LifecycleOwner get(android.view.View);
}
-keep,allowaccessmodification class androidx.lifecycle.viewmodel.CreationExtras {
}
-keep,allowaccessmodification class androidx.room.BaseRoomConnectionManager {
  public <init>();
  protected java.util.List getCallbacks();
  protected androidx.room.DatabaseConfiguration getConfiguration();
  protected androidx.room.RoomOpenDelegate getOpenDelegate();
  protected void onCreate(androidx.sqlite.SQLiteConnection);
  protected void onMigrate(androidx.sqlite.SQLiteConnection,int,int);
  protected void onOpen(androidx.sqlite.SQLiteConnection);
  public java.lang.Object useConnection(boolean,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class androidx.room.BaseRoomConnectionManager$DriverWrapper {
  public <init>(androidx.room.BaseRoomConnectionManager,androidx.sqlite.SQLiteDriver);
  public androidx.sqlite.SQLiteConnection open(java.lang.String);
}
-keep,allowaccessmodification class androidx.room.DatabaseConfiguration {
  public <init>(android.content.Context,java.lang.String,androidx.sqlite.db.SupportSQLiteOpenHelper$Factory,androidx.room.RoomDatabase$MigrationContainer,java.util.List,boolean,androidx.room.RoomDatabase$JournalMode,java.util.concurrent.Executor,java.util.concurrent.Executor,android.content.Intent,boolean,boolean,java.util.Set,java.lang.String,java.io.File,java.util.concurrent.Callable,androidx.room.RoomDatabase$PrepackagedDatabaseCallback,java.util.List,java.util.List,boolean,androidx.sqlite.SQLiteDriver,kotlin.coroutines.CoroutineContext);
  public static androidx.room.DatabaseConfiguration copy$default(androidx.room.DatabaseConfiguration,android.content.Context,java.lang.String,androidx.sqlite.db.SupportSQLiteOpenHelper$Factory,androidx.room.RoomDatabase$MigrationContainer,java.util.List,boolean,androidx.room.RoomDatabase$JournalMode,java.util.concurrent.Executor,java.util.concurrent.Executor,android.content.Intent,boolean,boolean,java.util.Set,java.lang.String,java.io.File,java.util.concurrent.Callable,androidx.room.RoomDatabase$PrepackagedDatabaseCallback,java.util.List,java.util.List,boolean,androidx.sqlite.SQLiteDriver,kotlin.coroutines.CoroutineContext,int,java.lang.Object);
  android.content.Context context;
  java.lang.String name;
  androidx.sqlite.SQLiteDriver sqliteDriver;
  androidx.sqlite.db.SupportSQLiteOpenHelper$Factory sqliteOpenHelperFactory;
}
-keep,allowaccessmodification class androidx.room.InvalidationTracker {
  public <init>(androidx.room.RoomDatabase,java.util.Map,java.util.Map,java.lang.String[]);
}
-keep,allowaccessmodification class androidx.room.Room {
  public static androidx.room.RoomDatabase$Builder databaseBuilder(android.content.Context,java.lang.Class,java.lang.String);
  public static androidx.room.RoomDatabase$Builder inMemoryDatabaseBuilder(android.content.Context,java.lang.Class);
}
-keep,allowaccessmodification class androidx.room.RoomDatabase {
  public <init>();
  public void clearAllTables();
  public void close();
  public java.util.List createAutoMigrations(java.util.Map);
  protected androidx.room.InvalidationTracker createInvalidationTracker();
  public androidx.sqlite.db.SupportSQLiteOpenHelper getOpenHelper();
  public java.util.Set getRequiredAutoMigrationSpecClasses();
}
-keep,allowaccessmodification class androidx.room.RoomDatabase$Builder {
  public androidx.room.RoomDatabase$Builder addMigrations(androidx.room.migration.Migration[]);
  public androidx.room.RoomDatabase$Builder allowMainThreadQueries();
  public androidx.room.RoomDatabase build();
}
-keep enum androidx.room.RoomDatabase$JournalMode {
  androidx.room.RoomDatabase$JournalMode WRITE_AHEAD_LOGGING;
}
-keep,allowaccessmodification class androidx.room.RoomDatabase$MigrationContainer {
  public <init>();
  public void addMigration(androidx.room.migration.Migration);
  public void addMigrations(java.util.List);
  public boolean contains(int,int);
}
-keep,allowaccessmodification class androidx.room.RoomDatabase$PrepackagedDatabaseCallback {
}
-keep,allowaccessmodification class androidx.room.RoomOpenDelegate {
  public <init>(int,java.lang.String,java.lang.String);
  public void createAllTables(androidx.sqlite.SQLiteConnection);
  public void dropAllTables(androidx.sqlite.SQLiteConnection);
  public int getVersion();
  public void onCreate(androidx.sqlite.SQLiteConnection);
  public void onOpen(androidx.sqlite.SQLiteConnection);
  public void onPostMigrate(androidx.sqlite.SQLiteConnection);
  public void onPreMigrate(androidx.sqlite.SQLiteConnection);
  public androidx.room.RoomOpenDelegate$ValidationResult onValidateSchema(androidx.sqlite.SQLiteConnection);
}
-keep,allowaccessmodification class androidx.room.RoomOpenDelegate$ValidationResult {
  public <init>(boolean,java.lang.String);
}
-keep,allowaccessmodification class androidx.room.driver.SupportSQLiteConnection {
  public <init>(androidx.sqlite.db.SupportSQLiteDatabase);
  public androidx.sqlite.db.SupportSQLiteDatabase getDb();
}
-keep,allowaccessmodification class androidx.room.driver.SupportSQLiteDriver {
  public <init>(androidx.sqlite.db.SupportSQLiteOpenHelper);
}
-keep,allowaccessmodification interface androidx.room.migration.AutoMigrationSpec {
}
-keep,allowaccessmodification class androidx.room.migration.Migration {
  int endVersion;
  int startVersion;
}
-keep,allowaccessmodification class androidx.room.util.FtsTableInfo {
  public <init>(java.lang.String,java.util.Set,java.lang.String);
  androidx.room.util.FtsTableInfo$Companion Companion;
  java.lang.String name;
}
-keep,allowaccessmodification class androidx.room.util.FtsTableInfo$Companion {
  public androidx.room.util.FtsTableInfo read(androidx.sqlite.SQLiteConnection,java.lang.String);
}
-keep,allowaccessmodification class androidx.room.util.KClassUtil {
  public static java.lang.Object findAndInstantiateDatabaseImpl$default(java.lang.Class,java.lang.String,int,java.lang.Object);
}
-keep,allowaccessmodification class androidx.room.util.TableInfo {
  public <init>(java.lang.String,java.util.Map,java.util.Set,java.util.Set);
  androidx.room.util.TableInfo$Companion Companion;
  java.lang.String name;
}
-keep,allowaccessmodification class androidx.room.util.TableInfo$Column {
  public <init>(java.lang.String,java.lang.String,boolean,int,java.lang.String,int);
  java.lang.String name;
}
-keep,allowaccessmodification class androidx.room.util.TableInfo$Companion {
  public androidx.room.util.TableInfo read(androidx.sqlite.SQLiteConnection,java.lang.String);
}
-keep,allowaccessmodification class androidx.room.util.TableInfo$ForeignKey {
  public <init>(java.lang.String,java.lang.String,java.lang.String,java.util.List,java.util.List);
}
-keep,allowaccessmodification class androidx.room.util.TableInfo$Index {
  public <init>(java.lang.String,boolean,java.util.List,java.util.List);
}
-keep,allowaccessmodification class androidx.room.util.ViewInfo {
  public <init>(java.lang.String,java.lang.String);
  androidx.room.util.ViewInfo$Companion Companion;
  java.lang.String name;
}
-keep,allowaccessmodification class androidx.room.util.ViewInfo$Companion {
  public androidx.room.util.ViewInfo read(androidx.sqlite.SQLiteConnection,java.lang.String);
}
-keep,allowaccessmodification class androidx.sqlite.SQLite {
  public static void execSQL(androidx.sqlite.SQLiteConnection,java.lang.String);
}
-keep,allowaccessmodification interface androidx.sqlite.SQLiteConnection {
  public void close();
  public androidx.sqlite.SQLiteStatement prepare(java.lang.String);
}
-keep,allowaccessmodification interface androidx.sqlite.SQLiteDriver {
  public androidx.sqlite.SQLiteConnection open(java.lang.String);
}
-keep,allowaccessmodification interface androidx.sqlite.SQLiteStatement {
  public void bindText(int,java.lang.String);
  public java.lang.String getText(int);
  public boolean step();
}
-keep,allowaccessmodification interface androidx.sqlite.db.SupportSQLiteDatabase {
  public int getVersion();
}
-keep,allowaccessmodification interface androidx.sqlite.db.SupportSQLiteOpenHelper {
  public androidx.sqlite.db.SupportSQLiteDatabase getWritableDatabase();
}
-keep,allowaccessmodification class androidx.sqlite.db.SupportSQLiteOpenHelper$Callback {
  public <init>(int);
  public void onCreate(androidx.sqlite.db.SupportSQLiteDatabase);
  public void onDowngrade(androidx.sqlite.db.SupportSQLiteDatabase,int,int);
  public void onOpen(androidx.sqlite.db.SupportSQLiteDatabase);
  public void onUpgrade(androidx.sqlite.db.SupportSQLiteDatabase,int,int);
}
-keep,allowaccessmodification class androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration {
  androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration$Companion Companion;
}
-keep,allowaccessmodification class androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration$Builder {
  public androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration build();
  public androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration$Builder callback(androidx.sqlite.db.SupportSQLiteOpenHelper$Callback);
  public androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration$Builder name(java.lang.String);
}
-keep,allowaccessmodification class androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration$Companion {
  public androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration$Builder builder(android.content.Context);
}
-keep,allowaccessmodification interface androidx.sqlite.db.SupportSQLiteOpenHelper$Factory {
  public androidx.sqlite.db.SupportSQLiteOpenHelper create(androidx.sqlite.db.SupportSQLiteOpenHelper$Configuration);
}
-keep,allowaccessmodification class androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory {
  public <init>();
}
-keep,allowaccessmodification class androidx.tracing.Trace {
  public static void beginSection(java.lang.String);
  public static void endSection();
  public static void forceEnableAppTracing();
}
-keep,allowaccessmodification class androidx.work.WorkInfo {
  public androidx.work.WorkInfo$State getState();
}
-keep enum androidx.work.WorkInfo$State {
  public boolean isFinished();
}
-keep,allowaccessmodification class androidx.work.WorkManager {
  public com.google.common.util.concurrent.ListenableFuture getWorkInfosForUniqueWork(java.lang.String);
  androidx.work.WorkManager$Companion Companion;
}
-keep,allowaccessmodification class androidx.work.WorkManager$Companion {
  public androidx.work.WorkManager getInstance(android.content.Context);
}
-keep,allowaccessmodification interface com.google.common.util.concurrent.ListenableFuture {
  public void addListener(java.lang.Runnable,java.util.concurrent.Executor);
}
-keep,allowaccessmodification class com.squareup.moshi.JsonAdapter {
  public com.squareup.moshi.JsonAdapter failOnUnknown();
  public java.lang.Object fromJson(java.lang.String);
  public com.squareup.moshi.JsonAdapter indent(java.lang.String);
  public java.lang.String toJson(java.lang.Object);
}
-keep,allowaccessmodification class com.squareup.moshi.JsonDataException {
}
-keep,allowaccessmodification class com.squareup.moshi.Moshi {
  public com.squareup.moshi.JsonAdapter adapter(java.lang.Class);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.MainActivity {
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.R$string {
  int action_close;
  int controls_create_project;
  int controls_new_project_name;
  int controls_public_catalog;
  int controls_save_settings;
  int editor_review_before_validate;
  int export_format_coco;
  int models_choose_file;
  int models_tab_explore;
  int models_tab_import;
  int publication_local_archive;
  int publication_publish;
  int qualification_training;
  int quality_open_batches;
  int quality_storage;
  int screen_controls;
  int screen_models;
  int setup_project_name;
  int training_activate;
  int training_scope_classification;
  int training_start;
  int workflow_propose;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils {
  public java.lang.String computeSha256(java.io.File);
  com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.geometry.ViewPoint {
  public float getX();
  public float getY();
  int $stable;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.i18n.StudioTextKt {
  public static java.lang.String tr(java.lang.String,java.lang.String);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles {
  public void replace(java.io.File,kotlin.jvm.functions.Function1);
  com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives {
  public static com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives$Receipt copyVerified$default(com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives,android.content.ContentResolver,java.io.File,android.net.Uri,kotlin.jvm.functions.Function0,int,java.lang.Object);
  com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.storage.SafArchives$Receipt {
  public long getBytes();
  public boolean getPersistentRead();
  public java.lang.String getSha256();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager {
  public <init>(android.content.Context);
  public java.io.File batchExportDir(long,int);
  public static java.io.File getImageFile$default(com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager,java.lang.String,java.lang.String,int,java.lang.Object);
  public java.io.File getImageFile(java.lang.String,java.lang.String);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings {
  public <init>(int,int,java.lang.String,java.lang.String,java.lang.String,java.lang.String,boolean,java.lang.String,java.lang.String,boolean,java.lang.String,java.lang.String,int,int,int,int,int,boolean,boolean,boolean,long,java.lang.String,java.lang.String,boolean,boolean,boolean,boolean,boolean,boolean,boolean,boolean,java.lang.String,int,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings copy$default(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings,int,int,java.lang.String,java.lang.String,java.lang.String,java.lang.String,boolean,java.lang.String,java.lang.String,boolean,java.lang.String,java.lang.String,int,int,int,int,int,boolean,boolean,boolean,long,java.lang.String,java.lang.String,boolean,boolean,boolean,boolean,boolean,boolean,boolean,boolean,java.lang.String,int,int,int,java.lang.Object);
  public boolean getContinuousTraining();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety {
  public java.util.Set getLockedStates();
  com.unicornwhodev.visiondatasetstudio.core.workflow.PublicationSafety INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.core.workflow.StudioPreferences {
  public boolean getShowGuidance();
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.data.db.AnnotationDao {
  public java.lang.Object getAnnotationSync(java.lang.String,kotlin.coroutines.Continuation);
  public java.lang.Object insertOrReplace(com.unicornwhodev.visiondatasetstudio.data.model.AnnotationRecord,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase {
  public com.unicornwhodev.visiondatasetstudio.data.db.AnnotationDao annotationDao();
  public com.unicornwhodev.visiondatasetstudio.data.db.AuditDao auditDao();
  public com.unicornwhodev.visiondatasetstudio.data.db.BatchDao batchDao();
  public com.unicornwhodev.visiondatasetstudio.data.db.ImageIdentityDao imageIdentityDao();
  public com.unicornwhodev.visiondatasetstudio.data.db.ModelProfileDao modelProfileDao();
  public com.unicornwhodev.visiondatasetstudio.data.db.ProjectDao projectDao();
  public com.unicornwhodev.visiondatasetstudio.data.db.SampleDao sampleDao();
  public com.unicornwhodev.visiondatasetstudio.data.db.SourceEntryDao sourceEntryDao();
  com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase$Companion Companion;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase$Companion {
  public com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase getInstance(android.content.Context);
  public androidx.room.migration.Migration getMIGRATION_1_2();
  public androidx.room.migration.Migration getMIGRATION_2_3();
  public androidx.room.migration.Migration getMIGRATION_3_4();
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.data.db.AuditDao {
  public kotlinx.coroutines.flow.Flow getLogsForBatch(int,long);
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.data.db.BatchDao {
  public java.lang.Object getBatchSync(long,int,kotlin.coroutines.Continuation);
  public java.lang.Object insertOrReplace(com.unicornwhodev.visiondatasetstudio.data.model.BatchEntity,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.data.db.ImageIdentityDao {
  public java.lang.Object owner(long,java.lang.String,java.lang.String,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.data.db.ModelProfileDao {
  public java.lang.Object get(java.lang.String,kotlin.coroutines.Continuation);
  public java.lang.Object getAllSync(kotlin.coroutines.Continuation);
  public kotlinx.coroutines.flow.Flow observe();
  public java.lang.Object save(com.unicornwhodev.visiondatasetstudio.data.model.ModelProfileEntity,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.data.db.ProjectDao {
  public java.lang.Object getProjectSync(long,kotlin.coroutines.Continuation);
  public java.lang.Object saveProject(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.data.db.SampleDao {
  public java.lang.Object getAllSamples(long,kotlin.coroutines.Continuation);
  public java.lang.Object getSampleSync(java.lang.String,kotlin.coroutines.Continuation);
  public java.lang.Object getSamplesForBatchSync(long,int,kotlin.coroutines.Continuation);
  public java.lang.Object insertNewSamples(java.util.List,kotlin.coroutines.Continuation);
  public java.lang.Object insertSamples(java.util.List,kotlin.coroutines.Continuation);
  public java.lang.Object updateSample(com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.data.db.SourceEntryDao {
  public java.lang.Object insert(java.util.List,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient {
  public <init>(kotlin.jvm.functions.Function0);
  public <init>(okhttp3.OkHttpClient,kotlin.jvm.functions.Function0);
  public java.lang.Object checkDatasetAccess(java.lang.String,kotlin.coroutines.Continuation);
  public java.lang.Object createDatasetRepo(java.lang.String,boolean,kotlin.coroutines.Continuation);
  public static java.lang.Object downloadImage$default(com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient,java.lang.String,java.io.File,kotlin.jvm.functions.Function2,long,kotlin.coroutines.Continuation,int,java.lang.Object);
  public java.lang.Object downloadImage(java.lang.String,java.io.File,kotlin.jvm.functions.Function2,long,kotlin.coroutines.Continuation);
  public java.lang.Object requirePathsAbsent(java.lang.String,java.lang.String,java.util.List,kotlin.coroutines.Continuation);
  public static java.lang.Object resolveRevision$default(com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient,java.lang.String,java.lang.String,kotlin.coroutines.Continuation,int,java.lang.Object);
  public static java.lang.Object uploadBatchFiles$default(com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient,java.lang.String,java.lang.String,java.lang.String,java.util.List,java.lang.String,kotlin.coroutines.Continuation,int,java.lang.Object);
  public java.lang.Object verifyToken(kotlin.coroutines.Continuation);
  int $stable;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.hf.HfRepoAccessResult {
  public boolean getExists();
  public boolean isPrivate();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.hf.HfTreeItem {
  public <init>(java.lang.String,java.lang.String,long);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.hf.HfUploadResult {
  public java.lang.String getCommitSha();
  public boolean getConflict();
  public boolean getSuccess();
  public java.lang.String toString();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.hf.HfWhoAmIResult {
  public java.util.List getOrgs();
  public java.lang.String getUsername();
  public boolean isValid();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.json.StudioJson {
  public com.squareup.moshi.Moshi getMoshi();
  com.unicornwhodev.visiondatasetstudio.data.json.StudioJson INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.AnnotationRecord {
  public <init>(java.lang.String,java.lang.String,long,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public java.lang.String getDataJson();
  public java.lang.String getSampleId();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.BatchEntity {
  public <init>(long,int,java.lang.String,int,int,int,int,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.Long,long,long,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public java.lang.String getArchivePath();
  public java.lang.String getArchiveSnapshot();
  public java.lang.String getHfCommitSha();
  public java.lang.String getRemoteParentCommit();
  public java.lang.String getRemoteReceiptJson();
  public java.lang.String getStatus();
  public int getTotalCases();
  public java.lang.String getVerificationKind();
  public java.lang.String getVerifiedArchiveSha256();
  public java.lang.String getVerifiedArchiveUri();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.BoxTarget {
  public <init>(java.lang.String,float,float,float,float,java.lang.String,boolean,java.lang.Float,java.lang.String,java.lang.Float,java.lang.Float,java.lang.Float,java.lang.Float,boolean,java.lang.Integer,int,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.CaptionTarget {
  public <init>(java.lang.String,java.lang.String,java.lang.String,boolean,boolean,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.ImageIdentityEntity {
  public java.lang.String getFirstSampleId();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.MaskTarget {
  public <init>(java.lang.String,java.lang.String,int,int,java.util.List,boolean,java.lang.String,java.lang.Float,boolean,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public boolean isHumanVerified();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.ModelProfileEntity {
  public <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,long,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static com.unicornwhodev.visiondatasetstudio.data.model.ModelProfileEntity copy$default(com.unicornwhodev.visiondatasetstudio.data.model.ModelProfileEntity,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,long,int,java.lang.Object);
  public java.lang.String getId();
  public java.lang.String getModelPath();
  public java.lang.String getName();
  public java.lang.String getSha256();
}
-keep enum com.unicornwhodev.visiondatasetstudio.data.model.PointLocalizationState {
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.PointTarget {
  public <init>(java.lang.String,float,float,java.lang.String,boolean,boolean,com.unicornwhodev.visiondatasetstudio.data.model.PointLocalizationState,boolean,java.lang.Float,java.lang.String,java.lang.Float,java.lang.Float,float,float,boolean,java.lang.String,java.lang.Integer,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static com.unicornwhodev.visiondatasetstudio.data.model.PointTarget copy$default(com.unicornwhodev.visiondatasetstudio.data.model.PointTarget,java.lang.String,float,float,java.lang.String,boolean,boolean,com.unicornwhodev.visiondatasetstudio.data.model.PointLocalizationState,boolean,java.lang.Float,java.lang.String,java.lang.Float,java.lang.Float,float,float,boolean,java.lang.String,java.lang.Integer,java.lang.String,int,java.lang.Object);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity {
  public <init>(long,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,long,java.lang.String,java.lang.String,boolean,java.lang.String,long,long,long,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity copy$default(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,long,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,long,java.lang.String,java.lang.String,boolean,java.lang.String,long,long,long,int,java.lang.Object);
  public java.lang.String getClassesCsv();
  public long getId();
  public long getLastRowCursor();
  public java.lang.String getModelConfigJson();
  public java.lang.String getModelPath();
  public java.lang.String getName();
  public java.lang.String getSettingsJson();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.QualityAuditTarget {
  public <init>(java.util.List,boolean,boolean,boolean,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations {
  public <init>(java.util.List,java.util.List,java.util.List,java.util.List,java.util.List,java.util.List,java.util.List,java.util.List,com.unicornwhodev.visiondatasetstudio.data.model.QualityAuditTarget,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations copy$default(com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations,java.util.List,java.util.List,java.util.List,java.util.List,java.util.List,java.util.List,java.util.List,java.util.List,com.unicornwhodev.visiondatasetstudio.data.model.QualityAuditTarget,int,java.lang.Object);
  public java.util.List getMasks();
  public java.util.List getPoints();
  public java.util.List getTags();
  int $stable;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity {
  public <init>(java.lang.String,long,int,java.lang.String,long,java.lang.Long,java.lang.String,java.lang.String,int,int,java.lang.String,java.lang.Long,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,long,long,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity copy$default(com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity,java.lang.String,long,int,java.lang.String,long,java.lang.Long,java.lang.String,java.lang.String,int,int,java.lang.String,java.lang.Long,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,long,long,int,java.lang.Object);
  public java.lang.String getAcquisitionStatus();
  public java.lang.String getAnnotationStatus();
  public java.lang.String getAssetId();
  public java.lang.String getLocalImagePath();
  public java.lang.Long getPhash();
  public java.lang.String getSampleId();
  public java.lang.String getSha256();
  public java.lang.String getSyncStatus();
  int $stable;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.SourceEntryEntity {
  public <init>(long,long,java.lang.String,java.lang.String,java.lang.Long,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.TagTarget {
  public <init>(java.lang.String,java.lang.String,boolean,boolean,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public java.lang.String getId();
  public java.lang.String getSourceProvenance();
  public boolean isHumanVerified();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.model.VqaTarget {
  public <init>(java.lang.String,java.lang.String,java.lang.String,boolean,java.util.List,boolean,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings {
  public com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings read(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity);
  public java.lang.String write(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings);
  com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.data.preferences.StudioPreferenceStore {
  public <init>(android.content.Context);
  public long getActiveProjectId();
  public kotlinx.coroutines.flow.StateFlow getState();
  public void setActiveProjectId(long);
  public void setLastBatch(int);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.batch.BatchDiscoveryResult {
  public boolean getEndOfSource();
  public boolean getSuccess();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine {
  public <init>(com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase,com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager,com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient,com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine,com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters);
  public static java.lang.Object discoverViewerBatch$default(com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine,com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,int,int,boolean,kotlin.coroutines.Continuation,int,java.lang.Object);
  public java.lang.Object getSampleAnnotations(java.lang.String,kotlin.coroutines.Continuation);
  public java.lang.Object prepareUniqueBatch(long,int,int,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation);
  public java.lang.Object publishAndVerifyBatch(long,int,kotlin.coroutines.Continuation);
  public java.lang.Object purgeReviewedBatch(long,int,boolean,kotlin.coroutines.Continuation);
  public java.lang.Object recordLocalArchive(long,int,java.io.File,kotlin.coroutines.Continuation);
  public java.lang.Object requireUniqueExport(java.util.List,kotlin.coroutines.Continuation);
  public static java.lang.Object runBatchInference$default(com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine,long,int,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,boolean,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation,int,java.lang.Object);
  public java.lang.Object saveSampleAnnotations(java.lang.String,com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations,kotlin.coroutines.Continuation);
  public java.lang.Object validateSample(java.lang.String,int,kotlin.coroutines.Continuation);
  public java.lang.Object verifyLocalArchive(long,int,java.lang.String,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.batch.BatchPublishResult {
  public boolean getSuccess();
  public java.lang.String toString();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.batch.ClaimSelection {
  public java.util.List getEntries();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity {
  public java.lang.Object accept(com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase,com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity,java.lang.String,kotlin.coroutines.Continuation);
  public java.lang.Object pixelSha256(java.io.File,kotlin.coroutines.Continuation);
  com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.batch.ProjectMaintenance {
  public <init>(android.content.Context,com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase,com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager,kotlin.jvm.functions.Function1);
  public <init>(android.content.Context,com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase,com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager,kotlin.jvm.functions.Function1,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public java.lang.Object discardBatch(long,int,kotlin.coroutines.Continuation);
  public java.lang.Object resetBatch(long,int,kotlin.coroutines.Continuation);
  public java.lang.Object resetProject(long,kotlin.coroutines.Continuation);
  public java.lang.Object resumePending(kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.batch.WorkClaimCoordinator {
  public <init>(android.content.Context,com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient);
  public java.lang.Object claim(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings,java.util.List,int,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters {
  public <init>(com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager,com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient);
  public static java.lang.Object packageBatchToLocalZip$default(com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters,com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,int,java.util.List,boolean,boolean,boolean,boolean,boolean,kotlin.coroutines.Continuation,int,java.lang.Object);
  public java.lang.Object packageBatchToLocalZip(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,int,java.util.List,boolean,boolean,boolean,boolean,boolean,kotlin.coroutines.Continuation);
  public boolean verifyPreparedPackage(java.io.File);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters$LocalZipExportResult {
  public java.lang.String getError();
  public int getSampleCount();
  public boolean getSuccess();
  public java.io.File getZipFile();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.AdaptiveCorrection {
  public java.util.List apply(java.util.List,com.unicornwhodev.visiondatasetstudio.domain.inference.CorrectionLedger);
  public java.lang.String hash(java.lang.String);
  com.unicornwhodev.visiondatasetstudio.domain.inference.AdaptiveCorrection INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.AdaptiveCorrectionStore {
  public <init>(android.content.Context);
  public com.unicornwhodev.visiondatasetstudio.domain.inference.CorrectionLedger read(long);
  public void reset(long);
  public java.lang.Object train(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,java.util.List,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.AuxiliaryTarget {
  public java.util.List getLabels();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.BundleManifest {
  public <init>(int,java.lang.String,java.lang.String,java.util.Map,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.BytePairTokenizer {
  public <init>(java.io.File);
  public static java.lang.String decode$default(com.unicornwhodev.visiondatasetstudio.domain.inference.BytePairTokenizer,java.util.List,boolean,int,java.lang.Object);
  public kotlin.Pair encode(java.lang.String,int,int);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.CheckpointReceipt {
  public java.util.Map getFiles();
  public java.lang.String getPrefix();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.CommunityModelCatalog {
  public java.util.List getEntries();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig suggestedConfig(com.unicornwhodev.visiondatasetstudio.domain.inference.CommunityModelCatalog$Availability,java.io.File);
  com.unicornwhodev.visiondatasetstudio.domain.inference.CommunityModelCatalog INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.CommunityModelCatalog$Availability {
  public <init>(com.unicornwhodev.visiondatasetstudio.domain.inference.CommunityModelCatalog$Entry,java.lang.String,java.util.List,boolean,boolean,java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.CommunityModelCatalog$Entry {
  public <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.util.List,java.lang.String,java.lang.String,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelCapabilities,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public java.util.List getExpectedFiles();
  public java.lang.String getId();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.CorrectionGroup {
  public java.util.List getExamples();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.CorrectionHead getHead();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.CorrectionHead {
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.CorrectionLedger {
  public java.util.List getGroups();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.DryRunResult {
  public java.lang.String getBackend();
  public java.lang.String getError();
  public long getLatencyMs();
  public java.util.List getProposals();
  public boolean getSuccess();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.EmbeddingIndex {
  public <init>(android.content.Context,long);
  public com.unicornwhodev.visiondatasetstudio.domain.inference.ImageEmbedding get(java.lang.String);
  public static java.util.List nearest$default(com.unicornwhodev.visiondatasetstudio.domain.inference.EmbeddingIndex,com.unicornwhodev.visiondatasetstudio.domain.inference.ImageEmbedding,int,int,java.lang.Object);
  public com.unicornwhodev.visiondatasetstudio.domain.inference.ImageEmbedding put(java.lang.String,java.lang.String,java.lang.String,float[]);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.ImageEmbedding {
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceDiagnostics {
  public <init>(java.lang.String,java.lang.String,java.lang.String,java.util.List,java.util.List,float,int,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.util.List,java.util.List,java.lang.Long,java.lang.String,java.lang.String,java.util.List,java.lang.String,java.util.List,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public java.util.List getInputShape();
  public java.util.List getOutputShapes();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceReceiptStore {
  public <init>(java.io.File);
  public java.util.List list(long);
  public java.io.File write(long,java.lang.String,java.lang.Integer,com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceResult);
}
-keep,allowaccessmodification interface com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceResult {
  public com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceDiagnostics getDiagnostics();
  public java.util.List orThrow();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceResult$Empty {
  public <init>(java.lang.String,com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceDiagnostics);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.InputTransform {
  com.unicornwhodev.visiondatasetstudio.domain.inference.InputTransform$Companion Companion;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.InputTransform$Companion {
  public com.unicornwhodev.visiondatasetstudio.domain.inference.InputTransform create(int,int,int,int,java.lang.String,float);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtBundle {
  public <init>(java.io.File);
  public void close();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine {
  public <init>();
  public void close();
  public java.lang.Object dryRun(android.graphics.Bitmap,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,kotlin.coroutines.Continuation);
  public float[] getLastEmbedding();
  public java.lang.String getLastError();
  public android.graphics.Bitmap getLastMask();
  public java.lang.String getLastNote();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.TensorValues getLastPatches();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceResult getLastResult();
  public static boolean loadModel$default(com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine,java.io.File,int,int,java.lang.Object);
  public boolean loadModel(java.io.File,int);
  public java.lang.Object runInference(android.graphics.Bitmap,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,kotlin.coroutines.Continuation);
  public java.lang.String tensorReport();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtTrainingSession {
  public <init>(java.io.File,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig);
  public java.util.List infer(android.graphics.Bitmap);
  public void restore(com.unicornwhodev.visiondatasetstudio.domain.inference.CheckpointReceipt);
  public com.unicornwhodev.visiondatasetstudio.domain.inference.CheckpointReceipt save(java.io.File);
  public static float train$default(com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtTrainingSession,android.graphics.Bitmap,float[],float,java.util.Map,int,java.lang.Object);
  public float train(android.graphics.Bitmap,float[],float,java.util.Map);
  public java.lang.String weightProbe();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.LocalModelClient {
  public <init>();
  public java.lang.Object run(android.graphics.Bitmap,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec {
  public boolean[] decode(com.unicornwhodev.visiondatasetstudio.data.model.MaskTarget);
  public java.util.List encode(boolean[]);
  com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.ModelAdapters {
  public java.util.List decode(java.util.Map,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,com.unicornwhodev.visiondatasetstudio.domain.inference.InputTransform);
  com.unicornwhodev.visiondatasetstudio.domain.inference.ModelAdapters INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.ModelCapabilities {
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig {
  public <init>(java.lang.String,int,int,int,java.lang.String,float,float,boolean,java.util.List,float,java.lang.String,int,int,int,int,java.lang.String,java.lang.String,int,java.lang.String,java.lang.String,int,java.util.List,java.util.List,java.lang.String,int,java.lang.String,java.lang.String,boolean,int,java.lang.String,float,int,int,java.lang.String,java.lang.String,boolean,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int,int,java.util.Map,int,int,float,int,int,int,java.lang.String,java.util.List,java.util.List,java.lang.String,java.util.Map,java.util.List,com.unicornwhodev.visiondatasetstudio.domain.inference.TrainingContract,java.lang.String,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig copy$default(com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,java.lang.String,int,int,int,java.lang.String,float,float,boolean,java.util.List,float,java.lang.String,int,int,int,int,java.lang.String,java.lang.String,int,java.lang.String,java.lang.String,int,java.util.List,java.util.List,java.lang.String,int,java.lang.String,java.lang.String,boolean,int,java.lang.String,float,int,int,java.lang.String,java.lang.String,boolean,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int,int,java.util.Map,int,int,float,int,int,int,java.lang.String,java.util.List,java.util.List,java.lang.String,java.util.Map,java.util.List,com.unicornwhodev.visiondatasetstudio.domain.inference.TrainingContract,java.lang.String,int,int,java.lang.Object);
  public java.lang.String getBundleKind();
  public float getCropFraction();
  public int getInputHeight();
  public int getInputWidth();
  public java.util.List getLabels();
  public int getOutputIndex();
  public java.lang.String getPrompt();
  public java.lang.String getSpatialLabel();
  public int getThreads();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.TrainingContract getTraining();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig signatureConfig();
  com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig$Companion Companion;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig$Companion {
  public static com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig defaultClassifierPreset$default(com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig$Companion,java.util.List,int,java.lang.Object);
  public com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig defaultClassifierPreset(java.util.List);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.ModelContract {
  public java.lang.String adapter(com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig);
  public java.lang.String resize(com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig);
  public void validate(com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig);
  com.unicornwhodev.visiondatasetstudio.domain.inference.ModelContract INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.ModelProposal {
  public <init>(java.lang.String,java.lang.String,float,float,float,float,float,float,float,java.lang.String,java.lang.String,int,java.lang.String,java.lang.Float,java.lang.Float,float,float,com.unicornwhodev.visiondatasetstudio.data.model.MaskTarget,java.lang.Integer,java.lang.Float,java.lang.Float,java.lang.Float,java.lang.Float,java.lang.String,java.util.List,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public java.lang.String getLabel();
  public java.lang.Float getModelX();
  public float getPointX();
  public float getScore();
  public java.lang.String getSource();
  public java.lang.String getText();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.PublicModelCatalog {
  public java.util.List getEntries();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.PublicModelCatalog$Inspection inspect(com.unicornwhodev.visiondatasetstudio.domain.inference.PublicModelCatalog$Entry,java.io.File);
  com.unicornwhodev.visiondatasetstudio.domain.inference.PublicModelCatalog INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.PublicModelCatalog$Entry {
  public java.lang.String getId();
  public long getMaxBytes();
  public java.lang.String getUrl();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.PublicModelCatalog$Inspection {
  public com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig getConfig();
  public java.lang.String getNote();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.SimilarImage {
  public float getCosine();
  public java.lang.String getSampleId();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.StudioPack {
  public <init>(java.lang.String,java.lang.String,java.util.List,java.util.List,int,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public void validate();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.TensorValues {
  public java.util.List getShape();
  public float[] getValues();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.inference.TrainingContract {
  public java.util.Map getAuxiliaryTargets();
  public java.lang.String getScope();
  public java.lang.String getTargetEncoding();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.DeviceTrainingRun {
  public com.unicornwhodev.visiondatasetstudio.domain.inference.CheckpointReceipt getCheckpoint();
  public int getCompletedSteps();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig getConfig();
  public java.lang.String getError();
  public java.lang.String getExportProof();
  public java.lang.String getFinalWeightProbe();
  public int getGeneration();
  public java.lang.String getId();
  public java.lang.Double getInitialLoss();
  public java.lang.String getInitialWeightProbe();
  public java.lang.String getLineageId();
  public java.lang.String getModelFile();
  public java.lang.String getParentRunId();
  public java.lang.String getPhase();
  public java.util.List getSamples();
  public int getSourceBatchNumber();
  public int getTotalSteps();
  public java.lang.Double getValidationLoss();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.LearnedTrainingModel {
  public java.lang.String getRunId();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining {
  public <init>(android.content.Context);
  public java.lang.Object abandon(long,java.lang.String,kotlin.coroutines.Continuation);
  public java.lang.Object activate(long,int,kotlin.coroutines.Continuation);
  public java.lang.Object cancel(long,kotlin.coroutines.Continuation);
  public java.lang.Object cleanupOrphanedCandidates(com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase,kotlin.coroutines.Continuation);
  public void enqueue(long);
  public static java.lang.Object inspectPreparation$default(com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining,com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,int,boolean,kotlin.coroutines.Continuation,int,java.lang.Object);
  public java.lang.Object prepare(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,int,int,float,kotlin.coroutines.Continuation);
  public com.unicornwhodev.visiondatasetstudio.domain.training.DeviceTrainingRun read(long);
  public com.unicornwhodev.visiondatasetstudio.domain.training.DeviceTrainingRun readBatch(long,int);
  public void releaseBatchImages(long,int);
  public void requireCleanupAllowed(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity,int,boolean,java.lang.String);
  public java.lang.Object resume(long,kotlin.coroutines.Continuation);
  com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining$Companion Companion;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining$Companion {
  public java.lang.String saveCheckpointReceipt(java.io.File,com.unicornwhodev.visiondatasetstudio.domain.inference.CheckpointReceipt);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.PreparedTrainingSample {
  public java.lang.String getSha256();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.TrainingInspection {
  public java.util.List getSamples();
  public int getTrainCount();
  public int getValidationCount();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.TrainingLineage {
  public com.unicornwhodev.visiondatasetstudio.domain.training.LearnedTrainingModel getLearned();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.TrainingLineageStore {
  public <init>(android.content.Context);
  public com.unicornwhodev.visiondatasetstudio.domain.training.TrainingLineage resolve(long,java.io.File,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.TrainingPolicy {
  public boolean enabled(com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity);
  com.unicornwhodev.visiondatasetstudio.domain.training.TrainingPolicy INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.TrainingSample {
  public java.lang.String getImage();
  public java.lang.String getSha256();
  public boolean getValidation();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.training.TrainingTargets {
  public java.util.Map auxiliary(com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig);
  public float[] encode(com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations,com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,int,int);
  public static double validationLoss$default(com.unicornwhodev.visiondatasetstudio.domain.training.TrainingTargets,java.util.List,float[],com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig,java.util.Map,int,java.lang.Object);
  com.unicornwhodev.visiondatasetstudio.domain.training.TrainingTargets INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.workflow.LocalWorkflowAgent {
  public java.lang.Object propose(java.lang.String,java.lang.String,java.util.Map,kotlin.coroutines.Continuation);
  com.unicornwhodev.visiondatasetstudio.domain.workflow.LocalWorkflowAgent INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.workflow.LocalWorkflowAgent$Choice {
  public java.lang.String getTemplate();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowJournal {
  public <init>(android.content.Context);
  public com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowRun read(long,int);
  public void write(com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowRun);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowRun {
  public <init>(long,int,java.lang.String,int,java.lang.String,java.lang.String,java.lang.String,int,int,kotlin.jvm.internal.DefaultConstructorMarker);
  public static com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowRun copy$default(com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowRun,long,int,java.lang.String,int,java.lang.String,java.lang.String,java.lang.String,int,int,java.lang.Object);
  public int getCursor();
  public java.lang.String getPhase();
  public java.lang.String getTemplate();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowTemplate {
  public java.util.List getSteps();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowTools {
  public java.lang.String getSystemPrompt();
  public com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowTemplate template(java.lang.String);
  com.unicornwhodev.visiondatasetstudio.domain.workflow.WorkflowTools INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.MainViewModel {
  public void clearOperationProgress();
  public void createProject(java.lang.String);
  public void deleteCurrentProject();
  public kotlinx.coroutines.flow.StateFlow getActiveProjectId();
  public com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine getBatchEngine();
  public kotlinx.coroutines.flow.StateFlow getCurrentAnnotations();
  public kotlinx.coroutines.flow.StateFlow getCurrentSample();
  public com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase getDb();
  public com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining getDeviceTraining();
  public kotlinx.coroutines.flow.StateFlow getEditorBusy();
  public com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine getLiteRtEngine();
  public kotlinx.coroutines.flow.StateFlow getOperationProgress();
  public com.unicornwhodev.visiondatasetstudio.data.preferences.StudioPreferenceStore getPreferenceStore();
  public kotlinx.coroutines.flow.StateFlow getPreferences();
  public kotlinx.coroutines.flow.StateFlow getProjectFlow();
  public com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager getStorageManager();
  public kotlinx.coroutines.flow.StateFlow getWorkflow();
  public void importModel(android.net.Uri);
  public kotlinx.coroutines.flow.StateFlow isBusy();
  public void loadBatch(int);
  public void navigateTo(com.unicornwhodev.visiondatasetstudio.ui.Screen);
  public void nextBatch();
  public void openSampleInEditor(java.lang.String);
  public static void preannotateActiveBatch$default(com.unicornwhodev.visiondatasetstudio.ui.MainViewModel,boolean,int,java.lang.Object);
  public void purgeActiveBatch();
  public void removeModelProfile(java.lang.String);
  public void resumeWorkflow();
  public static void runLiteRtOnCurrentSample$default(com.unicornwhodev.visiondatasetstudio.ui.MainViewModel,java.util.List,int,java.lang.Object);
  public void saveActiveModelProfile(java.lang.String);
  public void saveModelConfig(java.lang.String);
  public void selectModelProfile(java.lang.String);
  public void selectProject(long);
  public void startWorkflow(java.lang.String,java.lang.String);
  public void updateAnnotations(com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations);
  public void updatePreferences(com.unicornwhodev.visiondatasetstudio.core.workflow.StudioPreferences);
  public void validateCurrentAndNext();
  int $stable;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.OperationProgress {
  public boolean isError();
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen {
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$BatchGrid {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$BatchGrid INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$Home {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$Home INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$Models {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$Models INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$Preferences {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$Preferences INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$Publication {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$Publication INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$QualityDashboard {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$QualityDashboard INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$Setup {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$Setup INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$Similarity {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$Similarity INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$Training {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$Training INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.Screen$Workflow {
  com.unicornwhodev.visiondatasetstudio.ui.Screen$Workflow INSTANCE;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.StudioRootKt {
  public static void StudioRoot(com.unicornwhodev.visiondatasetstudio.ui.MainViewModel,androidx.compose.runtime.Composer,int);
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.screens.AnnotationEditorScreenKt {
  public static void InteractiveAnnotationCanvas-FN1QlJA(com.unicornwhodev.visiondatasetstudio.data.model.SampleEntity,com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations,com.unicornwhodev.visiondatasetstudio.ui.screens.EditorTool,java.lang.String,java.lang.String,float,long,kotlin.jvm.functions.Function2,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1,float,boolean,com.unicornwhodev.visiondatasetstudio.core.geometry.ViewPoint,kotlin.jvm.functions.Function1,androidx.compose.runtime.Composer,int,int,int);
}
-keep enum com.unicornwhodev.visiondatasetstudio.ui.screens.EditorTool {
  com.unicornwhodev.visiondatasetstudio.ui.screens.EditorTool MASK;
  com.unicornwhodev.visiondatasetstudio.ui.screens.EditorTool SAM_POINT;
}
-keep,allowaccessmodification class com.unicornwhodev.visiondatasetstudio.ui.theme.ThemeKt {
  public static void VisionDatasetStudioTheme(boolean,kotlin.jvm.functions.Function2,androidx.compose.runtime.Composer,int,int);
}
-keep @interface kotlin.Deprecated {
  public kotlin.DeprecationLevel level();
  public java.lang.String message();
  public kotlin.ReplaceWith replaceWith();
}
-keep enum kotlin.DeprecationLevel {
  kotlin.DeprecationLevel ERROR;
  kotlin.DeprecationLevel HIDDEN;
  kotlin.DeprecationLevel WARNING;
}
-keep,allowaccessmodification class kotlin.ExceptionsKt {
}
-keep,allowaccessmodification class kotlin.ExceptionsKt__ExceptionsKt {
  public static void addSuppressed(java.lang.Throwable,java.lang.Throwable);
}
-keep,allowaccessmodification interface kotlin.Function {
}
-keep,allowaccessmodification class kotlin.KotlinNothingValueException {
  public <init>();
}
-keep,allowaccessmodification interface kotlin.Lazy {
  public java.lang.Object getValue();
}
-keep,allowaccessmodification class kotlin.LazyKt {
}
-keep,allowaccessmodification class kotlin.LazyKt__LazyJVMKt {
  public static kotlin.Lazy lazy(kotlin.LazyThreadSafetyMode,kotlin.jvm.functions.Function0);
  public static kotlin.Lazy lazy(kotlin.jvm.functions.Function0);
}
-keep enum kotlin.LazyThreadSafetyMode {
  kotlin.LazyThreadSafetyMode PUBLICATION;
}
-keep @interface kotlin.Metadata {
  public java.lang.String[] d1();
  public java.lang.String[] d2();
  public int k();
  public int[] mv();
  public int xi();
  public java.lang.String xs();
}
-keep,allowaccessmodification class kotlin.NoWhenBranchMatchedException {
  public <init>();
}
-keep,allowaccessmodification class kotlin.Pair {
  public <init>(java.lang.Object,java.lang.Object);
  public java.lang.Object component1();
  public java.lang.Object component2();
  public java.lang.Object getFirst();
  public java.lang.Object getSecond();
}
-keep @interface kotlin.PublishedApi {
}
-keep @interface kotlin.RequiresOptIn {
  public java.lang.String message();
}
-keep,allowaccessmodification class kotlin.Result {
  public static java.lang.Object constructor-impl(java.lang.Object);
  public static java.lang.Throwable exceptionOrNull-impl(java.lang.Object);
  public static boolean isFailure-impl(java.lang.Object);
  kotlin.Result$Companion Companion;
}
-keep,allowaccessmodification class kotlin.Result$Companion {
}
-keep,allowaccessmodification class kotlin.ResultKt {
  public static java.lang.Object createFailure(java.lang.Throwable);
  public static void throwOnFailure(java.lang.Object);
}
-keep,allowaccessmodification class kotlin.TuplesKt {
  public static kotlin.Pair to(java.lang.Object,java.lang.Object);
}
-keep,allowaccessmodification class kotlin.Unit {
  kotlin.Unit INSTANCE;
}
-keep,allowaccessmodification class kotlin._Assertions {
  boolean ENABLED;
}
-keep enum kotlin.annotation.AnnotationRetention {
  kotlin.annotation.AnnotationRetention BINARY;
}
-keep @interface kotlin.annotation.Retention {
  public kotlin.annotation.AnnotationRetention value();
}
-keep,allowaccessmodification class kotlin.collections.AbstractIterator {
  public <init>();
  protected void computeNext();
  protected void done();
  protected void setNext(java.lang.Object);
}
-keep,allowaccessmodification class kotlin.collections.ArrayDeque {
  public <init>();
  public void addLast(java.lang.Object);
  public boolean isEmpty();
  public java.lang.Object removeFirstOrNull();
}
-keep,allowaccessmodification class kotlin.collections.ArraysKt {
}
-keep,allowaccessmodification class kotlin.collections.ArraysKt___ArraysKt {
  public static boolean contains(java.lang.Object[],java.lang.Object);
  public static java.util.List drop(int[],int);
  public static java.lang.Object first(java.lang.Object[]);
  public static kotlin.ranges.IntRange getIndices(float[]);
  public static java.lang.String joinToString$default(byte[],java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1,int,java.lang.Object);
  public static java.lang.String joinToString$default(java.lang.Object[],java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1,int,java.lang.Object);
  public static java.lang.Object last(java.lang.Object[]);
  public static java.lang.Object single(java.lang.Object[]);
  public static int sum(int[]);
  public static java.util.List take(int[],int);
  public static java.util.List toList(float[]);
  public static java.util.List toList(int[]);
  public static java.util.List toList(java.lang.Object[]);
}
-keep,allowaccessmodification class kotlin.collections.CollectionsKt {
}
-keep,allowaccessmodification class kotlin.collections.CollectionsKt__CollectionsJVMKt {
  public static java.util.List build(java.util.List);
  public static java.util.List createListBuilder();
  public static java.util.List listOf(java.lang.Object);
}
-keep,allowaccessmodification class kotlin.collections.CollectionsKt__CollectionsKt {
  public static java.util.List emptyList();
  public static kotlin.ranges.IntRange getIndices(java.util.Collection);
  public static java.util.List listOf(java.lang.Object[]);
  public static java.util.List listOfNotNull(java.lang.Object);
  public static void throwCountOverflow();
  public static void throwIndexOverflow();
}
-keep,allowaccessmodification class kotlin.collections.CollectionsKt__IterablesKt {
  public static int collectionSizeOrDefault(java.lang.Iterable,int);
}
-keep,allowaccessmodification class kotlin.collections.CollectionsKt__IteratorsJVMKt {
  public static java.util.Iterator iterator(java.util.Enumeration);
}
-keep,allowaccessmodification class kotlin.collections.CollectionsKt__MutableCollectionsKt {
  public static boolean addAll(java.util.Collection,java.lang.Iterable);
}
-keep,allowaccessmodification class kotlin.collections.CollectionsKt___CollectionsKt {
  public static kotlin.sequences.Sequence asSequence(java.lang.Iterable);
  public static int count(java.lang.Iterable);
  public static java.util.List drop(java.lang.Iterable,int);
  public static java.lang.Object first(java.lang.Iterable);
  public static java.lang.Object first(java.util.List);
  public static java.lang.Object firstOrNull(java.util.List);
  public static java.util.Set intersect(java.lang.Iterable,java.lang.Iterable);
  public static java.lang.String joinToString$default(java.lang.Iterable,java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1,int,java.lang.Object);
  public static java.lang.Object last(java.util.List);
  public static java.util.List plus(java.util.Collection,java.lang.Iterable);
  public static java.util.List plus(java.util.Collection,java.lang.Object);
  public static java.lang.Object single(java.util.List);
  public static java.util.List sorted(java.lang.Iterable);
  public static java.util.List sortedWith(java.lang.Iterable,java.util.Comparator);
  public static java.util.List takeLast(java.util.List,int);
  public static float[] toFloatArray(java.util.Collection);
  public static int[] toIntArray(java.util.Collection);
  public static java.util.List toList(java.lang.Iterable);
  public static java.util.Set toSet(java.lang.Iterable);
  public static java.util.List zipWithNext(java.lang.Iterable);
}
-keep,allowaccessmodification class kotlin.collections.IntIterator {
  public int nextInt();
}
-keep,allowaccessmodification class kotlin.collections.MapsKt {
}
-keep,allowaccessmodification class kotlin.collections.MapsKt__MapsJVMKt {
  public static java.util.Map build(java.util.Map);
  public static java.util.Map createMapBuilder();
  public static int mapCapacity(int);
  public static java.util.Map mapOf(kotlin.Pair);
}
-keep,allowaccessmodification class kotlin.collections.MapsKt__MapsKt {
  public static java.util.Map emptyMap();
  public static java.lang.Object getValue(java.util.Map,java.lang.Object);
  public static java.util.LinkedHashMap linkedMapOf(kotlin.Pair[]);
  public static java.util.Map mapOf(kotlin.Pair[]);
  public static java.util.Map mutableMapOf(kotlin.Pair[]);
  public static java.util.Map toMap(java.lang.Iterable);
}
-keep,allowaccessmodification class kotlin.collections.SetsKt {
}
-keep,allowaccessmodification class kotlin.collections.SetsKt__SetsJVMKt {
  public static java.util.Set build(java.util.Set);
  public static java.util.Set createSetBuilder();
}
-keep,allowaccessmodification class kotlin.collections.SetsKt__SetsKt {
  public static java.util.Set emptySet();
  public static java.util.Set setOf(java.lang.Object[]);
}
-keep,allowaccessmodification class kotlin.collections.SetsKt___SetsKt {
  public static java.util.Set minus(java.util.Set,java.lang.Iterable);
}
-keep,allowaccessmodification class kotlin.comparisons.ComparisonsKt {
}
-keep,allowaccessmodification class kotlin.comparisons.ComparisonsKt__ComparisonsKt {
  public static int compareValues(java.lang.Comparable,java.lang.Comparable);
  public static int compareValuesBy(java.lang.Object,java.lang.Object,kotlin.jvm.functions.Function1[]);
}
-keep,allowaccessmodification class kotlin.coroutines.AbstractCoroutineContextElement {
  public <init>(kotlin.coroutines.CoroutineContext$Key);
  public java.lang.Object fold(java.lang.Object,kotlin.jvm.functions.Function2);
  public kotlin.coroutines.CoroutineContext$Element get(kotlin.coroutines.CoroutineContext$Key);
  public kotlin.coroutines.CoroutineContext$Key getKey();
  public kotlin.coroutines.CoroutineContext minusKey(kotlin.coroutines.CoroutineContext$Key);
  public kotlin.coroutines.CoroutineContext plus(kotlin.coroutines.CoroutineContext);
}
-keep,allowaccessmodification class kotlin.coroutines.AbstractCoroutineContextKey {
  public <init>(kotlin.coroutines.CoroutineContext$Key,kotlin.jvm.functions.Function1);
}
-keep,allowaccessmodification interface kotlin.coroutines.Continuation {
  public kotlin.coroutines.CoroutineContext getContext();
  public void resumeWith(java.lang.Object);
}
-keep,allowaccessmodification interface kotlin.coroutines.ContinuationInterceptor {
  public kotlin.coroutines.CoroutineContext$Element get(kotlin.coroutines.CoroutineContext$Key);
  public kotlin.coroutines.Continuation interceptContinuation(kotlin.coroutines.Continuation);
  public kotlin.coroutines.CoroutineContext minusKey(kotlin.coroutines.CoroutineContext$Key);
  public void releaseInterceptedContinuation(kotlin.coroutines.Continuation);
  kotlin.coroutines.ContinuationInterceptor$Key Key;
}
-keep,allowaccessmodification class kotlin.coroutines.ContinuationInterceptor$DefaultImpls {
  public static kotlin.coroutines.CoroutineContext$Element get(kotlin.coroutines.ContinuationInterceptor,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext minusKey(kotlin.coroutines.ContinuationInterceptor,kotlin.coroutines.CoroutineContext$Key);
  public static void releaseInterceptedContinuation(kotlin.coroutines.ContinuationInterceptor,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class kotlin.coroutines.ContinuationInterceptor$Key {
}
-keep,allowaccessmodification interface kotlin.coroutines.CoroutineContext {
  public kotlin.coroutines.CoroutineContext$Element get(kotlin.coroutines.CoroutineContext$Key);
  public kotlin.coroutines.CoroutineContext plus(kotlin.coroutines.CoroutineContext);
}
-keep,allowaccessmodification interface kotlin.coroutines.CoroutineContext$Element {
  public java.lang.Object fold(java.lang.Object,kotlin.jvm.functions.Function2);
  public kotlin.coroutines.CoroutineContext$Element get(kotlin.coroutines.CoroutineContext$Key);
  public kotlin.coroutines.CoroutineContext$Key getKey();
  public kotlin.coroutines.CoroutineContext minusKey(kotlin.coroutines.CoroutineContext$Key);
}
-keep,allowaccessmodification class kotlin.coroutines.CoroutineContext$Element$DefaultImpls {
  public static java.lang.Object fold(kotlin.coroutines.CoroutineContext$Element,java.lang.Object,kotlin.jvm.functions.Function2);
  public static kotlin.coroutines.CoroutineContext$Element get(kotlin.coroutines.CoroutineContext$Element,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext minusKey(kotlin.coroutines.CoroutineContext$Element,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext plus(kotlin.coroutines.CoroutineContext$Element,kotlin.coroutines.CoroutineContext);
}
-keep,allowaccessmodification interface kotlin.coroutines.CoroutineContext$Key {
}
-keep,allowaccessmodification class kotlin.coroutines.EmptyCoroutineContext {
  kotlin.coroutines.EmptyCoroutineContext INSTANCE;
}
-keep,allowaccessmodification class kotlin.coroutines.intrinsics.IntrinsicsKt {
}
-keep,allowaccessmodification class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt {
  public static kotlin.coroutines.Continuation intercepted(kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt {
  public static java.lang.Object getCOROUTINE_SUSPENDED();
}
-keep,allowaccessmodification class kotlin.coroutines.jvm.internal.Boxing {
  public static java.lang.Boolean boxBoolean(boolean);
  public static java.lang.Character boxChar(char);
  public static java.lang.Double boxDouble(double);
  public static java.lang.Float boxFloat(float);
  public static java.lang.Integer boxInt(int);
  public static java.lang.Long boxLong(long);
}
-keep,allowaccessmodification class kotlin.coroutines.jvm.internal.ContinuationImpl {
  public <init>(kotlin.coroutines.Continuation);
}
-keep @interface kotlin.coroutines.jvm.internal.DebugMetadata {
  public java.lang.String c();
  public java.lang.String f();
  public int[] i();
  public int[] l();
  public java.lang.String m();
  public java.lang.String[] n();
  public java.lang.String[] s();
}
-keep,allowaccessmodification class kotlin.coroutines.jvm.internal.DebugProbesKt {
  public static void probeCoroutineSuspended(kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class kotlin.coroutines.jvm.internal.SpillingKt {
  public static java.lang.Object nullOutSpilledVariable(java.lang.Object);
}
-keep,allowaccessmodification class kotlin.coroutines.jvm.internal.SuspendLambda {
  public <init>(int,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class kotlin.io.ByteStreamsKt {
  public static long copyTo$default(java.io.InputStream,java.io.OutputStream,int,int,java.lang.Object);
  public static byte[] readBytes(java.io.InputStream);
}
-keep,allowaccessmodification class kotlin.io.CloseableKt {
  public static void closeFinally(java.io.Closeable,java.lang.Throwable);
}
-keep,allowaccessmodification class kotlin.io.FileTreeWalk {
}
-keep,allowaccessmodification class kotlin.io.FilesKt {
}
-keep,allowaccessmodification class kotlin.io.FilesKt__FileReadWriteKt {
  public static byte[] readBytes(java.io.File);
  public static java.lang.String readText$default(java.io.File,java.nio.charset.Charset,int,java.lang.Object);
  public static void writeBytes(java.io.File,byte[]);
  public static void writeText$default(java.io.File,java.lang.String,java.nio.charset.Charset,int,java.lang.Object);
}
-keep,allowaccessmodification class kotlin.io.FilesKt__FileTreeWalkKt {
  public static kotlin.io.FileTreeWalk walkTopDown(java.io.File);
}
-keep,allowaccessmodification class kotlin.io.FilesKt__UtilsKt {
  public static java.io.File copyTo$default(java.io.File,java.io.File,boolean,int,int,java.lang.Object);
  public static boolean deleteRecursively(java.io.File);
  public static java.lang.String getExtension(java.io.File);
  public static java.lang.String getInvariantSeparatorsPath(java.io.File);
  public static java.lang.String getNameWithoutExtension(java.io.File);
  public static java.io.File relativeTo(java.io.File,java.io.File);
}
-keep,allowaccessmodification class kotlin.io.TextStreamsKt {
  public static java.lang.String readText(java.io.Reader);
}
-keep,allowaccessmodification class kotlin.jdk7.AutoCloseableKt {
  public static void closeFinally(java.lang.AutoCloseable,java.lang.Throwable);
}
-keep,allowaccessmodification class kotlin.jvm.JvmClassMappingKt {
  public static java.lang.Class getJavaClass(kotlin.reflect.KClass);
}
-keep @interface kotlin.jvm.JvmField {
}
-keep @interface kotlin.jvm.JvmInline {
}
-keep @interface kotlin.jvm.JvmName {
  public java.lang.String name();
}
-keep @interface kotlin.jvm.JvmOverloads {
}
-keep @interface kotlin.jvm.JvmStatic {
}
-keep,allowaccessmodification interface kotlin.jvm.functions.Function0 {
  public java.lang.Object invoke();
}
-keep,allowaccessmodification interface kotlin.jvm.functions.Function1 {
  public java.lang.Object invoke(java.lang.Object);
}
-keep,allowaccessmodification interface kotlin.jvm.functions.Function2 {
  public java.lang.Object invoke(java.lang.Object,java.lang.Object);
}
-keep,allowaccessmodification interface kotlin.jvm.functions.Function3 {
  public java.lang.Object invoke(java.lang.Object,java.lang.Object,java.lang.Object);
}
-keep,allowaccessmodification class kotlin.jvm.internal.CallableReference {
  java.lang.Object receiver;
}
-keep,allowaccessmodification class kotlin.jvm.internal.DefaultConstructorMarker {
}
-keep,allowaccessmodification class kotlin.jvm.internal.FunctionReferenceImpl {
  public <init>(int,java.lang.Class,java.lang.String,java.lang.String,int);
  public <init>(int,java.lang.Object,java.lang.Class,java.lang.String,java.lang.String,int);
}
-keep,allowaccessmodification class kotlin.jvm.internal.InlineMarker {
  public static void finallyEnd(int);
  public static void finallyStart(int);
}
-keep,allowaccessmodification class kotlin.jvm.internal.Intrinsics {
  public static boolean areEqual(java.lang.Object,java.lang.Object);
  public static void checkNotNull(java.lang.Object);
  public static void checkNotNull(java.lang.Object,java.lang.String);
  public static void checkNotNullExpressionValue(java.lang.Object,java.lang.String);
  public static void checkNotNullParameter(java.lang.Object,java.lang.String);
  public static void reifiedOperationMarker(int,java.lang.String);
  public static void throwUninitializedPropertyAccessException(java.lang.String);
}
-keep,allowaccessmodification class kotlin.jvm.internal.Intrinsics$Kotlin {
}
-keep,allowaccessmodification class kotlin.jvm.internal.Lambda {
  public <init>(int);
}
-keep,allowaccessmodification class kotlin.jvm.internal.PropertyReference1Impl {
  public <init>(java.lang.Class,java.lang.String,java.lang.String,int);
  public java.lang.Object get(java.lang.Object);
}
-keep,allowaccessmodification class kotlin.jvm.internal.Ref$BooleanRef {
  public <init>();
  boolean element;
}
-keep,allowaccessmodification class kotlin.jvm.internal.Ref$FloatRef {
  public <init>();
  float element;
}
-keep,allowaccessmodification class kotlin.jvm.internal.Ref$IntRef {
  public <init>();
  int element;
}
-keep,allowaccessmodification class kotlin.jvm.internal.Ref$ObjectRef {
  public <init>();
  java.lang.Object element;
}
-keep,allowaccessmodification class kotlin.jvm.internal.Reflection {
  public static kotlin.reflect.KClass getOrCreateKotlinClass(java.lang.Class);
}
-keep @interface kotlin.jvm.internal.SourceDebugExtension {
  public java.lang.String[] value();
}
-keep,allowaccessmodification class kotlin.jvm.internal.StringCompanionObject {
  kotlin.jvm.internal.StringCompanionObject INSTANCE;
}
-keep,allowaccessmodification interface kotlin.jvm.internal.markers.KMappedMarker {
}
-keep,allowaccessmodification class kotlin.math.MathKt {
}
-keep,allowaccessmodification class kotlin.math.MathKt__MathJVMKt {
  public static int roundToInt(float);
  public static long roundToLong(float);
}
-keep,allowaccessmodification class kotlin.ranges.IntRange {
}
-keep,allowaccessmodification class kotlin.ranges.LongProgression {
  public long getFirst();
  public long getLast();
}
-keep,allowaccessmodification class kotlin.ranges.LongRange {
  public <init>(long,long);
}
-keep,allowaccessmodification class kotlin.ranges.RangesKt {
}
-keep,allowaccessmodification class kotlin.ranges.RangesKt___RangesKt {
  public static int coerceAtLeast(int,int);
  public static kotlin.ranges.IntRange until(int,int);
}
-keep,allowaccessmodification interface kotlin.reflect.KClass {
  public java.lang.String getQualifiedName();
  public java.lang.String getSimpleName();
}
-keep,allowaccessmodification class kotlin.reflect.KClasses {
  public static java.lang.Object cast(kotlin.reflect.KClass,java.lang.Object);
  public static java.lang.Object safeCast(kotlin.reflect.KClass,java.lang.Object);
}
-keep,allowaccessmodification interface kotlin.sequences.Sequence {
  public java.util.Iterator iterator();
}
-keep,allowaccessmodification class kotlin.sequences.SequencesKt {
}
-keep,allowaccessmodification class kotlin.sequences.SequencesKt__SequencesKt {
  public static kotlin.sequences.Sequence asSequence(java.util.Iterator);
  public static kotlin.sequences.Sequence generateSequence(java.lang.Object,kotlin.jvm.functions.Function1);
}
-keep,allowaccessmodification class kotlin.sequences.SequencesKt___SequencesKt {
  public static kotlin.sequences.Sequence filter(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1);
  public static kotlin.sequences.Sequence filterNot(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1);
  public static java.lang.String joinToString$default(kotlin.sequences.Sequence,java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1,int,java.lang.Object);
  public static kotlin.sequences.Sequence map(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1);
  public static java.util.List toList(kotlin.sequences.Sequence);
  public static java.util.Set toSet(kotlin.sequences.Sequence);
  public static kotlin.sequences.Sequence zipWithNext(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2);
}
-keep,allowaccessmodification class kotlin.text.CharsKt {
}
-keep,allowaccessmodification class kotlin.text.CharsKt__CharKt {
  public static int digitToInt(char);
}
-keep,allowaccessmodification class kotlin.text.Charsets {
  java.nio.charset.Charset UTF_8;
}
-keep,allowaccessmodification class kotlin.text.Regex {
  public <init>(java.lang.String);
  public boolean containsMatchIn(java.lang.CharSequence);
  public boolean matches(java.lang.CharSequence);
  public java.lang.String replace(java.lang.CharSequence,java.lang.String);
}
-keep,allowaccessmodification class kotlin.text.StringsKt {
}
-keep,allowaccessmodification class kotlin.text.StringsKt__IndentKt {
  public static java.lang.String trimMargin$default(java.lang.String,java.lang.String,int,java.lang.Object);
}
-keep,allowaccessmodification class kotlin.text.StringsKt__StringsJVMKt {
  public static boolean endsWith$default(java.lang.String,java.lang.String,boolean,int,java.lang.Object);
  public static boolean equals(java.lang.String,java.lang.String,boolean);
  public static java.lang.String replace$default(java.lang.String,java.lang.String,java.lang.String,boolean,int,java.lang.Object);
  public static boolean startsWith$default(java.lang.String,java.lang.String,boolean,int,java.lang.Object);
  public static boolean startsWith(java.lang.String,java.lang.String,boolean);
}
-keep,allowaccessmodification class kotlin.text.StringsKt__StringsKt {
  public static boolean contains$default(java.lang.CharSequence,java.lang.CharSequence,boolean,int,java.lang.Object);
  public static boolean contains(java.lang.CharSequence,java.lang.CharSequence,boolean);
  public static boolean isBlank(java.lang.CharSequence);
  public static kotlin.sequences.Sequence lineSequence(java.lang.CharSequence);
  public static java.lang.String padStart(java.lang.String,int,char);
  public static java.util.List split$default(java.lang.CharSequence,char[],boolean,int,int,java.lang.Object);
  public static java.lang.String substringAfter$default(java.lang.String,char,java.lang.String,int,java.lang.Object);
  public static java.lang.String substringBefore$default(java.lang.String,char,java.lang.String,int,java.lang.Object);
  public static java.lang.CharSequence trim(java.lang.CharSequence);
  public static java.lang.CharSequence trimEnd(java.lang.CharSequence);
  public static java.lang.CharSequence trimStart(java.lang.CharSequence);
}
-keep,allowaccessmodification class kotlin.text.StringsKt___StringsKt {
  public static java.lang.String dropLast(java.lang.String,int);
}
-keep,allowaccessmodification class kotlin.time.AbstractLongTimeSource {
  public <init>(kotlin.time.DurationUnit);
  protected long read();
}
-keep,allowaccessmodification class kotlin.time.Duration {
  public static kotlin.time.Duration box-impl(long);
  public static long getInWholeMilliseconds-impl(long);
  public static boolean isNegative-impl(long);
  public static java.lang.String toString-impl(long);
  public long unbox-impl();
  kotlin.time.Duration$Companion Companion;
}
-keep,allowaccessmodification class kotlin.time.Duration$Companion {
  public long parse-UwyO8pc(java.lang.String);
}
-keep,allowaccessmodification class kotlin.time.DurationKt {
  public static long toDuration(int,kotlin.time.DurationUnit);
  public static long toDuration(long,kotlin.time.DurationUnit);
}
-keep enum kotlin.time.DurationUnit {
  kotlin.time.DurationUnit MILLISECONDS;
  kotlin.time.DurationUnit SECONDS;
}
-keep,allowaccessmodification interface kotlin.time.TimeSource$WithComparableMarks {
}
-keep,allowaccessmodification class kotlinx.coroutines.AbstractCoroutine {
  public <init>(kotlin.coroutines.CoroutineContext,boolean,boolean);
  public kotlin.coroutines.CoroutineContext getContext();
  public kotlin.coroutines.CoroutineContext getCoroutineContext();
  public void start(kotlinx.coroutines.CoroutineStart,java.lang.Object,kotlin.jvm.functions.Function2);
}
-keep,allowaccessmodification class kotlinx.coroutines.AwaitKt {
  public static java.lang.Object awaitAll(java.util.Collection,kotlin.coroutines.Continuation);
  public static java.lang.Object awaitAll(kotlinx.coroutines.Deferred[],kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class kotlinx.coroutines.BuildersKt {
  public static kotlinx.coroutines.Deferred async$default(kotlinx.coroutines.CoroutineScope,kotlin.coroutines.CoroutineContext,kotlinx.coroutines.CoroutineStart,kotlin.jvm.functions.Function2,int,java.lang.Object);
  public static kotlinx.coroutines.Job launch$default(kotlinx.coroutines.CoroutineScope,kotlin.coroutines.CoroutineContext,kotlinx.coroutines.CoroutineStart,kotlin.jvm.functions.Function2,int,java.lang.Object);
  public static java.lang.Object runBlocking$default(kotlin.coroutines.CoroutineContext,kotlin.jvm.functions.Function2,int,java.lang.Object);
  public static java.lang.Object runBlocking(kotlin.coroutines.CoroutineContext,kotlin.jvm.functions.Function2);
  public static java.lang.Object withContext(kotlin.coroutines.CoroutineContext,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface kotlinx.coroutines.CancellableContinuation {
  public void invokeOnCancellation(kotlin.jvm.functions.Function1);
  public boolean isActive();
  public void resume(java.lang.Object,kotlin.jvm.functions.Function1);
  public void resumeUndispatched(kotlinx.coroutines.CoroutineDispatcher,java.lang.Object);
}
-keep,allowaccessmodification class kotlinx.coroutines.CancellableContinuationImpl {
  public <init>(kotlin.coroutines.Continuation,int);
  public java.lang.Object getResult();
  public void initCancellability();
}
-keep,allowaccessmodification class kotlinx.coroutines.CancellableContinuationKt {
  public static void disposeOnCancellation(kotlinx.coroutines.CancellableContinuation,kotlinx.coroutines.DisposableHandle);
}
-keep,allowaccessmodification interface kotlinx.coroutines.CompletableJob {
  public boolean complete();
}
-keep,allowaccessmodification class kotlinx.coroutines.CoroutineDispatcher {
  public <init>();
  public void dispatch(kotlin.coroutines.CoroutineContext,java.lang.Runnable);
  public void dispatchYield(kotlin.coroutines.CoroutineContext,java.lang.Runnable);
  public kotlin.coroutines.Continuation interceptContinuation(kotlin.coroutines.Continuation);
  public boolean isDispatchNeeded(kotlin.coroutines.CoroutineContext);
  public java.lang.String toString();
}
-keep,allowaccessmodification interface kotlinx.coroutines.CoroutineExceptionHandler {
  public void handleException(kotlin.coroutines.CoroutineContext,java.lang.Throwable);
  kotlinx.coroutines.CoroutineExceptionHandler$Key Key;
}
-keep,allowaccessmodification class kotlinx.coroutines.CoroutineExceptionHandler$DefaultImpls {
  public static java.lang.Object fold(kotlinx.coroutines.CoroutineExceptionHandler,java.lang.Object,kotlin.jvm.functions.Function2);
  public static kotlin.coroutines.CoroutineContext$Element get(kotlinx.coroutines.CoroutineExceptionHandler,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext minusKey(kotlinx.coroutines.CoroutineExceptionHandler,kotlin.coroutines.CoroutineContext$Key);
  public static kotlin.coroutines.CoroutineContext plus(kotlinx.coroutines.CoroutineExceptionHandler,kotlin.coroutines.CoroutineContext);
}
-keep,allowaccessmodification class kotlinx.coroutines.CoroutineExceptionHandler$Key {
}
-keep,allowaccessmodification class kotlinx.coroutines.CoroutineExceptionHandlerKt {
  public static void handleCoroutineException(kotlin.coroutines.CoroutineContext,java.lang.Throwable);
}
-keep,allowaccessmodification class kotlinx.coroutines.CoroutineName {
  public <init>(java.lang.String);
}
-keep,allowaccessmodification interface kotlinx.coroutines.CoroutineScope {
  public kotlin.coroutines.CoroutineContext getCoroutineContext();
}
-keep,allowaccessmodification class kotlinx.coroutines.CoroutineScopeKt {
  public static kotlinx.coroutines.CoroutineScope CoroutineScope(kotlin.coroutines.CoroutineContext);
  public static void cancel$default(kotlinx.coroutines.CoroutineScope,java.util.concurrent.CancellationException,int,java.lang.Object);
  public static boolean isActive(kotlinx.coroutines.CoroutineScope);
}
-keep enum kotlinx.coroutines.CoroutineStart {
  kotlinx.coroutines.CoroutineStart UNDISPATCHED;
}
-keep,allowaccessmodification class kotlinx.coroutines.DebugKt {
  public static boolean getRECOVER_STACK_TRACES();
}
-keep,allowaccessmodification class kotlinx.coroutines.DefaultExecutorKt {
  public static kotlinx.coroutines.Delay getDefaultDelay();
}
-keep,allowaccessmodification interface kotlinx.coroutines.Deferred {
  public java.lang.Throwable getCompletionExceptionOrNull();
}
-keep,allowaccessmodification interface kotlinx.coroutines.Delay {
  public java.lang.Object delay(long,kotlin.coroutines.Continuation);
  public kotlinx.coroutines.DisposableHandle invokeOnTimeout(long,java.lang.Runnable,kotlin.coroutines.CoroutineContext);
  public void scheduleResumeAfterDelay(long,kotlinx.coroutines.CancellableContinuation);
}
-keep,allowaccessmodification class kotlinx.coroutines.Delay$DefaultImpls {
  public static java.lang.Object delay(kotlinx.coroutines.Delay,long,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class kotlinx.coroutines.DelayKt {
  public static java.lang.Object delay(long,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface kotlinx.coroutines.DelayWithTimeoutDiagnostics {
  public java.lang.String timeoutMessage-LRDsOJo(long);
}
-keep,allowaccessmodification class kotlinx.coroutines.Dispatchers {
  public static kotlinx.coroutines.CoroutineDispatcher getDefault();
  public static kotlinx.coroutines.CoroutineDispatcher getIO();
  public static kotlinx.coroutines.MainCoroutineDispatcher getMain();
}
-keep,allowaccessmodification interface kotlinx.coroutines.DisposableHandle {
  public void dispose();
}
-keep,allowaccessmodification class kotlinx.coroutines.ExecutorsKt {
  public static kotlinx.coroutines.CoroutineDispatcher from(java.util.concurrent.Executor);
}
-keep @interface kotlinx.coroutines.ExperimentalCoroutinesApi {
}
-keep,allowaccessmodification interface kotlinx.coroutines.Job {
  public void cancel(java.util.concurrent.CancellationException);
  public kotlin.sequences.Sequence getChildren();
  public boolean isActive();
  public boolean isCompleted();
  kotlinx.coroutines.Job$Key Key;
}
-keep,allowaccessmodification class kotlinx.coroutines.Job$DefaultImpls {
  public static kotlinx.coroutines.DisposableHandle invokeOnCompletion$default(kotlinx.coroutines.Job,boolean,boolean,kotlin.jvm.functions.Function1,int,java.lang.Object);
}
-keep,allowaccessmodification class kotlinx.coroutines.Job$Key {
}
-keep,allowaccessmodification class kotlinx.coroutines.JobImpl {
  public <init>(kotlinx.coroutines.Job);
}
-keep,allowaccessmodification class kotlinx.coroutines.JobKt {
  public static kotlinx.coroutines.CompletableJob Job$default(kotlinx.coroutines.Job,int,java.lang.Object);
  public static java.lang.Object cancelAndJoin(kotlinx.coroutines.Job,kotlin.coroutines.Continuation);
  public static kotlinx.coroutines.Job getJob(kotlin.coroutines.CoroutineContext);
}
-keep,allowaccessmodification class kotlinx.coroutines.JobSupport {
  public boolean childCancelled(java.lang.Throwable);
  public kotlin.sequences.Sequence getChildren();
  protected java.lang.Throwable getCompletionCause();
  public java.lang.Throwable getCompletionExceptionOrNull();
  public kotlinx.coroutines.selects.SelectClause0 getOnJoin();
  public boolean isCancelled();
  public boolean isCompleted();
  public java.lang.Object join(kotlin.coroutines.Continuation);
  public java.lang.String toString();
}
-keep,allowaccessmodification class kotlinx.coroutines.MainCoroutineDispatcher {
  public <init>();
  public kotlinx.coroutines.MainCoroutineDispatcher getImmediate();
}
-keep,allowaccessmodification class kotlinx.coroutines.SupervisorKt {
  public static kotlinx.coroutines.CompletableJob SupervisorJob$default(kotlinx.coroutines.Job,int,java.lang.Object);
}
-keep,allowaccessmodification class kotlinx.coroutines.TimeoutCancellationException {
}
-keep,allowaccessmodification class kotlinx.coroutines.TimeoutKt {
  public static java.lang.Object withTimeout(long,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation);
  public static java.lang.Object withTimeout-KLykuaI(long,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification class kotlinx.coroutines.YieldContext {
  kotlinx.coroutines.YieldContext$Key Key;
  boolean dispatcherWasUnconfined;
}
-keep,allowaccessmodification class kotlinx.coroutines.YieldContext$Key {
}
-keep,allowaccessmodification class kotlinx.coroutines.YieldKt {
  public static java.lang.Object yield(kotlin.coroutines.Continuation);
}
-keep enum kotlinx.coroutines.channels.BufferOverflow {
}
-keep,allowaccessmodification interface kotlinx.coroutines.channels.Channel {
}
-keep,allowaccessmodification class kotlinx.coroutines.channels.ChannelKt {
  public static kotlinx.coroutines.channels.Channel Channel$default(int,kotlinx.coroutines.channels.BufferOverflow,kotlin.jvm.functions.Function1,int,java.lang.Object);
}
-keep,allowaccessmodification interface kotlinx.coroutines.channels.ReceiveChannel {
  public kotlinx.coroutines.selects.SelectClause1 getOnReceive();
  public java.lang.Object receive(kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface kotlinx.coroutines.channels.SendChannel {
  public java.lang.Object trySend-JP2dKIU(java.lang.Object);
}
-keep,allowaccessmodification class kotlinx.coroutines.debug.internal.DebugProbesImpl {
  public void dumpCoroutines(java.io.PrintStream);
  public void install$kotlinx_coroutines_core();
  public boolean isInstalled$kotlinx_coroutines_debug();
  public void uninstall$kotlinx_coroutines_core();
  kotlinx.coroutines.debug.internal.DebugProbesImpl INSTANCE;
}
-keep,allowaccessmodification interface kotlinx.coroutines.flow.Flow {
}
-keep,allowaccessmodification class kotlinx.coroutines.flow.FlowKt {
  public static java.lang.Object first(kotlinx.coroutines.flow.Flow,kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface kotlinx.coroutines.flow.StateFlow {
  public java.lang.Object getValue();
}
-keep,allowaccessmodification class kotlinx.coroutines.internal.CoroutineExceptionHandlerImplKt {
  public static void ensurePlatformExceptionHandlerLoaded(kotlinx.coroutines.CoroutineExceptionHandler);
}
-keep,allowaccessmodification class kotlinx.coroutines.internal.CoroutineExceptionHandlerImpl_commonKt {
  public static void handleUncaughtCoroutineException(kotlin.coroutines.CoroutineContext,java.lang.Throwable);
}
-keep,allowaccessmodification class kotlinx.coroutines.internal.ExceptionSuccessfullyProcessed {
  kotlinx.coroutines.internal.ExceptionSuccessfullyProcessed INSTANCE;
}
-keep,allowaccessmodification interface kotlinx.coroutines.internal.MainDispatcherFactory {
  public kotlinx.coroutines.MainCoroutineDispatcher createDispatcher(java.util.List);
  public int getLoadPriority();
  public java.lang.String hintOnError();
}
-keep,allowaccessmodification class kotlinx.coroutines.internal.MainDispatcherFactory$DefaultImpls {
  public static java.lang.String hintOnError(kotlinx.coroutines.internal.MainDispatcherFactory);
}
-keep,allowaccessmodification class kotlinx.coroutines.internal.MainDispatchersKt {
  public static boolean isMissing(kotlinx.coroutines.MainCoroutineDispatcher);
  public static kotlinx.coroutines.MainCoroutineDispatcher tryCreateDispatcher(kotlinx.coroutines.internal.MainDispatcherFactory,java.util.List);
}
-keep,allowaccessmodification class kotlinx.coroutines.internal.MissingMainCoroutineDispatcherFactory {
  kotlinx.coroutines.internal.MissingMainCoroutineDispatcherFactory INSTANCE;
}
-keep,allowaccessmodification class kotlinx.coroutines.internal.StackTraceRecoveryKt {
  public static java.lang.Throwable unwrapImpl(java.lang.Throwable);
}
-keep,allowaccessmodification class kotlinx.coroutines.internal.ThreadSafeHeap {
  public <init>();
  public void addLast(kotlinx.coroutines.internal.ThreadSafeHeapNode);
  public kotlinx.coroutines.internal.ThreadSafeHeapNode find(kotlin.jvm.functions.Function1);
  public kotlinx.coroutines.internal.ThreadSafeHeapNode firstImpl();
  public boolean isEmpty();
  public boolean remove(kotlinx.coroutines.internal.ThreadSafeHeapNode);
  public kotlinx.coroutines.internal.ThreadSafeHeapNode removeAtImpl(int);
  public kotlinx.coroutines.internal.ThreadSafeHeapNode removeFirstOrNull();
}
-keep,allowaccessmodification interface kotlinx.coroutines.internal.ThreadSafeHeapNode {
  public kotlinx.coroutines.internal.ThreadSafeHeap getHeap();
  public int getIndex();
  public void setHeap(kotlinx.coroutines.internal.ThreadSafeHeap);
  public void setIndex(int);
}
-keep,allowaccessmodification class kotlinx.coroutines.selects.OnTimeoutKt {
  public static void onTimeout-8Mi8wO0(kotlinx.coroutines.selects.SelectBuilder,long,kotlin.jvm.functions.Function1);
}
-keep,allowaccessmodification interface kotlinx.coroutines.selects.SelectBuilder {
  public void invoke(kotlinx.coroutines.selects.SelectClause0,kotlin.jvm.functions.Function1);
  public void invoke(kotlinx.coroutines.selects.SelectClause1,kotlin.jvm.functions.Function2);
}
-keep,allowaccessmodification interface kotlinx.coroutines.selects.SelectClause0 {
}
-keep,allowaccessmodification interface kotlinx.coroutines.selects.SelectClause1 {
}
-keep,allowaccessmodification class kotlinx.coroutines.selects.SelectImplementation {
  public <init>(kotlin.coroutines.CoroutineContext);
  public java.lang.Object doSelect(kotlin.coroutines.Continuation);
}
-keep,allowaccessmodification interface kotlinx.serialization.DeserializationStrategy {
  public java.lang.Object deserialize(kotlinx.serialization.encoding.Decoder);
}
-keep,allowaccessmodification interface kotlinx.serialization.KSerializer {
  public kotlinx.serialization.descriptors.SerialDescriptor getDescriptor();
}
-keep,allowaccessmodification class kotlinx.serialization.SealedClassSerializer {
  public <init>(java.lang.String,kotlin.reflect.KClass,kotlin.reflect.KClass[],kotlinx.serialization.KSerializer[],java.lang.annotation.Annotation[]);
}
-keep @interface kotlinx.serialization.SerialName {
  public java.lang.String value();
}
-keep @interface kotlinx.serialization.Serializable {
}
-keep,allowaccessmodification interface kotlinx.serialization.SerializationStrategy {
  public void serialize(kotlinx.serialization.encoding.Encoder,java.lang.Object);
}
-keep,allowaccessmodification class kotlinx.serialization.UnknownFieldException {
  public <init>(int);
}
-keep,allowaccessmodification class kotlinx.serialization.builtins.BuiltinSerializersKt {
  public static kotlinx.serialization.KSerializer getNullable(kotlinx.serialization.KSerializer);
}
-keep,allowaccessmodification interface kotlinx.serialization.descriptors.SerialDescriptor {
}
-keep,allowaccessmodification interface kotlinx.serialization.encoding.CompositeDecoder {
  public boolean decodeBooleanElement(kotlinx.serialization.descriptors.SerialDescriptor,int);
  public int decodeElementIndex(kotlinx.serialization.descriptors.SerialDescriptor);
  public int decodeIntElement(kotlinx.serialization.descriptors.SerialDescriptor,int);
  public java.lang.Object decodeNullableSerializableElement(kotlinx.serialization.descriptors.SerialDescriptor,int,kotlinx.serialization.DeserializationStrategy,java.lang.Object);
  public boolean decodeSequentially();
  public java.lang.Object decodeSerializableElement(kotlinx.serialization.descriptors.SerialDescriptor,int,kotlinx.serialization.DeserializationStrategy,java.lang.Object);
  public java.lang.String decodeStringElement(kotlinx.serialization.descriptors.SerialDescriptor,int);
  public void endStructure(kotlinx.serialization.descriptors.SerialDescriptor);
}
-keep,allowaccessmodification interface kotlinx.serialization.encoding.CompositeEncoder {
  public void encodeBooleanElement(kotlinx.serialization.descriptors.SerialDescriptor,int,boolean);
  public void encodeIntElement(kotlinx.serialization.descriptors.SerialDescriptor,int,int);
  public void encodeNullableSerializableElement(kotlinx.serialization.descriptors.SerialDescriptor,int,kotlinx.serialization.SerializationStrategy,java.lang.Object);
  public void encodeSerializableElement(kotlinx.serialization.descriptors.SerialDescriptor,int,kotlinx.serialization.SerializationStrategy,java.lang.Object);
  public void encodeStringElement(kotlinx.serialization.descriptors.SerialDescriptor,int,java.lang.String);
  public void endStructure(kotlinx.serialization.descriptors.SerialDescriptor);
  public boolean shouldEncodeElementDefault(kotlinx.serialization.descriptors.SerialDescriptor,int);
}
-keep,allowaccessmodification interface kotlinx.serialization.encoding.Decoder {
  public kotlinx.serialization.encoding.CompositeDecoder beginStructure(kotlinx.serialization.descriptors.SerialDescriptor);
}
-keep,allowaccessmodification interface kotlinx.serialization.encoding.Encoder {
  public kotlinx.serialization.encoding.CompositeEncoder beginStructure(kotlinx.serialization.descriptors.SerialDescriptor);
}
-keep,allowaccessmodification class kotlinx.serialization.internal.ArrayListSerializer {
  public <init>(kotlinx.serialization.KSerializer);
}
-keep,allowaccessmodification class kotlinx.serialization.internal.BooleanSerializer {
  kotlinx.serialization.internal.BooleanSerializer INSTANCE;
}
-keep,allowaccessmodification interface kotlinx.serialization.internal.GeneratedSerializer {
  public kotlinx.serialization.KSerializer[] childSerializers();
  public kotlinx.serialization.KSerializer[] typeParametersSerializers();
}
-keep,allowaccessmodification class kotlinx.serialization.internal.GeneratedSerializer$DefaultImpls {
  public static kotlinx.serialization.KSerializer[] typeParametersSerializers(kotlinx.serialization.internal.GeneratedSerializer);
}
-keep,allowaccessmodification class kotlinx.serialization.internal.IntSerializer {
  kotlinx.serialization.internal.IntSerializer INSTANCE;
}
-keep,allowaccessmodification class kotlinx.serialization.internal.PluginExceptionsKt {
  public static void throwMissingFieldException(int,int,kotlinx.serialization.descriptors.SerialDescriptor);
}
-keep,allowaccessmodification class kotlinx.serialization.internal.PluginGeneratedSerialDescriptor {
  public <init>(java.lang.String,kotlinx.serialization.internal.GeneratedSerializer,int);
  public void addElement(java.lang.String,boolean);
}
-keep,allowaccessmodification class kotlinx.serialization.internal.SerializationConstructorMarker {
}
-keep,allowaccessmodification class kotlinx.serialization.internal.StringSerializer {
  kotlinx.serialization.internal.StringSerializer INSTANCE;
}
-keep enum kotlinx.serialization.json.ClassDiscriminatorMode {
  kotlinx.serialization.json.ClassDiscriminatorMode NONE;
}
-keep,allowaccessmodification class kotlinx.serialization.json.Json {
  public java.lang.Object decodeFromString(kotlinx.serialization.DeserializationStrategy,java.lang.String);
  public kotlinx.serialization.modules.SerializersModule getSerializersModule();
}
-keep,allowaccessmodification class kotlinx.serialization.json.JsonBuilder {
  public void setClassDiscriminatorMode(kotlinx.serialization.json.ClassDiscriminatorMode);
  public void setPrettyPrint(boolean);
  public void setPrettyPrintIndent(java.lang.String);
  public void setSerializersModule(kotlinx.serialization.modules.SerializersModule);
}
-keep,allowaccessmodification class kotlinx.serialization.json.JsonContentPolymorphicSerializer {
  public <init>(kotlin.reflect.KClass);
  protected kotlinx.serialization.DeserializationStrategy selectDeserializer(kotlinx.serialization.json.JsonElement);
}
-keep,allowaccessmodification class kotlinx.serialization.json.JsonElement {
}
-keep,allowaccessmodification class kotlinx.serialization.json.JsonElementKt {
  public static kotlinx.serialization.json.JsonObject getJsonObject(kotlinx.serialization.json.JsonElement);
}
-keep,allowaccessmodification class kotlinx.serialization.json.JsonKt {
  public static kotlinx.serialization.json.Json Json$default(kotlinx.serialization.json.Json,kotlin.jvm.functions.Function1,int,java.lang.Object);
}
-keep,allowaccessmodification class kotlinx.serialization.json.JsonObject {
}
-keep,allowaccessmodification class kotlinx.serialization.json.JvmStreamsKt {
  public static void encodeToStream(kotlinx.serialization.json.Json,kotlinx.serialization.SerializationStrategy,java.lang.Object,java.io.OutputStream);
}
-keep,allowaccessmodification class kotlinx.serialization.modules.SerializersModule {
}
-keep,allowaccessmodification class kotlinx.serialization.modules.SerializersModuleBuilder {
  public <init>();
  public kotlinx.serialization.modules.SerializersModule build();
  public void polymorphicDefaultDeserializer(kotlin.reflect.KClass,kotlin.jvm.functions.Function1);
}
-keep,allowaccessmodification class okhttp3.HttpUrl {
  public java.lang.String encodedPath();
}
-keep,allowaccessmodification interface okhttp3.Interceptor {
  public okhttp3.Response intercept(okhttp3.Interceptor$Chain);
}
-keep,allowaccessmodification interface okhttp3.Interceptor$Chain {
  public okhttp3.Response proceed(okhttp3.Request);
  public okhttp3.Request request();
}
-keep,allowaccessmodification class okhttp3.OkHttpClient {
}
-keep,allowaccessmodification class okhttp3.OkHttpClient$Builder {
  public <init>();
  public okhttp3.OkHttpClient$Builder addNetworkInterceptor(okhttp3.Interceptor);
  public okhttp3.OkHttpClient build();
  public okhttp3.OkHttpClient$Builder readTimeout(long,java.util.concurrent.TimeUnit);
}
-keep,allowaccessmodification class okhttp3.Request {
  public java.lang.String header(java.lang.String);
  public java.lang.String method();
  public okhttp3.HttpUrl url();
}
-keep,allowaccessmodification class okhttp3.Response {
  public void close();
  public int code();
  public static java.lang.String header$default(okhttp3.Response,java.lang.String,java.lang.String,int,java.lang.Object);
  public boolean isSuccessful();
}
-keep @interface org.jetbrains.annotations.NotNull {
}
-keep @interface org.jetbrains.annotations.Nullable {
}
-keep,allowaccessmodification class org.tensorflow.lite.Interpreter {
  public <init>(java.io.File);
  public java.lang.String[] getSignatureKeys();
}
