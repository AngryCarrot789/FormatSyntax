package reghzy.formatsyntax;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.ui.JBColor;

import java.util.HashSet;

public class FormatSyntaxPlugin implements Annotator {
    public static final HashSet<String> ALLOWED_DESCS = new HashSet<>();

    static {
        ALLOWED_DESCS.add("splice(String, Appender)");
        ALLOWED_DESCS.add("interpolate(String, Function)");
        ALLOWED_DESCS.add("interpolate(String, Function<String, ?>)");
        ALLOWED_DESCS.add("interpolate(String, Function<String, Object>)");
        ALLOWED_DESCS.add("interpolate(String, Map)");
        ALLOWED_DESCS.add("interpolate(String, Map<String, ?>)");
        ALLOWED_DESCS.add("interpolate(String, Map<String, Object>)");
        ALLOWED_DESCS.add("format(String)");
        ALLOWED_DESCS.add("format(String, Object...)");
    }

    private static final TextAttributesKey FORMAT_KEY;
    private static final TextAttributesKey STRING;

    static {
        TextAttributes colour = DefaultLanguageHighlighterColors.KEYWORD.getDefaultAttributes().clone();
        colour.setForegroundColor(JBColor.red);
        FORMAT_KEY = TextAttributesKey.createTextAttributesKey("FORMAT_KEY", colour);
        STRING = TextAttributesKey.createTextAttributesKey("STRING", DefaultLanguageHighlighterColors.STRING);
    }

    @Override
    public void annotate(PsiElement element, AnnotationHolder holder) {
        if (element instanceof PsiLiteralExpression) {
            PsiLiteralExpression literal = (PsiLiteralExpression) element;
            if (literal.getValue() instanceof String) {
                try {
                    if (!isValidFormatMethod(literal)) {
                        return;
                    }
                }
                catch (Throwable throwable) {
                    return;
                }

                int j, i, k;
                String format = literal.getText();
                if (format == null || (i = format.indexOf('{', j = 0)) == -1)
                    return;

                // 191-194, 194-197, 197-200

                int offset = literal.getTextRange().getStartOffset();
                do {
                    if (i == 0 || format.charAt(i - 1) != '\\') { // check escape char
                        if (j != i) {
                            holder.createInfoAnnotation(new TextRange(offset + j, offset + i), null).setTextAttributes(STRING);
                        }

                        if ((k = format.indexOf('}', i)) != -1) { // get closing char index
                            j = k + 1; // set last char to after closing char
                            holder.createInfoAnnotation(new TextRange(offset + i, offset + j), null).setTextAttributes(FORMAT_KEY);
                            i = k; // set next search index to the '}' char
                        }
                        else {
                            j = i; // set last char to the last '{' char
                        }
                    }
                    else { // remove escape char
                        holder.createInfoAnnotation(new TextRange(offset + j, offset + (i - 1)), null).setTextAttributes(STRING);
                        j = i; // set last index to the '{' char, which was originally escaped
                    }
                } while ((i = format.indexOf('{', i + 1)) != -1);
                holder.createInfoAnnotation(new TextRange(offset + j, offset + format.length()), null).setTextAttributes(STRING);
            }
        }
    }

    public static boolean appendClassName(StringBuilder sb, JvmParameter param) {
        JvmType type = param.getType();
        String className;
        if (type instanceof PsiClassReferenceType) {
            PsiClassReferenceType ref = (PsiClassReferenceType) type;
            className = ref.getClassName();
        }
        else if (type instanceof PsiType) {
            className = ((PsiType) type).getPresentableText();
        }
        else {
            return false;
        }

        sb.append(className);
        return true;
    }

    public static boolean isValidFormatMethod(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiExpressionList) {
            PsiElement methodCall = parent.getParent();
            if (methodCall instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) methodCall;
                PsiMethod method = methodCallExpression.resolveMethod();
                if (method == null) {
                    return false;
                }

                JvmParameter[] params = method.getParameters();
                if (params.length == 0) {
                    return false;
                }

                StringBuilder sb = new StringBuilder();
                sb.append(method.getName()).append('(');
                if (!appendClassName(sb, params[0])) {
                    return false;
                }

                for (int i = 1; i < params.length; i++) {
                    sb.append(", ");
                    if (!appendClassName(sb, params[i]));
                }
                sb.append(')');
                return sb.toString().equals("format(String, Object...)");
            }
        }

        return false;
    }
}
