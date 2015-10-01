package com.coffeelint.inspection;

import com.coffeelint.CoffeeLintBundle;
import com.coffeelint.CoffeeLintProjectComponent;
import com.coffeelint.cli.CoffeeLint;
import com.coffeelint.cli.CoffeeLintRunner;
import com.coffeelint.cli.LintResult;
import com.coffeelint.config.CoffeeLintConfigFileListener;
import com.coffeelint.config.CoffeeLintConfigFileUtil;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.wix.ActualFile;
import com.wix.ActualFile2;
import com.wix.ThreadLocalActualFile;
import com.wix.ThreadLocalTempActualFile;
import com.wix.annotator.ExternalLintAnnotationInput;
import com.wix.annotator.ExternalLintAnnotationResult;
import com.wix.annotator.InspectionUtil;
import com.wix.utils.Delayer;
import com.wix.utils.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author idok
 */
public class CoffeeLintExternalAnnotator extends ExternalAnnotator<ExternalLintAnnotationInput, ExternalLintAnnotationResult<LintResult>> {

    public static final CoffeeLintExternalAnnotator INSTANCE = new CoffeeLintExternalAnnotator();
    private static final Logger LOG = Logger.getInstance(CoffeeLintBundle.LOG_ID);
    private static final String MESSAGE_PREFIX = "CoffeeLint: ";
    private static final Key<ThreadLocalTempActualFile> COFFEE_LINT_TEMP_FILE = Key.create("COFFEE_LINT_TEMP_FILE");

    @Nullable
    @Override
    public ExternalLintAnnotationInput collectInformation(@NotNull PsiFile file) {
        return collectInformation(file, null);
    }

    @Nullable
    @Override
    public ExternalLintAnnotationInput collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        return collectInformation(file, editor);
    }

    @NotNull
    public static HighlightDisplayKey getHighlightDisplayKeyByClass() {
        String id = "CoffeeLint";
        HighlightDisplayKey key = HighlightDisplayKey.find(id);
        if (key == null) {
            key = new HighlightDisplayKey(id, id);
        }
        return key;
    }

    @Override
    public void apply(@NotNull PsiFile file, ExternalLintAnnotationResult<LintResult> annotationResult, @NotNull AnnotationHolder holder) {
        if (annotationResult == null) {
            return;
        }
        InspectionProjectProfileManager inspectionProjectProfileManager = InspectionProjectProfileManager.getInstance(file.getProject());
        SeverityRegistrar severityRegistrar = inspectionProjectProfileManager.getSeverityRegistrar();
//        HighlightDisplayKey inspectionKey = getHighlightDisplayKeyByClass();
//        HighlightSeverity severity = InspectionUtil.getSeverity(inspectionProjectProfileManager, inspectionKey, file);
        EditorColorsScheme colorsScheme = annotationResult.input.colorsScheme;

        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) {
            return;
        }
        CoffeeLintProjectComponent component = annotationResult.input.project.getComponent(CoffeeLintProjectComponent.class);
        for (CoffeeLint.Issue warn : annotationResult.result.coffeeLint.file.errors) {
            HighlightSeverity severity = getHighlightSeverity(warn, component.treatAsWarnings);
            TextAttributes forcedTextAttributes = InspectionUtil.getTextAttributes(colorsScheme, severityRegistrar, severity);
            Annotation annotation = createAnnotation(holder, file, document, warn, severity, forcedTextAttributes, false);
//            if (annotation != null) {
//                int offset = StringUtil.lineColToOffset(document.getText(), warn.line - 1, warn.column);
//                PsiElement lit = PsiUtil.getElementAtOffset(file, offset);
//                BaseActionFix actionFix = Fixes.getFixForRule(warn.rule, lit);
//                if (actionFix != null) {
//                    annotation.registerFix(actionFix, null, inspectionKey);
//                }
//                annotation.registerFix(new SuppressActionFix(warn.rule, lit), null, inspectionKey);
//            }
        }
    }

    private static HighlightSeverity getHighlightSeverity(CoffeeLint.Issue warn, boolean treatAsWarnings) {
        if (treatAsWarnings) {
            return HighlightSeverity.WARNING;
        }
        return warn.severity.equals("error") ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
    }

    @Nullable
    private static Annotation createAnnotation(@NotNull AnnotationHolder holder, @NotNull PsiFile file, @NotNull Document document, @NotNull CoffeeLint.Issue warn,
                                               @NotNull HighlightSeverity severity, @Nullable TextAttributes forcedTextAttributes,
                                               boolean showErrorOnWholeLine) {
        int line = warn.line - 1;
//        int column = warn.column /*- 1*/;

        if (line < 0 || line >= document.getLineCount()) {
            return null;
        }
        int lineEndOffset = document.getLineEndOffset(line);
        int lineStartOffset = document.getLineStartOffset(line);

//        int errorLineStartOffset = StringUtil.lineColToOffset(document.getCharsSequence(),  line, column);
//        int errorLineStartOffset = PsiUtil.calcErrorStartOffsetInDocument(document, lineStartOffset, lineEndOffset, column, tab);

//        if (errorLineStartOffset == -1) {
//            return null;
//        }
//        PsiElement element = file.findElementAt(errorLineStartOffset);
        TextRange range;
//        if (showErrorOnWholeLine) {
            range = new TextRange(lineStartOffset, lineEndOffset);
//        } else {
////            int offset = StringUtil.lineColToOffset(document.getText(), warn.line - 1, warn.column);
//            PsiElement lit = PsiUtil.getElementAtOffset(file, errorLineStartOffset);
//            range = lit.getTextRange();
//            range = new TextRange(errorLineStartOffset, errorLineStartOffset + 1);
//        }

        Annotation annotation = InspectionUtil.createAnnotation(holder, severity, forcedTextAttributes, range, MESSAGE_PREFIX + warn.message.trim() + " (" + warn.source + ')');
//        if (annotation != null) {
//            annotation.setAfterEndOfLine(errorLineStartOffset == lineEndOffset);
//        }
        return annotation;
    }

    @Nullable
    private static ExternalLintAnnotationInput collectInformation(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        if (psiFile.getContext() != null || !CoffeeLintConfigFileUtil.isCoffeeScriptFile(psiFile)) {
            return null;
        }
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
            return null;
        }
        if (psiFile.getViewProvider() instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
            return null;
        }
        Project project = psiFile.getProject();
        CoffeeLintProjectComponent component = project.getComponent(CoffeeLintProjectComponent.class);
        if (!component.isSettingsValid() || !component.isEnabled()) {
            return null;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null) {
            return null;
        }
        String fileContent = document.getText();
        if (StringUtil.isEmptyOrSpaces(fileContent)) {
            return null;
        }
        EditorColorsScheme colorsScheme = editor != null ? editor.getColorsScheme() : null;
