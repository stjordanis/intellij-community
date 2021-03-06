// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonReferenceExpression;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairProcessor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.themes.metadata.UIThemeMetadataService;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Completion in IntelliJ theme files.
 */
public class ThemeJsonCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (!isThemeJsonFile(parameters.getOriginalFile())) return;

    PsiElement position = parameters.getPosition();
    if (!(position instanceof LeafPsiElement)) return;

    PsiElement parent = position.getParent();
    if (parent instanceof JsonStringLiteral || parent instanceof JsonReferenceExpression) {
      PsiElement parentParent = parent.getParent();
      if (!(parentParent instanceof JsonProperty)) return;
      handleJsonProperty(position, (JsonProperty)parentParent, parameters, result);
    }
    else if (parent instanceof JsonProperty) {
      handleJsonProperty(position, (JsonProperty)parent, parameters, result);
    }
  }

  private static boolean isThemeJsonFile(@Nullable PsiFile file) {
    return file instanceof JsonFile && ThemeJsonUtil.isThemeFilename(file.getName());
  }

  private static void handleJsonProperty(@NotNull PsiElement element,
                                         @NotNull JsonProperty property,
                                         @NotNull CompletionParameters parameters,
                                         @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) return;
    if (!isInsideUiProperty(property)) return;
    if (!isPropertyKey(element)) return;

    String presentNamePart = ThemeJsonUtil.getParentNames(property);

    boolean shouldSurroundWithQuotes = !element.getText().startsWith("\"");
    Iterable<LookupElement> lookupElements = getLookupElements(presentNamePart, shouldSurroundWithQuotes);
    result.addAllElements(lookupElements);
  }

  private static boolean isInsideUiProperty(@NotNull JsonProperty property) {
    PsiElement parent = property;
    while ((parent = parent.getParent()) != null) {
      if (!(parent instanceof JsonProperty)) continue;
      if ("ui".equals(((JsonProperty)parent).getName())) return true;
    }
    return false;
  }

  private static boolean isPropertyKey(@NotNull PsiElement element) {
    PsiElement sibling = element.getParent(); // element is leaf, parent is reference expression
    while ((sibling = sibling.getPrevSibling()) != null) {
      if (":".equals(sibling.getText())) return false; // no completion for values
    }
    return true;
  }

  private static Iterable<LookupElement> getLookupElements(@NotNull String presentNamePart,
                                                           boolean shouldSurroundWithQuotes) {
    Predicate<String> conditionFilter;
    Function<String, String> mapFunction;
    if (presentNamePart.startsWith("*")) {
      //TODO darcula etc. should be proposed after *?
      String tail = presentNamePart.substring(1);
      conditionFilter = key -> key.endsWith(tail);
      mapFunction = key -> key.substring(presentNamePart.length() - 1); // + 1 for dot, - 2 for '*.'
    }
    else if (presentNamePart.isEmpty()) {
      conditionFilter = key -> true;
      mapFunction = key -> key;
    }
    else {
      conditionFilter = key -> StringUtil.startsWithConcatenation(key, presentNamePart, ".");
      mapFunction = key -> key.substring(presentNamePart.length() + 1); // + 1 for dot
    }

    List<LookupElement> variants = new SmartList<>();
    PairProcessor<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> processor = (themeMetadata, uiKeyMetadata) -> {
      String key = uiKeyMetadata.getKey();
      if (!conditionFilter.test(key)) return true;

      final String completionKey = mapFunction.apply(key);

      String description = uiKeyMetadata.getDescription();
      final String source = uiKeyMetadata.getSource();
      final String tailText = (StringUtil.isEmpty(description) ? "" : " (" + description + ")") +
                              (StringUtil.isEmpty(source) ? "" : " in " + source);
      final LookupElementBuilder builder =
        LookupElementBuilder.create(completionKey)
          .withPresentableText(key)
          .withStrikeoutness(uiKeyMetadata.isDeprecated())
          .withTailText(tailText, true)
          .withTypeText("[" + ObjectUtils.chooseNotNull(themeMetadata.getName(), themeMetadata.getPluginId()) + "]")
          .withInsertHandler(shouldSurroundWithQuotes ? MyInsertHandler.SURROUND_WITH_QUOTES : MyInsertHandler.INSTANCE);

      variants.add(builder);
      return true;
    };
    UIThemeMetadataService.getInstance().processAllKeys(processor);
    return variants;
  }

  //TODO insert ': ' if necessary
  private static class MyInsertHandler implements InsertHandler<LookupElement> {

    private static final MyInsertHandler INSTANCE = new MyInsertHandler(false);
    private static final MyInsertHandler SURROUND_WITH_QUOTES = new MyInsertHandler(true);

    private final boolean mySurroundWithQuotes;

    private MyInsertHandler(boolean surroundWithQuotes) {
      mySurroundWithQuotes = surroundWithQuotes;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      if (mySurroundWithQuotes) {
        String quoted = "\"" + item.getLookupString() + "\"";
        context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), quoted);
      }
    }
  }
}