//        tabSize = getTabSize(editor);
//        tabSize = 4;
        return new ExternalLintAnnotationInput(project, psiFile, fileContent, colorsScheme);
    }

    @Nullable
    @Override
    public ExternalLintAnnotationResult<LintResult> doAnnotate(ExternalLintAnnotationInput collectedInfo) {
        ActualFile2 actualCodeFile = null;
        try {
            PsiFile file = collectedInfo.psiFile;
            if (!CoffeeLintConfigFileUtil.isCoffeeScriptFile(file)) return null;
            CoffeeLintProjectComponent component = file.getProject().getComponent(CoffeeLintProjectComponent.class);
            if (!component.isSettingsValid() || !component.isEnabled()) {
                return null;
            }

            CoffeeLintConfigFileListener.start(collectedInfo.project);
            String relativeFile;
            actualCodeFile = ActualFile2.getOrCreateActualFile(COFFEE_LINT_TEMP_FILE, file, collectedInfo.fileContent);
            if (actualCodeFile == null || actualCodeFile.getActualFile() == null) {
                return null;
            }
            relativeFile = FileUtils.makeRelative(new File(file.getProject().getBasePath()), actualCodeFile.getActualFile());
            if (relativeFile == null) {
                LOG.error("Error running CoffeeLint inspection: relative file path is null");
                return null;
            }
            LintResult result = CoffeeLintRunner.lint(file.getProject().getBasePath(), relativeFile, component.nodeInterpreter, component.lintExecutable, component.configFile, component.customRulesPath, component.extensions);

            actualCodeFile.deleteTemp();
            if (StringUtils.isNotEmpty(result.errorOutput)) {
                component.showInfoNotification(result.errorOutput, NotificationType.WARNING);
                return null;
            }
            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document == null) {
                component.showInfoNotification("Error running CoffeeLint inspection: Could not get document for file " + file.getName(), NotificationType.WARNING);
                LOG.error("Could not get document for file " + file.getName());
                return null;
            }
            return new ExternalLintAnnotationResult<LintResult>(collectedInfo, result);
        } catch (Exception e) {
            LOG.error("Error running CoffeeLint inspection: ", e);
            showNotification("Error running CoffeeLint inspection: " + e.getMessage(), NotificationType.ERROR);
        } finally {
            if (actualCodeFile != null) {
                actualCodeFile.deleteTemp();
            }
        }
        return null;
    }

    private final Delayer delayer = new Delayer(TimeUnit.SECONDS.toMillis(5L));

    public void showNotification(String content, NotificationType type) {
        if (delayer.should()) {
            CoffeeLintProjectComponent.showNotification(content, type);
            delayer.done();
        }
    }
}